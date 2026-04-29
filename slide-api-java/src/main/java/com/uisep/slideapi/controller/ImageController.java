package com.uisep.slideapi.controller;

import com.uisep.slideapi.entity.processed.SlideImage;
import com.uisep.slideapi.service.SlideQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Servicio de imágenes extraídas de los slides")
public class ImageController {

    private final SlideQueryService queryService;

    @GetMapping("/{slideId}/{filename}")
    @Operation(
        summary = "Obtener imagen por slide y filename",
        description = """
            Sirve el binario de una imagen extraída del HTML de un slide.
            
            **Uso en el HTML procesado:** el campo `htmlContent` de cada slide contiene \
            referencias del tipo:
            ```html
            <img src="/api/v1/images/1234/img_001.jpg">
            ```
            
            La respuesta incluye:
            - `Content-Type` correcto (image/jpeg, image/png, image/gif, etc.)
            - `Cache-Control: public, max-age=31536000` — las imágenes son inmutables; \
            cachear 1 año en cliente.
            - `Content-Disposition: inline` — para renderizado directo en WebView.
            
            **Estrategia de prefetch offline:** obtener la lista de imágenes con \
            `GET /slides/{slideId}/images` y descargarlas antes de renderizar el slide.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Imagen binaria",
            content = @Content(mediaType = "image/*")),
        @ApiResponse(responseCode = "404", description = "Imagen no encontrada",
            content = @Content)
    })
    public ResponseEntity<byte[]> getImage(
            @Parameter(description = "ID del slide al que pertenece la imagen", example = "1234")
            @PathVariable Integer slideId,
            @Parameter(description = "Nombre de archivo de la imagen (extraído del HTML original)",
                       example = "img_001.jpg")
            @PathVariable String filename) {

        return queryService.getImage(slideId, filename)
            .map(image -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + image.getOriginalFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .contentType(MediaType.parseMediaType(image.getMimeType()))
                .contentLength(image.getSizeBytes())
                .body(image.getImageData()))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-id/{imageId}")
    @Operation(
        summary = "Obtener imagen por ID único",
        description = """
            Sirve el binario de una imagen por su ID interno (tabla `slide_images`).
            
            Útil cuando se tiene el ID de la imagen directamente \
            (por ejemplo, desde `GET /slides/{slideId}/images`).
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Imagen binaria",
            content = @Content(mediaType = "image/*")),
        @ApiResponse(responseCode = "404", description = "Imagen no encontrada",
            content = @Content)
    })
    public ResponseEntity<byte[]> getImageById(
            @Parameter(description = "ID interno de la imagen", example = "891")
            @PathVariable Long imageId) {

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
