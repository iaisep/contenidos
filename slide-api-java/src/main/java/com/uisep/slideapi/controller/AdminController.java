package com.uisep.slideapi.controller;

import com.uisep.slideapi.dto.SlideDTO.*;
import com.uisep.slideapi.entity.processed.SlideSyncLog;
import com.uisep.slideapi.repository.processed.SlideSyncLogRepository;
import com.uisep.slideapi.service.SlideSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final SlideSyncLogRepository syncLogRepo;

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

    // ─── Sync Log ──────────────────────────────────────────────────────────────

    @GetMapping("/sync/log")
    @Operation(
        summary = "Log de sincronizaciones",
        description = """
            Historial de slides creados o actualizados durante sincronizaciones.

            Solo se registran acciones `CREATED`, `UPDATED` y `FAILED` — los slides
            sin cambios (`SKIPPED`) no generan entrada en el log.

            **Filtros disponibles:**
            - `action` — `CREATED`, `UPDATED` o `FAILED`
            - `slideId` — historial de un slide concreto
            - `syncRunId` — todos los eventos de una ejecución específica
            - `from` / `to` — rango de fechas (ISO-8601: `2026-04-01T00:00:00`)
            """)
    @ApiResponse(responseCode = "200", description = "Página de entradas de log")
    public ResponseEntity<Page<SyncLogEntry>> getSyncLog(
            @Parameter(description = "Filtrar por acción", example = "UPDATED")
            @RequestParam(required = false) String action,
            @Parameter(description = "Filtrar por ID de slide", example = "1234")
            @RequestParam(required = false) Integer slideId,
            @Parameter(description = "Filtrar por ID de ejecución de sync")
            @RequestParam(required = false) String syncRunId,
            @Parameter(description = "Desde (ISO-8601)", example = "2026-04-01T00:00:00")
            @RequestParam(required = false) String from,
            @Parameter(description = "Hasta (ISO-8601)", example = "2026-04-30T23:59:59")
            @RequestParam(required = false) String to,
            @Parameter(description = "Página (empieza en 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Registros por página (máx 200)", example = "50")
            @RequestParam(defaultValue = "50") int size) {

        size = Math.min(size, 200);
        var pageable = PageRequest.of(page, size);

        Page<SlideSyncLog> raw;

        if (slideId != null) {
            raw = syncLogRepo.findBySlideIdOrderBySyncedAtDesc(slideId, pageable);
        } else if (syncRunId != null) {
            raw = syncLogRepo.findBySyncRunIdOrderBySyncedAtDesc(syncRunId, pageable);
        } else if (from != null || to != null) {
            LocalDateTime dtFrom = from != null ? LocalDateTime.parse(from) : LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime dtTo   = to   != null ? LocalDateTime.parse(to)   : LocalDateTime.now().plusYears(1);
            raw = syncLogRepo.findBySyncedAtBetweenOrderBySyncedAtDesc(dtFrom, dtTo, pageable);
        } else if (action != null) {
            raw = syncLogRepo.findByActionOrderBySyncedAtDesc(
                SlideSyncLog.SyncAction.valueOf(action.toUpperCase()), pageable);
        } else {
            raw = syncLogRepo.findByOrderBySyncedAtDesc(pageable);
        }

        Page<SyncLogEntry> result = raw.map(e -> SyncLogEntry.builder()
            .id(e.getId())
            .syncRunId(e.getSyncRunId())
            .syncedAt(e.getSyncedAt())
            .slideId(e.getSlideId())
            .slideName(e.getSlideName())
            .action(e.getAction().name())
            .slideType(e.getSlideType())
            .channelId(e.getChannelId())
            .channelName(e.getChannelName())
            .odooWriteDate(e.getOdooWriteDate())
            .originalSizeBytes(e.getOriginalSizeBytes())
            .processedSizeBytes(e.getProcessedSizeBytes())
            .imagesExtracted(e.getImagesExtracted())
            .message(e.getMessage())
            .build());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/sync/log/runs")
    @Operation(
        summary = "Resumen de ejecuciones de sync",
        description = """
            Lista de ejecuciones de sincronización, ordenadas por la más reciente.

            Cada entrada representa un `syncRunId` único con métricas agregadas:
            slides creados, actualizados y fallidos.

            Para ver los slides de una ejecución específica usar `GET /admin/sync/log?syncRunId={id}`.
            """)
    @ApiResponse(responseCode = "200", description = "Lista de ejecuciones de sync")
    public ResponseEntity<List<SyncRunSummary>> getSyncRuns(
            @Parameter(description = "Número de ejecuciones a devolver (máx 100)", example = "20")
            @RequestParam(defaultValue = "20") int limit) {

        limit = Math.min(limit, 100);
        List<Object[]> rows = syncLogRepo.findSyncRunSummaries(limit);

        List<SyncRunSummary> summaries = rows.stream().map(r -> SyncRunSummary.builder()
            .syncRunId((String) r[0])
            .startedAt(r[1] instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) r[1])
            .lastEventAt(r[2] instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) r[2])
            .totalEvents(((Number) r[3]).longValue())
            .created(((Number) r[4]).longValue())
            .updated(((Number) r[5]).longValue())
            .failed(((Number) r[6]).longValue())
            .build())
            .toList();

        return ResponseEntity.ok(summaries);
    }
}
