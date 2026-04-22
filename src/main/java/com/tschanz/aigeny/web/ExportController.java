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
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aigeny_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(data);
    }
}
