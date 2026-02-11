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
 * Controller para consultar slides procesados.
 * Este es el endpoint principal para la app.
 */
@RestController
@RequestMapping("/api/v1/slides")
@RequiredArgsConstructor
@Tag(name = "Slides", description = "Endpoints para consultar slides procesados")
public class SlideController {
    
    private final SlideQueryService queryService;
    
    @GetMapping
    @Operation(summary = "Lista slides publicados", 
               description = "Obtiene todos los slides activos y publicados para la app")
    public ResponseEntity<List<SlideListItem>> getPublishedSlides() {
        return ResponseEntity.ok(queryService.getPublishedSlides());
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Obtiene un slide por ID", 
               description = "Retorna el slide completo con su contenido HTML procesado e imágenes")
    public ResponseEntity<SlideResponse> getSlideById(@PathVariable Integer id) {
        return queryService.getSlideById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/channel/{channelId}")
    @Operation(summary = "Lista slides de un canal", 
               description = "Obtiene todos los slides de un curso específico")
    public ResponseEntity<List<SlideListItem>> getSlidesByChannel(@PathVariable Integer channelId) {
        return ResponseEntity.ok(queryService.getSlidesByChannel(channelId));
    }
    
    @GetMapping("/search")
    @Operation(summary = "Busca slides por nombre", 
               description = "Búsqueda por coincidencia parcial en el nombre del slide")
    public ResponseEntity<List<SlideListItem>> searchSlides(@RequestParam String q) {
        return ResponseEntity.ok(queryService.searchSlides(q));
    }
    
    @GetMapping("/{slideId}/images")
    @Operation(summary = "Lista imágenes de un slide", 
               description = "Obtiene información de todas las imágenes extraídas de un slide")
    public ResponseEntity<List<ImageInfo>> getSlideImages(@PathVariable Integer slideId) {
        return ResponseEntity.ok(queryService.getSlideImages(slideId));
    }
}
