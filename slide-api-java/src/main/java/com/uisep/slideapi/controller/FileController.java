package com.uisep.slideapi.controller;

import com.uisep.slideapi.service.OdooFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final OdooFileService odooFileService;

    /**
     * Streams a locally cached slide file (PDF/presentation).
     * If not cached yet, triggers download from Odoo on first request.
     */
    @GetMapping("/{slideId}")
    public ResponseEntity<Resource> getFile(@PathVariable int slideId) {
        Path filePath = odooFileService.localPath(slideId);

        if (!odooFileService.exists(slideId)) {
            // Attempt download on first request
            var downloaded = odooFileService.downloadAndSave(slideId);
            if (downloaded.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            filePath = downloaded.get();
        }

        try {
            Resource resource = new FileSystemResource(filePath);
            String contentType = detectContentType(filePath);
            long fileSize = Files.size(filePath);

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.inline()
                        .filename("slide_" + slideId + extensionFor(contentType))
                        .build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);

        } catch (IOException e) {
            log.error("Error serving file for slide {}: {}", slideId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private String detectContentType(Path filePath) {
        try {
            byte[] header = new byte[8];
            try (var in = Files.newInputStream(filePath)) {
                int read = in.read(header);
                if (read >= 4) {
                    // PDF magic: %PDF
                    if (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46)
                        return "application/pdf";
                    // PNG: \x89PNG
                    if (header[0] == (byte)0x89 && header[1] == 0x50)
                        return "image/png";
                    // JPEG: \xFF\xD8
                    if (header[0] == (byte)0xFF && header[1] == (byte)0xD8)
                        return "image/jpeg";
                }
            }
        } catch (IOException ignored) {}
        return "application/octet-stream";
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png"       -> ".png";
            case "image/jpeg"      -> ".jpg";
            default                -> ".bin";
        };
    }
}
