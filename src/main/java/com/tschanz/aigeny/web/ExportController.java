package com.tschanz.aigeny.web;

import com.tschanz.aigeny.export.ExportService;
import com.tschanz.aigeny.tools.QueryResult;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles CSV file download for the last query result in the session.
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final String CSV_CONTENT_DISPOSITION = "attachment; filename=\"aigeny_export.csv\"";
    private static final String CSV_CONTENT_TYPE        = "text/csv;charset=UTF-8";

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> downloadCsv(HttpSession session) {
        QueryResult result = ChatController.getLastResult(session);
        if (result == null || result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        byte[] data = exportService.toCsv(result);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, CSV_CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType(CSV_CONTENT_TYPE))
                .body(data);
    }
}
