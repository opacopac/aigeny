package com.tschanz.aigeny.export;

import com.tschanz.aigeny.tools.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Produces CSV byte arrays from a QueryResult for HTTP download.
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    /** Returns UTF-8 CSV bytes (with BOM for broad client compatibility). */
    public byte[] toCsv(QueryResult result) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.print('\uFEFF'); // UTF-8 BOM for broad client compatibility
        pw.println(String.join(";", result.getColumns()));
        for (Map<String, Object> row : result.getRows()) {
            StringBuilder line = new StringBuilder();
            List<String> cols = result.getColumns();
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) line.append(";");
                Object val = row.get(cols.get(i));
                String s = val == null ? "" : val.toString();
                if (s.contains(";") || s.contains("\n") || s.contains("\"")) {
                    s = "\"" + s.replace("\"", "\"\"") + "\"";
                }
                line.append(s);
            }
            pw.println(line);
        }
        pw.flush();
        byte[] bytes = sw.toString().getBytes(StandardCharsets.UTF_8);
        log.info("CSV export: {} rows, {} bytes", result.getRows().size(), bytes.length);
        return bytes;
    }
}
