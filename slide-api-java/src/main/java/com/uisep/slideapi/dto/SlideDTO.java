package com.uisep.slideapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class SlideDTO {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Slide completo con contenido HTML procesado e imágenes extraídas")
    public static class SlideResponse {
        @Schema(description = "ID del slide (mismo que en Odoo)", example = "1234")
        private Integer id;

        @Schema(description = "ID del canal/curso al que pertenece", example = "42")
        private Integer channelId;

        @Schema(description = "Nombre del canal/curso", example = "Habilidades Digitales")
        private String channelName;

        @Schema(description = "Título del slide", example = "Tema 1 – Introducción a Excel")
        private String name;

        @Schema(description = "Tipo de slide", example = "pdf",
                allowableValues = {"pdf", "presentation", "video", "webpage", "infographic"})
        private String slideType;

        @Schema(description = "HTML procesado del slide. Las imágenes Base64 originales han sido " +
                "reemplazadas por referencias a /api/v1/images/{slideId}/{filename}. " +
                "Solo presente en GET /slides/{id}, nunca en listados.",
                example = "<section>...</section>")
        private String htmlContent;


        @Schema(description = "URL del contenido multimedia. Para slides tipo 'video' (Bunny), " +
                "'youtube_video', 'local_external' o 'pdf': URL directa al recurso. " +
                "Para slides tipo 'article' el contenido está en htmlContent.",
                example = "https://iframe.mediadelivery.net/embed/12345/uuid")
        private String contentUrl;

        @Schema(description = "ID del video de YouTube (solo para slide_type=youtube_video). " +
                "Útil para usar el SDK nativo de YouTube en la app móvil.",
                example = "XTRCR0izctk")
        private String youtubeId;

        @Schema(description = "Descripción del slide", example = "Introducción al módulo de cálculo")
        private String description;

        @Schema(description = "Si el slide está activo en Odoo", example = "true")
        private Boolean active;

        @Schema(description = "Si el slide está publicado y visible para alumnos", example = "true")
        private Boolean isPublished;

        @Schema(description = "Número de visualizaciones acumuladas", example = "158")
        private Integer totalViews;

        @Schema(description = "Tamaño del HTML original en bytes (antes del procesamiento)", example = "2457600")
        private Long originalSizeBytes;

        @Schema(description = "Tamaño del HTML procesado en bytes (sin Base64)", example = "48320")
        private Long processedSizeBytes;

        @Schema(description = "Número de imágenes extraídas del HTML", example = "7")
        private Integer imagesExtracted;

        @Schema(description = "Fecha de creación en Odoo", example = "2024-03-15T10:30:00")
        private LocalDateTime odooCreateDate;

        @Schema(description = "Última vez que se sincronizó con la réplica", example = "2026-04-27T18:00:00")
        private LocalDateTime lastSyncedAt;

        @Schema(description = "Estado de procesamiento del slide",
                allowableValues = {"PENDING", "COMPLETED", "FAILED", "SKIPPED"},
                example = "COMPLETED")
        private String migrationStatus;

        @Schema(description = "Imágenes extraídas del HTML de este slide")
        private List<ImageInfo> images;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Metadatos de una imagen extraída del HTML de un slide")
    public static class ImageInfo {
        @Schema(description = "ID único de la imagen", example = "891")
        private Long id;

        @Schema(description = "Nombre de archivo de la imagen (para construir la URL)",
                example = "img_001.jpg")
        private String filename;

        @Schema(description = "MIME type de la imagen", example = "image/jpeg")
        private String mimeType;

        @Schema(description = "Tamaño de la imagen en bytes", example = "45312")
        private Long sizeBytes;

        @Schema(description = "URL pública de la imagen para usarla en el HTML",
                example = "/api/v1/images/1234/img_001.jpg")
        private String publicUrl;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Resumen de un slide para listados — sin htmlContent")
    public static class SlideListItem {
        @Schema(description = "ID del slide", example = "1234")
        private Integer id;

        @Schema(description = "ID del canal/curso", example = "42")
        private Integer channelId;

        @Schema(description = "Nombre del canal/curso", example = "Habilidades Digitales")
        private String channelName;

        @Schema(description = "Título del slide", example = "Tema 1 – Introducción a Excel")
        private String name;

        @Schema(description = "Tipo de slide",
                allowableValues = {"pdf", "presentation", "video", "webpage", "infographic"},
                example = "pdf")
        private String slideType;

        @Schema(description = "Si el slide está activo", example = "true")
        private Boolean active;

        @Schema(description = "Si el slide está publicado", example = "true")
        private Boolean isPublished;

        @Schema(description = "Número de visualizaciones", example = "158")
        private Integer totalViews;

        @Schema(description = "Tamaño original en bytes", example = "2457600")
        private Long originalSizeBytes;

        @Schema(description = "Tamaño procesado en bytes (sin Base64)", example = "48320")
        private Long processedSizeBytes;

        @Schema(description = "Estado de procesamiento",
                allowableValues = {"PENDING", "COMPLETED", "FAILED", "SKIPPED"},
                example = "COMPLETED")
        private String migrationStatus;

        @Schema(description = "URL del contenido multimedia (para tipos video/pdf/external)",
                example = "https://iframe.mediadelivery.net/embed/12345/uuid")
        private String contentUrl;

        @Schema(description = "ID de YouTube (solo para youtube_video)", example = "XTRCR0izctk")
        private String youtubeId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Canal/curso con sus estadísticas de slides")
    public static class ChannelResponse {
        @Schema(description = "ID del canal (mismo que en Odoo slide.channel)", example = "42")
        private Integer id;

        @Schema(description = "Nombre del canal/curso", example = "Habilidades Digitales")
        private String name;

        @Schema(description = "Descripción del canal", example = "Curso de competencias digitales")
        private String description;

        @Schema(description = "Si el canal está activo", example = "true")
        private Boolean active;

        @Schema(description = "Si el canal está publicado y visible para alumnos", example = "true")
        private Boolean isPublished;

        @Schema(description = "Visualizaciones totales del canal", example = "3420")
        private Integer totalViews;

        @Schema(description = "Número de slides procesados en este canal", example = "28")
        private Integer slideCount;

        @Schema(description = "Tamaño total de slides procesados en bytes", example = "1258291")
        private Long totalSizeBytes;

        @Schema(description = "Fecha de creación en Odoo", example = "2024-01-10T09:00:00")
        private LocalDateTime odooCreateDate;

        @Schema(description = "Última sincronización con la réplica", example = "2026-04-27T18:00:00")
        private LocalDateTime lastSyncedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Estadísticas del servicio: réplica Odoo vs BD procesada")
    public static class DepurationStats {
        @Schema(description = "Total de slides en la réplica Odoo (activos + inactivos)", example = "40860")
        private Long totalSlides;

        @Schema(description = "Slides activos en la réplica (active=true)", example = "37268")
        private Long activeSlides;

        @Schema(description = "Slides inactivos en la réplica (archivados)", example = "3592")
        private Long inactiveSlides;

        @Schema(description = "Slides con imágenes Base64 embebidas pendientes de migrar. " +
                "-1 = no calculado (operación costosa, usar endpoint dedicado)", example = "-1")
        private Long slidesWithBase64;

        @Schema(description = "Tamaño total del HTML original de slides COMPLETADOS (bytes)", example = "1187246208")
        private Long totalOriginalSizeBytes;

        @Schema(description = "Tamaño total del HTML procesado de slides COMPLETADOS (bytes)", example = "26623529")
        private Long totalProcessedSizeBytes;

        @Schema(description = "Bytes ahorrados (original - procesado)", example = "1160622679")
        private Long totalSavedBytes;

        @Schema(description = "Porcentaje de ahorro sobre el tamaño original", example = "97.76")
        private Double savingsPercentage;

        @Schema(description = "Total de imágenes extraídas del HTML y almacenadas por separado", example = "2298")
        private Long totalImagesExtracted;

        @Schema(description = "Acciones de mantenimiento pendientes")
        private PendingActions pendingActions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Tareas de mantenimiento pendientes en la BD procesada")
    public static class PendingActions {
        @Schema(description = "Slides inactivos en la réplica que no se han eliminado de la BD procesada",
                example = "3592")
        private Long inactiveSlidesToDelete;

        @Schema(description = "Tamaño total de htmlContent de slides inactivos en la réplica (bytes)",
                example = "271126499")
        private Long inactiveSlidesSize;

        @Schema(description = "Slides con imágenes Base64 sin migrar (-1 = no calculado)", example = "-1")
        private Long slidesToMigrateBase64;

        @Schema(description = "Tamaño de Base64 sin migrar en bytes (-1 = no calculado)", example = "-1")
        private Long base64Size;

        @Schema(description = "Campos temporales pendientes de limpiar", example = "0")
        private Long temporaryFieldsToClean;

        @Schema(description = "Tamaño de campos temporales (bytes)", example = "0")
        private Long temporaryFieldsSize;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Resultado de procesar un slide individual durante la sincronización")
    public static class MigrationResult {
        @Schema(description = "ID del slide procesado", example = "1234")
        private Integer slideId;

        @Schema(description = "Nombre del slide", example = "Tema 1 – Introducción a Excel")
        private String slideName;

        @Schema(description = "Resultado del procesamiento",
                allowableValues = {"SUCCESS", "FAILED", "SKIPPED"},
                example = "SUCCESS")
        private String status;

        @Schema(description = "Número de imágenes Base64 extraídas y almacenadas", example = "3")
        private Integer imagesExtracted;

        @Schema(description = "Tamaño del HTML original en bytes", example = "2457600")
        private Long originalSize;

        @Schema(description = "Tamaño del HTML procesado en bytes", example = "48320")
        private Long newSize;

        @Schema(description = "Bytes ahorrados (original - procesado)", example = "2409280")
        private Long savedBytes;

        @Schema(description = "Mensaje descriptivo del resultado", example = "OK - 3 imágenes extraídas")
        private String message;

        @Schema(description = "Momento en que se procesó el slide", example = "2026-04-27T18:05:32")
        private LocalDateTime processedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Resultado global de una operación de sincronización batch")
    public static class SyncResult {
        @Schema(description = "Momento en que inició la sincronización", example = "2026-04-27T18:00:00")
        private LocalDateTime startedAt;

        @Schema(description = "Momento en que terminó la sincronización", example = "2026-04-27T18:12:45")
        private LocalDateTime completedAt;

        @Schema(description = "Duración total en milisegundos", example = "765312")
        private Long durationMs;

        @Schema(description = "Total de slides procesados", example = "15370")
        private Integer slidesProcessed;

        @Schema(description = "Slides nuevos creados en la BD procesada", example = "14980")
        private Integer slidesCreated;

        @Schema(description = "Slides existentes actualizados", example = "390")
        private Integer slidesUpdated;

        @Schema(description = "Slides que fallaron el procesamiento", example = "0")
        private Integer slidesFailed;

        @Schema(description = "Canales procesados/actualizados", example = "34")
        private Integer channelsProcessed;

        @Schema(description = "Tamaño total original de slides procesados (bytes)", example = "8589934592")
        private Long totalOriginalSize;

        @Schema(description = "Tamaño total procesado (bytes)", example = "196608000")
        private Long totalProcessedSize;

        @Schema(description = "Resultados individuales por slide (puede ser largo)", nullable = true)
        private List<MigrationResult> migrationResults;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "Progreso actual de la cola de sincronización")
    public static class SyncProgressStats {
        @Schema(description = "Total de slides en la cola de sincronización", example = "26688")
        private Long totalSlides;

        @Schema(description = "Slides pendientes de procesar", example = "15370")
        private Long pending;

        @Schema(description = "Slides que se están procesando ahora mismo", example = "1")
        private Long processing;

        @Schema(description = "Slides ya procesados exitosamente", example = "11306")
        private Long completed;

        @Schema(description = "Slides que fallaron el procesamiento", example = "11")
        private Long failed;

        @Schema(description = "Porcentaje de completado (completed / total × 100)", example = "42.36")
        private Double completionPercentage;
    }
}
