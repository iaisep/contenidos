package com.uisep.slideapi.controller;

import com.uisep.slideapi.dto.SlideDTO.*;
import com.uisep.slideapi.service.SlideQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/slides")
@RequiredArgsConstructor
@Tag(name = "Slides", description = "Consulta de slides educativos procesados")
public class SlideController {

    private final SlideQueryService queryService;

    @GetMapping
    @Operation(
        summary = "Listar slides publicados (paginado)",
        description = """
            Devuelve los slides activos y publicados de la BD procesada.

            Usa `page` y `size` para paginar. Por defecto devuelve la primera página de 50 slides.

            **El campo `htmlContent` no se incluye** en el listado — usar `GET /slides/{id}` para el HTML completo.

            Un slide aparece aquí solo si cumple los 3 criterios:
            - `slide.active = true`
            - `slide.is_published = true`
            - Su canal tiene `active = true` y `is_published = true`
            """)
    @ApiResponse(responseCode = "200", description = "Página de slides",
        content = @Content(schema = @Schema(implementation = SlidePageResponse.class)))
    public ResponseEntity<Page<SlideListItem>> getPublishedSlides(
            @Parameter(description = "Número de página (empieza en 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Slides por página (máx 200)", example = "50")
            @RequestParam(defaultValue = "50") int size) {
        size = Math.min(size, 200);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("channelId", "id"));
        return ResponseEntity.ok(queryService.getPublishedSlides(pageable));
    }

    @GetMapping("/all")
    @Operation(
        summary = "Listar TODOS los slides publicados (sin paginación)",
        description = """
            Devuelve todos los slides en una sola respuesta. **Respuesta grande (~3MB).**

            Usar solo para sincronización offline completa. Para uso normal preferir `GET /slides` con paginación.
            """)
    @ApiResponse(responseCode = "200", description = "Lista completa de slides",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = SlideListItem.class))))
    public ResponseEntity<List<SlideListItem>> getAllSlides() {
        return ResponseEntity.ok(queryService.getPublishedSlides(org.springframework.data.domain.Pageable.unpaged()).getContent());
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Obtener un slide completo por ID",
        description = """
            Devuelve el slide con su `htmlContent` procesado e imágenes extraídas.

            El HTML no contiene Base64 — las imágenes se sirven desde `/api/v1/images/{slideId}/{filename}`.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Slide encontrado",
            content = @Content(schema = @Schema(implementation = SlideResponse.class))),
        @ApiResponse(responseCode = "404", description = "Slide no encontrado", content = @Content)
    })
    public ResponseEntity<SlideResponse> getSlideById(
            @Parameter(description = "ID del slide (mismo ID que en Odoo)", example = "1234")
            @PathVariable Integer id) {
        return queryService.getSlideById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/channel/{channelId}")
    @Operation(
        summary = "Listar slides de un canal",
        description = "Obtiene todos los slides de un curso específico. Sin `htmlContent`.")
    @ApiResponse(responseCode = "200", description = "Slides del canal",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = SlideListItem.class))))
    public ResponseEntity<List<SlideListItem>> getSlidesByChannel(
            @Parameter(description = "ID del canal/curso", example = "42")
            @PathVariable Integer channelId) {
        return ResponseEntity.ok(queryService.getSlidesByChannel(channelId, org.springframework.data.domain.Pageable.unpaged()).getContent());
    }

    @GetMapping("/search")
    @Operation(
        summary = "Buscar slides por nombre",
        description = "Búsqueda case-insensitive por coincidencia parcial en el título. Sin `htmlContent`.")
    @ApiResponse(responseCode = "200", description = "Slides que coinciden",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = SlideListItem.class))))
    public ResponseEntity<List<SlideListItem>> searchSlides(
            @Parameter(description = "Texto a buscar", example = "excel", required = true)
            @RequestParam String q) {
        return ResponseEntity.ok(queryService.searchSlides(q, org.springframework.data.domain.Pageable.unpaged()).getContent());
    }

    @GetMapping("/{slideId}/images")
    @Operation(
        summary = "Listar imágenes de un slide",
        description = "Metadatos de todas las imágenes extraídas del HTML. Útil para prefetch offline.")
    @ApiResponse(responseCode = "200", description = "Lista de imágenes",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ImageInfo.class))))
    public ResponseEntity<List<ImageInfo>> getSlideImages(
            @Parameter(description = "ID del slide", example = "1234")
            @PathVariable Integer slideId) {
        return ResponseEntity.ok(queryService.getSlideImages(slideId));
    }

    // Schema helper para documentar la respuesta paginada en Swagger
    @Schema(description = "Respuesta paginada de slides")
    static class SlidePageResponse {
        @Schema(description = "Slides de esta página") public List<SlideListItem> content;
        @Schema(description = "Número de página actual (empieza en 0)", example = "0") public int number;
        @Schema(description = "Slides por página", example = "50") public int size;
        @Schema(description = "Total de slides disponibles", example = "11304") public long totalElements;
        @Schema(description = "Total de páginas", example = "227") public int totalPages;
        @Schema(description = "Es la primera página", example = "true") public boolean first;
        @Schema(description = "Es la última página", example = "false") public boolean last;
    }
}
