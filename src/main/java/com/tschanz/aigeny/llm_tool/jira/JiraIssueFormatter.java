package com.tschanz.aigeny.llm_tool.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm_tool.QueryResult;
import com.tschanz.aigeny.llm_tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts raw Jira API JSON responses into human-readable Markdown {@link ToolResult}s.
 *
 * <p>Extracted from {@link QueryJiraTool} to satisfy the Single-Responsibility Principle (S-2).
 * All formatting/parsing logic lives here; the tool itself stays focused on HTTP orchestration.
 */
@Component
public class JiraIssueFormatter {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueFormatter.class);

    private static final String MSG_NO_ISSUES    = "jira.no_issues_found";
    private static final String MSG_ISSUES_FOUND = "jira.issues_found";

    private final ObjectMapper objectMapper;

    public JiraIssueFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Formats a single Jira issue (response from {@code /rest/api/2/issue/{key}}) into Markdown.
     *
     * @param json Raw JSON body returned by the Jira REST API
     * @return {@link ToolResult} with a Markdown-formatted issue detail page
     */
    public ToolResult formatSingleIssue(String json) throws Exception {
        JsonNode f = objectMapper.readTree(json);
        String key = f.path("key").asText();
        JsonNode fields = f.path("fields");

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(key).append("**: ").append(fields.path("summary").asText()).append("\n\n");
        sb.append("| Field | Value |\n|---|---|\n");
        sb.append("| Status | ").append(fields.path("status").path("name").asText("-")).append(" |\n");
        sb.append("| Type | ").append(fields.path("issuetype").path("name").asText("-")).append(" |\n");
        sb.append("| Priority | ").append(fields.path("priority").path("name").asText("-")).append(" |\n");
        sb.append("| Assignee | ").append(fields.path("assignee").path("displayName").asText("Unassigned")).append(" |\n");
        sb.append("| Created | ").append(fields.path("created").asText("-")).append(" |\n");
        sb.append("| Updated | ").append(fields.path("updated").asText("-")).append(" |\n");

        String description = fields.path("description").asText("").trim();
        if (!description.isBlank()) {
            sb.append("\n**Description:**\n").append(description).append("\n");
        }

        appendComments(sb, fields.path("comment").path("comments"));
        appendLinkedIssues(sb, fields.path("issuelinks"));
        appendSubtasks(sb, fields.path("subtasks"));
        appendAttachments(sb, fields.path("attachment"));

        return new ToolResult(sb.toString());
    }

    /**
     * Formats a Jira JQL search response (from {@code /rest/api/2/search}) into a Markdown list
     * with an attached {@link QueryResult} for tabular export.
     *
     * @param json Raw JSON body returned by the Jira REST API
     * @return {@link ToolResult} with a Markdown list and optional {@link QueryResult}
     */
    public ToolResult formatSearchResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        int total = root.path("total").asInt(0);
        JsonNode issues = root.path("issues");

        if (!issues.isArray() || issues.isEmpty()) {
            return new ToolResult(Messages.get(MSG_NO_ISSUES));
        }

        log.info("   Jira total={} returned={}", total, issues.size());

        List<String> columns = List.of("KEY", "SUMMARY", "STATUS", "ASSIGNEE", "PRIORITY", "TYPE");
        List<Map<String, Object>> rows = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        text.append(Messages.get(MSG_ISSUES_FOUND, total, issues.size()));

        for (JsonNode issue : issues) {
            String key = issue.path("key").asText();
            JsonNode f = issue.path("fields");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("KEY",      key);
            row.put("SUMMARY",  f.path("summary").asText(""));
            row.put("STATUS",   f.path("status").path("name").asText(""));
            row.put("ASSIGNEE", f.path("assignee").path("displayName").asText("Unassigned"));
            row.put("PRIORITY", f.path("priority").path("name").asText(""));
            row.put("TYPE",     f.path("issuetype").path("name").asText(""));
            rows.add(row);
            text.append("- ").append(key).append(": ").append(row.get("SUMMARY"))
                .append(" [").append(row.get("STATUS")).append("] (").append(row.get("ASSIGNEE")).append(")\n");
        }

        return new ToolResult(text.toString(), new QueryResult("Jira", columns, rows));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void appendComments(StringBuilder sb, JsonNode comments) {
        if (!comments.isArray() || comments.isEmpty()) return;

        sb.append("\n**Last ").append(Math.min(comments.size(), 3)).append(" comment(s):**\n");
        int start = Math.max(0, comments.size() - 3);
        for (int i = start; i < comments.size(); i++) {
            JsonNode c = comments.get(i);
            sb.append("- *").append(c.path("author").path("displayName").asText("?")).append("*: ")
              .append(c.path("body").asText("")).append("\n");
        }
    }

    private void appendLinkedIssues(StringBuilder sb, JsonNode issueLinks) {
        if (!issueLinks.isArray() || issueLinks.isEmpty()) return;

        sb.append("\n**Linked Issues (").append(issueLinks.size()).append("):**\n");
        for (JsonNode link : issueLinks) {
            String typeName = link.path("type").path("name").asText("?");
            JsonNode outward = link.path("outwardIssue");
            JsonNode inward  = link.path("inwardIssue");
            if (!outward.isMissingNode()) {
                String direction = link.path("type").path("outward").asText(typeName);
                sb.append("- _").append(direction).append("_ **").append(outward.path("key").asText()).append("**: ")
                  .append(outward.path("fields").path("summary").asText(""))
                  .append(" [").append(outward.path("fields").path("status").path("name").asText("-")).append("]\n");
            }
            if (!inward.isMissingNode()) {
                String direction = link.path("type").path("inward").asText(typeName);
                sb.append("- _").append(direction).append("_ **").append(inward.path("key").asText()).append("**: ")
                  .append(inward.path("fields").path("summary").asText(""))
                  .append(" [").append(inward.path("fields").path("status").path("name").asText("-")).append("]\n");
            }
        }
    }

    private void appendSubtasks(StringBuilder sb, JsonNode subtasks) {
        if (!subtasks.isArray() || subtasks.isEmpty()) return;

        sb.append("\n**Sub-Tasks (").append(subtasks.size()).append("):**\n");
        for (JsonNode st : subtasks) {
            sb.append("- **").append(st.path("key").asText()).append("**: ")
              .append(st.path("fields").path("summary").asText(""))
              .append(" [").append(st.path("fields").path("status").path("name").asText("-")).append("]\n");
        }
    }

    private void appendAttachments(StringBuilder sb, JsonNode attachments) {
        if (!attachments.isArray() || attachments.isEmpty()) return;

        sb.append("\n**Attachments (").append(attachments.size()).append("):**\n");
        for (JsonNode att : attachments) {
            String filename   = att.path("filename").asText("-");
            String contentUrl = att.path("content").asText("");
            String mimeType   = att.path("mimeType").asText("");
            long   size       = att.path("size").asLong(0);
            sb.append("- `").append(filename).append("`");
            if (size > 0) sb.append(" (").append(size / 1024 + 1).append(" KB)");
            if (!mimeType.isBlank()) sb.append(" [").append(mimeType).append("]");
            if (!contentUrl.isBlank()) sb.append(" → URL: ").append(contentUrl);
            sb.append("\n");
        }
        sb.append("\n_To read a TXT or Excel attachment, call `read_jira_attachment` with the URL above._\n");
    }
}

