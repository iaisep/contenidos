package com.uisep.slideapi.controller;

import com.uisep.slideapi.dto.SlideDTO.*;
import com.uisep.slideapi.service.SlideSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller para operaciones de administración: sincronización y migración.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Endpoints de administración para sincronización y migración")
public class AdminController {
    
    private final SlideSyncService syncService;
    
    @PostMapping("/sync")
    @Operation(summary = "Sincroniza todos los slides", 
               description = "Sincroniza desde la réplica todos los slides activos. Puede tardar varios minutos.")
    public ResponseEntity<SyncResult> syncAllSlides(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        
        SyncResult result = syncService.syncAllSlides(activeOnly);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/sync/slide/{slideId}")
    @Operation(summary = "Sincroniza un slide específico", 
               description = "Sincroniza un slide individual por su ID")
    public ResponseEntity<MigrationResult> syncSlide(@PathVariable Integer slideId) {
        MigrationResult result = syncService.syncSlideById(slideId);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/migrate-base64")
    @Operation(summary = "Migra imágenes Base64", 
               description = "Procesa slides con imágenes Base64 embebidas y las extrae")
    public ResponseEntity<List<MigrationResult>> migrateBase64(
            @RequestParam(defaultValue = "10") int limit) {
        
        List<MigrationResult> results = syncService.migrateBase64Slides(limit);
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/stats")
    @Operation(summary = "Estadísticas de depuración", 
               description = "Obtiene métricas del estado actual de los slides y ahorro de espacio")
    public ResponseEntity<DepurationStats> getDepurationStats() {
        DepurationStats stats = syncService.getDepurationStats();
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Verifica el estado del servicio")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
