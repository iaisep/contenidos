package com.uisep.slideapi.controller;

import com.uisep.slideapi.dto.SlideDTO.*;
import com.uisep.slideapi.service.SlideSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administración: sincronización, estadísticas y mantenimiento")
public class AdminController {

    private final SlideSyncService syncService;

    @GetMapping("/health")
    @Operation(
        summary = "Health check",
        description = "Verifica que el servicio está en pie y los datasources conectados. " +
                      "Usado por el healthcheck de Docker.")
    @ApiResponse(responseCode = "200", description = "Servicio operativo",
        content = @Content(mediaType = "text/plain", schema = @Schema(example = "OK")))
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/sync")
    @Operation(
        summary = "Sincronizar slides desde la réplica Odoo",
        description = """
            Lee la réplica PostgreSQL de Odoo y sincroniza los slides a la BD procesada \
            (Supabase). Incluye:
            
            1. Detecta slides nuevos, actualizados y eliminados
            2. Procesa el HTML: extrae imágenes Base64 y las almacena por separado
            3. Actualiza estadísticas de los canales
            
            **Duración:** varios minutos para sincronizaciones grandes \
            (15 000+ slides pueden tardar 10–15 minutos).
            
            **Filtro de slides:** solo se sincronizan slides con:
            - `slide.active = true AND slide.is_published = true`
            - Canal con `active = true AND is_published = true`
            
            Consultar el progreso en tiempo real con `GET /admin/sync/progress`.
            """)
    @ApiResponse(responseCode = "200", description = "Resultado de la sincronización",
        content = @Content(schema = @Schema(implementation = SyncResult.class)))
    public ResponseEntity<SyncResult> syncAllSlides(
            @Parameter(description = "Si es true, solo sincroniza slides activos. " +
                                     "Siempre debe ser true en producción.",
                       example = "true")
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(syncService.syncAllSlides(activeOnly));
    }

    @PostMapping("/sync/slide/{slideId}")
    @Operation(
        summary = "Sincronizar un slide específico",
        description = """
            Fuerza la sincronización de un slide individual por su ID. \
            Útil para re-procesar slides que fallaron o para verificar un slide puntual \
            después de modificarlo en Odoo.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultado del procesamiento",
            content = @Content(schema = @Schema(implementation = MigrationResult.class))),
        @ApiResponse(responseCode = "404", description = "Slide no encontrado en la réplica",
            content = @Content)
    })
    public ResponseEntity<MigrationResult> syncSlide(
            @Parameter(description = "ID del slide en Odoo", example = "1234")
            @PathVariable Integer slideId) {
        return ResponseEntity.ok(syncService.syncSlideById(slideId));
    }

    @GetMapping("/sync/progress")
    @Operation(
        summary = "Ver progreso de la sincronización",
        description = """
            Devuelve el estado actual de la cola de sincronización.
            
            - `totalSlides` — slides objetivo (filtrado por canal publicado)
            - `completed` — ya procesados y disponibles en la app
            - `pending` — en cola, aún no procesados
            - `processing` — procesándose ahora mismo
            - `failed` — fallaron el procesamiento (ver logs para detalles)
            - `completionPercentage` — (completed / total) × 100
            """)
    @ApiResponse(responseCode = "200", description = "Estadísticas de progreso",
        content = @Content(schema = @Schema(implementation = SyncProgressStats.class)))
    public ResponseEntity<?> getSyncProgress() {
        return ResponseEntity.ok(syncService.getSyncProgress());
    }

    @PostMapping("/sync/reset-stuck")
    @Operation(
        summary = "Resetear slides atascados en PROCESSING",
        description = """
            Cambia de `PROCESSING` a `PENDING` los slides que quedaron atascados \
            (por ejemplo, si el servicio se reinició durante una sincronización).
            
            Ejecutar si `GET /admin/sync/progress` muestra slides en `processing` \
            sin que haya una sincronización activa.
            """)
    @ApiResponse(responseCode = "200", description = "Número de slides reseteados",
        content = @Content(schema = @Schema(example = "{\"message\": \"Slides reseteados\", \"count\": 3}")))
    public ResponseEntity<?> resetStuckSlides() {
        int reset = syncService.resetStuckSlides();
        return ResponseEntity.ok(Map.of("message", "Slides reseteados", "count", reset));
    }

    @PostMapping("/migrate-base64")
    @Operation(
        summary = "Migrar imágenes Base64 embebidas",
        description = """
            Procesa slides que aún tienen imágenes Base64 en su HTML: extrae las imágenes, \
            las almacena en la tabla `slide_images` y actualiza el HTML con referencias \
            a `/api/v1/images/{slideId}/{filename}`.
            
            Usar el parámetro `limit` para controlar cuántos slides procesar por llamada \
            y evitar timeouts en slides con muchas imágenes grandes.
            """)
    @ApiResponse(responseCode = "200", description = "Resultados de migración por slide",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = MigrationResult.class))))
    public ResponseEntity<List<MigrationResult>> migrateBase64(
            @Parameter(description = "Número máximo de slides a procesar en esta llamada",
                       example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(syncService.migrateBase64Slides(limit));
    }

    @GetMapping("/stats")
    @Operation(
        summary = "Estadísticas del servicio",
        description = """
            Devuelve métricas comparativas entre la réplica Odoo y la BD procesada:
            
            - Conteos de slides activos/inactivos en la réplica
            - Tamaño original vs procesado (ahorro de espacio por extracción de Base64)
            - Estadísticas de imágenes extraídas
            - Acciones de mantenimiento pendientes
            
            **Nota:** `slidesWithBase64` siempre devuelve `-1` porque la detección \
            de Base64 requiere escanear el campo `html_content` de toda la réplica \
            (operación muy costosa). Usar `POST /migrate-base64?limit=1` para detectar \
            si quedan slides sin migrar.
            """)
    @ApiResponse(responseCode = "200", description = "Estadísticas del servicio",
        content = @Content(schema = @Schema(implementation = DepurationStats.class)))
    public ResponseEntity<DepurationStats> getDepurationStats() {
        return ResponseEntity.ok(syncService.getDepurationStats());
    }


    @PostMapping("/sync/reset-outdated")
    @Operation(
        summary = "Detectar y resetear slides modificados en Odoo",
        description = """
            Compara el write_date de la replica Odoo con el odoo_write_date guardado \
            en la BD procesada para cada slide COMPLETED. Los slides donde la replica \
            es mas reciente se resetean a PENDING para ser re-sincronizados en el \
            proximo ciclo de POST /admin/sync.

            Tambien se llama automaticamente al inicio de cada sincronizacion completa.
            """)
    @ApiResponse(responseCode = "200", description = "Slides reseteados a PENDING",
        content = @Content(schema = @Schema(
            example = "{\"message\": \"Slides desactualizados reseteados a PENDING\", \"count\": 12}")))
    public ResponseEntity<?> resetOutdatedSlides() {
        int count = syncService.resetOutdatedSlides();
        return ResponseEntity.ok(Map.of(
            "message", "Slides desactualizados reseteados a PENDING",
            "count", count));
    }

    @PostMapping("/purge-excluded")
    @Operation(
        summary = "Eliminar slides que ya no cumplen el filtro",
        description = """
            Elimina de la BD procesada los slides que ya no pertenecen al conjunto \
            relevante: slides publicados en canales publicados y activos.
            
            Casos en que un slide se vuelve excluido:
            - Se archivó (`active = false`) en Odoo
            - Se despublicó (`is_published = false`) en Odoo
            - Su canal fue archivado o despublicado
            
            La operación elimina de forma segura: primero las imágenes (`slide_images`), \
            luego el slide procesado (`slides`), luego el tracking (`processing_status`).
            
            Ejecutar periódicamente o después de cambios masivos en Odoo.
            """)
    @ApiResponse(responseCode = "200", description = "Número de slides eliminados",
        content = @Content(schema = @Schema(
            example = "{\"message\": \"Slides excluidos eliminados de la BD procesada\", \"deleted\": 0}")))
    public ResponseEntity<?> purgeExcludedSlides() {
        int deleted = syncService.purgeExcludedSlides();
        return ResponseEntity.ok(Map.of(
            "message", "Slides excluidos eliminados de la BD procesada",
            "deleted", deleted));
    }
}
