package com.uisep.slideapi.entity.processed;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad para almacenar cursos/canales procesados.
 */
@Entity
@Table(name = "channels", schema = "slide_api", indexes = {
    @Index(name = "idx_channels_active", columnList = "active"),
    @Index(name = "idx_channels_is_published", columnList = "is_published")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedChannel {
    
    @Id
    @Column(name = "id")
    private Integer id;  // Mismo ID que en la réplica
    
    @Column(name = "name", length = 500)
    private String name;
    
    @Column(name = "description", columnDefinition = "text")
    private String description;
    
    @Column(name = "active")
    private Boolean active;
    
    @Column(name = "is_published")
    private Boolean isPublished;
    
    @Column(name = "total_views")
    private Integer totalViews;
    
    @Column(name = "slide_count")
    private Integer slideCount;
    
    @Column(name = "total_size_bytes")
    private Long totalSizeBytes;
    
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
    
    @PrePersist
    protected void onCreate() {
        if (firstProcessedAt == null) {
            firstProcessedAt = LocalDateTime.now();
        }
        lastSyncedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastSyncedAt = LocalDateTime.now();
    }
}
