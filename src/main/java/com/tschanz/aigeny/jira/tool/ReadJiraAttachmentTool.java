package com.tschanz.aigeny.jira.tool;
import com.tschanz.aigeny.jira.JiraTokenContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.AbstractTool;
import com.tschanz.aigeny.tool.ToolResult;
import com.tschanz.aigeny.Messages;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Tool for downloading and reading Jira attachments in TXT, CSV or Excel (.xls/.xlsx) format.
 * The attachment content URL is obtained from search_jira (issueKey fetch).
 */
@Service
public class ReadJiraAttachmentTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(ReadJiraAttachmentTool.class);

    /** Max characters returned for text/CSV attachments to avoid overwhelming the LLM context. */
    private static final int MAX_TEXT_CHARS = 12_000;
    /** Max rows rendered per sheet for Excel attachments. */
    private static final int MAX_EXCEL_ROWS = 200;

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_NOT_CONFIGURED  = "jira.error.not_configured";
    private static final String MSG_NO_TOKEN        = "jira.error.no_token";
    private static final String MSG_MISSING_URL     = "jira.attachment.missing_url";
    private static final String MSG_UNSUPPORTED     = "jira.attachment.unsupported_type";
    private static final String MSG_AUTH_FAILED     = "jira.error.auth_failed_en";
    private static final String MSG_HTTP_ERROR      = "jira.error.http_en";
    private static final String MSG_TOO_LARGE       = "jira.attachment.text_truncated";

    private final JiraConfiguration jiraConfig;
    private final HttpClient http;

    public ReadJiraAttachmentTool(JiraConfiguration jiraConfig, ObjectMapper objectMapper) {
        super(objectMapper);
        this.jiraConfig = jiraConfig;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override public String getName() { return "read_jira_attachment"; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String filename = args.path("filename").asText("").trim();
            if (!filename.isBlank()) return "Anhang lesen: " + filename;
            // Try to extract filename from URL
            String url = args.path("attachmentUrl").asText("").trim();
            if (!url.isBlank()) {
                String[] parts = url.split("/");
                String lastPart = parts[parts.length - 1].split("\\?")[0];
                if (lastPart.contains(".")) return "Anhang lesen: " + lastPart;
            }
        } catch (Exception ignored) {}
        return "Jira-Anhang lesen";
    }

    @Override
    public String getDescription() {
        return "Download and read the content of a Jira attachment. " +
               "Supported formats: plain text (.txt), CSV (.csv), Excel (.xls, .xlsx), Word (.docx). " +
               "Provide the 'attachmentUrl' from the attachment list returned by search_jira. " +
               "Optionally provide 'filename' if the URL does not reveal the file extension.";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = Map.of(
            "attachmentUrl", Map.of("type", "string",
                "description", "The direct content URL of the attachment (from search_jira result)"),
            "filename", Map.of("type", "string",
                "description", "Optional: original filename (e.g. 'report.xlsx') to help detect file type")
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object", "properties", propsMap, "required", java.util.List.of("attachmentUrl")));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
                String baseUrl = jiraConfig.getBaseUrl() == null ? "" : jiraConfig.getBaseUrl().replaceAll("/$", "");

        if (baseUrl.isBlank()) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        // Resolve effective token
        String effectiveToken = JiraTokenContext.get();
        if (effectiveToken == null || effectiveToken.isBlank()) {
            effectiveToken = jiraConfig.getToken();
        }
        if (effectiveToken == null || effectiveToken.isBlank()) {
            return new ToolResult(Messages.get(MSG_NO_TOKEN));
        }

        JsonNode args = objectMapper.readTree(argumentsJson);
        String attachmentUrl = args.path("attachmentUrl").asText("").trim();
        String filename = args.path("filename").asText("").trim();

        if (attachmentUrl.isBlank()) {
            return new ToolResult(Messages.get(MSG_MISSING_URL));
        }

        // Derive filename from URL if not supplied
        if (filename.isBlank()) {
            String path = URI.create(attachmentUrl).getPath();
            filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        }

        String lowerName = filename.toLowerCase();
        String authHeader = "Bearer " + effectiveToken;

        log.info(">> JIRA ATTACHMENT  url={} filename={}", attachmentUrl, filename);

        // Download the raw bytes
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(attachmentUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", authHeader)
                .header("Accept", "*/*")
                .GET().build();

        HttpResponse<byte[]> response = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 401) {
            return new ToolResult(Messages.get(MSG_AUTH_FAILED));
        }
        if (response.statusCode() != 200) {
            log.error("<< JIRA ATTACHMENT status={}", response.statusCode());
            return new ToolResult(Messages.get(MSG_HTTP_ERROR, response.statusCode(), "(binary body)"));
        }

        log.info("<< JIRA ATTACHMENT status=200 bytes={}", response.body().length);

        byte[] bytes = response.body();

        if (lowerName.endsWith(".xlsx")) {
            return parseExcel(bytes, filename, false);
        } else if (lowerName.endsWith(".xls")) {
            return parseExcel(bytes, filename, true);
        } else if (lowerName.endsWith(".docx")) {
            return parseDocx(bytes, filename);
        } else if (lowerName.endsWith(".txt") || lowerName.endsWith(".csv")
                || lowerName.endsWith(".log") || lowerName.endsWith(".xml")
                || lowerName.endsWith(".json") || lowerName.endsWith(".md")) {
            return parseText(bytes, filename);
        } else {
            // Try to detect via content: if it looks like text, return as-is
            String asText = tryDecodeText(bytes);
            if (asText != null) {
                return parseText(bytes, filename);
            }
            return new ToolResult(Messages.get(MSG_UNSUPPORTED, filename));
        }
    }

    // ── Text parsing ──────────────────────────────────────────────────────────

    private ToolResult parseText(byte[] bytes, String filename) {
        String content;
        try {
            content = new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
        boolean truncated = content.length() > MAX_TEXT_CHARS;
        if (truncated) {
            content = content.substring(0, MAX_TEXT_CHARS);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**Attachment: `").append(filename).append("`**\n\n");
        sb.append("```\n").append(content).append("\n```\n");
        if (truncated) {
            sb.append("\n_").append(Messages.get(MSG_TOO_LARGE, MAX_TEXT_CHARS)).append("_\n");
        }
        return new ToolResult(sb.toString());
    }

    /** Attempt UTF-8 decode; return null if it looks like binary. */
    private String tryDecodeText(byte[] bytes) {
        try {
            String s = new String(bytes, StandardCharsets.UTF_8);
            // Rough heuristic: if >5% null/control bytes → binary
            long badChars = s.chars().filter(c -> c == 0 || (c < 32 && c != '\n' && c != '\r' && c != '\t')).count();
            return (badChars * 20 < bytes.length) ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Word DOCX parsing ─────────────────────────────────────────────────────

    private ToolResult parseDocx(byte[] bytes, String filename) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("**Attachment: `").append(filename).append("`**\n\n");

        try (InputStream is = new ByteArrayInputStream(bytes);
             XWPFDocument doc = new XWPFDocument(is)) {

            // Process body elements in document order (paragraphs and tables interleaved)
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    String style = para.getStyle() == null ? "" : para.getStyle().toLowerCase();
                    String text = para.getText();
                    if (text.isBlank()) {
                        sb.append("\n");
                        continue;
                    }
                    // Map Word heading styles to Markdown headings
                    if (style.startsWith("heading1") || style.equals("1")) {
                        sb.append("# ").append(text).append("\n\n");
                    } else if (style.startsWith("heading2") || style.equals("2")) {
                        sb.append("## ").append(text).append("\n\n");
                    } else if (style.startsWith("heading3") || style.equals("3")) {
                        sb.append("### ").append(text).append("\n\n");
                    } else if (para.getNumID() != null) {
                        // List item
                        sb.append("- ").append(text).append("\n");
                    } else {
                        sb.append(text).append("\n\n");
                    }

                } else if (element instanceof XWPFTable table) {
                    // Render table as Markdown
                    java.util.List<XWPFTableRow> rows = table.getRows();
                    if (rows.isEmpty()) continue;

                    // Header row
                    XWPFTableRow headerRow = rows.get(0);
                    sb.append("|");
                    for (XWPFTableCell cell : headerRow.getTableCells()) {
                        sb.append(" ").append(cell.getText().replace("|", "\\|").trim()).append(" |");
                    }
                    sb.append("\n|");
                    for (int ci = 0; ci < headerRow.getTableCells().size(); ci++) sb.append("---|");
                    sb.append("\n");

                    // Data rows
                    for (int ri = 1; ri < rows.size(); ri++) {
                        sb.append("|");
                        for (XWPFTableCell cell : rows.get(ri).getTableCells()) {
                            sb.append(" ").append(cell.getText().replace("|", "\\|").trim()).append(" |");
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }
            }
        }

        boolean truncated = sb.length() > MAX_TEXT_CHARS + 200;
        String result = truncated ? sb.substring(0, MAX_TEXT_CHARS) + "\n\n_...(truncated)_" : sb.toString();
        return new ToolResult(result);
    }

    // ── Excel parsing ─────────────────────────────────────────────────────────

    private ToolResult parseExcel(byte[] bytes, String filename, boolean isXls) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("**Attachment: `").append(filename).append("`**\n\n");

        try (InputStream is = new ByteArrayInputStream(bytes);
             Workbook wb = isXls ? new HSSFWorkbook(is) : new XSSFWorkbook(is)) {

            DataFormatter fmt = new DataFormatter();

            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                sb.append("### Sheet: ").append(sheet.getSheetName()).append("\n\n");

                int firstRow = sheet.getFirstRowNum();
                int lastRow = Math.min(sheet.getLastRowNum(), firstRow + MAX_EXCEL_ROWS);
                int totalRows = sheet.getLastRowNum() - firstRow + 1;

                // Collect rows into a list first to determine column widths
                java.util.List<String[]> rows = new java.util.ArrayList<>();
                int maxCols = 0;
                for (int ri = firstRow; ri <= lastRow; ri++) {
                    Row row = sheet.getRow(ri);
                    if (row == null) {
                        rows.add(new String[0]);
                        continue;
                    }
                    int colCount = row.getLastCellNum();
                    if (colCount > maxCols) maxCols = colCount;
                    String[] cells = new String[colCount];
                    for (int ci = 0; ci < colCount; ci++) {
                        Cell cell = row.getCell(ci, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        cells[ci] = cell == null ? "" : fmt.formatCellValue(cell).replace("|", "\\|").trim();
                    }
                    rows.add(cells);
                }

                if (rows.isEmpty()) {
                    sb.append("_(empty sheet)_\n\n");
                    continue;
                }

                // Render as Markdown table
                String[] header = rows.get(0);
                sb.append("|");
                for (int ci = 0; ci < maxCols; ci++) {
                    sb.append(" ").append(ci < header.length ? header[ci] : "").append(" |");
                }
                sb.append("\n|");
                for (int ci = 0; ci < maxCols; ci++) sb.append("---|");
                sb.append("\n");

                for (int ri = 1; ri < rows.size(); ri++) {
                    String[] cells = rows.get(ri);
                    sb.append("|");
                    for (int ci = 0; ci < maxCols; ci++) {
                        sb.append(" ").append(ci < cells.length ? cells[ci] : "").append(" |");
                    }
                    sb.append("\n");
                }

                if (totalRows - 1 > MAX_EXCEL_ROWS) {
                    sb.append("\n_... (").append(totalRows - 1 - MAX_EXCEL_ROWS)
                      .append(" more rows not shown)_\n");
                }
                sb.append("\n");
            }
        }

        return new ToolResult(sb.toString());
    }
}
