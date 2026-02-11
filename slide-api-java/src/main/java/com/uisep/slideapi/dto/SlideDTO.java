package com.uisep.slideapi.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs para respuestas de la API.
 */
public class SlideDTO {
    
    /**
     * DTO para un slide procesado (respuesta para la app).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlideResponse {
        private Integer id;
        private Integer channelId;
        private String channelName;
        private String name;
        private String slideType;
        private String htmlContent;
        private String description;
        private Boolean active;
        private Boolean isPublished;
        private Integer totalViews;
        private Long originalSizeBytes;
        private Long processedSizeBytes;
        private Integer imagesExtracted;
        private LocalDateTime odooCreateDate;
        private LocalDateTime lastSyncedAt;
        private String migrationStatus;
        private List<ImageInfo> images;
    }
    
    /**
     * DTO para información de imagen.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageInfo {
        private Long id;
        private String filename;
        private String mimeType;
        private Long sizeBytes;
        private String publicUrl;
    }
    
    /**
     * DTO para listado resumido de slides.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlideListItem {
        private Integer id;
        private Integer channelId;
        private String channelName;
        private String name;
        private String slideType;
        private Boolean active;
        private Boolean isPublished;
        private Integer totalViews;
        private Long originalSizeBytes;
        private Long processedSizeBytes;
        private String migrationStatus;
    }
    
    /**
     * DTO para canal procesado.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelResponse {
        private Integer id;
        private String name;
        private String description;
        private Boolean active;
        private Boolean isPublished;
        private Integer totalViews;
        private Integer slideCount;
        private Long totalSizeBytes;
        private LocalDateTime odooCreateDate;
        private LocalDateTime lastSyncedAt;
    }
    
    /**
     * DTO para estadísticas de depuración.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepurationStats {
        private Long totalSlides;
        private Long activeSlides;
        private Long inactiveSlides;
        private Long slidesWithBase64;
        private Long totalOriginalSizeBytes;
        private Long totalProcessedSizeBytes;
        private Long totalSavedBytes;
        private Double savingsPercentage;
        private Long totalImagesExtracted;
        private PendingActions pendingActions;
    }
    
    /**
     * DTO para acciones pendientes de depuración.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingActions {
        private Long inactiveSlidesToDelete;
        private Long inactiveSlidesSize;
        private Long slidesToMigrateBase64;
        private Long base64Size;
        private Long temporaryFieldsToClean;
        private Long temporaryFieldsSize;
    }
    
    /**
     * DTO para resultado de migración.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MigrationResult {
        private Integer slideId;
        private String slideName;
        private String status;  // SUCCESS, FAILED, SKIPPED
        private Integer imagesExtracted;
        private Long originalSize;
        private Long newSize;
        private Long savedBytes;
        private String message;
        private LocalDateTime processedAt;
    }
    
    /**
     * DTO para resultado de sincronización batch.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncResult {
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Long durationMs;
        private Integer slidesProcessed;
        private Integer slidesCreated;
        private Integer slidesUpdated;
        private Integer slidesFailed;
        private Integer channelsProcessed;
        private Long totalOriginalSize;
        private Long totalProcessedSize;
        private List<MigrationResult> migrationResults;
    }
}
