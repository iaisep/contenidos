package com.uisep.slideapi.controller;

import com.uisep.slideapi.dto.SlideDTO.*;
import com.uisep.slideapi.service.SlideQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
@Tag(name = "Channels", description = "Consulta de canales/cursos eLearning procesados")
public class ChannelController {

    private final SlideQueryService queryService;

    @GetMapping
    @Operation(
        summary = "Listar todos los canales publicados",
        description = """
            Devuelve todos los canales (cursos de eLearning) activos y publicados.
            Incluye estadísticas: número de slides procesados y tamaño total.
            
            Un canal aparece aquí solo si tiene `active = true` y `is_published = true`.
            """)
    @ApiResponse(responseCode = "200", description = "Lista de canales",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChannelResponse.class))))
    public ResponseEntity<List<ChannelResponse>> getPublishedChannels() {
        return ResponseEntity.ok(queryService.getPublishedChannels());
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Obtener un canal por ID",
        description = "Devuelve la información completa de un canal/curso específico.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Canal encontrado",
            content = @Content(schema = @Schema(implementation = ChannelResponse.class))),
        @ApiResponse(responseCode = "404", description = "Canal no encontrado",
            content = @Content)
    })
    public ResponseEntity<ChannelResponse> getChannelById(
            @Parameter(description = "ID del canal (mismo ID que en Odoo slide.channel)", example = "42")
            @PathVariable Integer id) {
        return queryService.getChannelById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/slides")
    @Operation(
        summary = "Listar slides de un canal",
        description = """
            Devuelve los slides de un canal específico. Equivalente a \
            `GET /api/v1/slides/channel/{channelId}`.
            
            Sin `htmlContent` — usar `GET /api/v1/slides/{slideId}` para el HTML completo.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Slides del canal",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SlideListItem.class))))
    })
    public ResponseEntity<List<SlideListItem>> getChannelSlides(
            @Parameter(description = "ID del canal", example = "42")
            @PathVariable Integer id) {
        return ResponseEntity.ok(queryService.getSlidesByChannel(id, org.springframework.data.domain.Pageable.unpaged()).getContent());
    }
}
