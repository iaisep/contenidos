package com.uisep.slideapi.controller;

import com.uisep.slideapi.dto.SlideDTO.*;
import com.uisep.slideapi.service.SlideQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller para consultar canales/cursos procesados.
 */
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
@Tag(name = "Channels", description = "Endpoints para consultar canales/cursos")
public class ChannelController {
    
    private final SlideQueryService queryService;
    
    @GetMapping
    @Operation(summary = "Lista canales publicados", 
               description = "Obtiene todos los canales activos y publicados")
    public ResponseEntity<List<ChannelResponse>> getPublishedChannels() {
        return ResponseEntity.ok(queryService.getPublishedChannels());
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Obtiene un canal por ID", 
               description = "Retorna el canal con su información completa")
    public ResponseEntity<ChannelResponse> getChannelById(@PathVariable Integer id) {
        return queryService.getChannelById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/{id}/slides")
    @Operation(summary = "Lista slides de un canal", 
               description = "Obtiene todos los slides de un canal específico")
    public ResponseEntity<List<SlideListItem>> getChannelSlides(@PathVariable Integer id) {
        return ResponseEntity.ok(queryService.getSlidesByChannel(id));
    }
}
