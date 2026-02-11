package com.uisep.slideapi.controller;

import com.uisep.slideapi.entity.processed.SlideImage;
import com.uisep.slideapi.service.SlideQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para servir imágenes extraídas de los slides.
 */
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Endpoints para servir imágenes de slides")
public class ImageController {
    
    private final SlideQueryService queryService;
    
    @GetMapping("/{slideId}/{filename}")
    @Operation(summary = "Obtiene una imagen por slide y filename", 
               description = "Sirve la imagen binaria para reemplazar las referencias Base64")
    public ResponseEntity<byte[]> getImage(
            @PathVariable Integer slideId, 
            @PathVariable String filename) {
        
        return queryService.getImage(slideId, filename)
            .map(image -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "inline; filename=\"" + image.getOriginalFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000") // Cache 1 año
                .contentType(MediaType.parseMediaType(image.getMimeType()))
                .contentLength(image.getSizeBytes())
                .body(image.getImageData()))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/by-id/{imageId}")
    @Operation(summary = "Obtiene una imagen por ID", 
               description = "Sirve la imagen binaria por su ID único")
    public ResponseEntity<byte[]> getImageById(@PathVariable Long imageId) {
        
        return queryService.getImageById(imageId)
            .map(image -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "inline; filename=\"" + image.getOriginalFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .contentType(MediaType.parseMediaType(image.getMimeType()))
                .contentLength(image.getSizeBytes())
                .body(image.getImageData()))
            .orElse(ResponseEntity.notFound().build());
    }
}
