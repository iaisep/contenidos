package com.uisep.slideapi.entity.processed;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad para almacenar slides procesados y depurados.
 * Esta tabla contiene los datos limpios para la app.
 */
@Entity
@Table(name = "slides", schema = "slide_api", indexes = {
    @Index(name = "idx_slides_channel_id", columnList = "channel_id"),
    @Index(name = "idx_slides_slide_type", columnList = "slide_type"),
    @Index(name = "idx_slides_active", columnList = "active"),
    @Index(name = "idx_slides_is_published", columnList = "is_published")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedSlide {
    
    @Id
    @Column(name = "id")
    private Integer id;  // Mismo ID que en la réplica
    
    @Column(name = "channel_id")
    private Integer channelId;
    
    @Column(name = "channel_name")
    private String channelName;
    
    @Column(name = "name", length = 500)
    private String name;
    
    @Column(name = "slide_type", length = 50)
    private String slideType;
    
    @Column(name = "html_content", columnDefinition = "text")
    private String htmlContent;  // HTML limpio (sin Base64)
    
    @Column(name = "description", columnDefinition = "text")
    private String description;
    
    @Column(name = "active")
    private Boolean active;
    
    @Column(name = "is_published")
    private Boolean isPublished;
    
    @Column(name = "total_views")
    private Integer totalViews;
    
    // Métricas de depuración
    @Column(name = "original_size_bytes")
    private Long originalSizeBytes;
    
    @Column(name = "processed_size_bytes")
    private Long processedSizeBytes;
    
    @Column(name = "images_extracted")
    private Integer imagesExtracted;
    
    @Column(name = "has_base64_original")
    private Boolean hasBase64Original;
    
    // Fechas de Odoo
    @Column(name = "odoo_create_date")
    private LocalDateTime odooCreateDate;
    
    @Column(name = "odoo_write_date")
    private LocalDateTime odooWriteDate;
    
    // Fechas de procesamiento
    @Column(name = "first_processed_at")
    private LocalDateTime firstProcessedAt;
    
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
    
    @Column(name = "migration_status", length = 50)
    private String migrationStatus;  // PENDING, PROCESSING, COMPLETED, FAILED
    
    @Column(name = "migration_notes", columnDefinition = "text")
    private String migrationNotes;
    
    @PrePersist
    protected void onCreate() {
        if (firstProcessedAt == null) {
            firstProcessedAt = LocalDateTime.now();
        }
        lastSyncedAt = LocalDateTime.now();
        if (migrationStatus == null) {
            migrationStatus = "PENDING";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastSyncedAt = LocalDateTime.now();
    }
}
