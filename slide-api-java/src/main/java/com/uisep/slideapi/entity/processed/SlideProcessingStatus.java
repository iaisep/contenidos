package com.uisep.slideapi.entity.processed;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tabla de control para trackear qué slides ya fueron sincronizados.
 * Permite reanudar sincronización desde donde quedó.
 */
@Entity
@Table(name = "slide_processing_status")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideProcessingStatus {
    
    @Id
    private Integer slideId;  // ID del slide en la réplica
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProcessingStatus status;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
    
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "original_size_bytes")
    private Long originalSizeBytes;
    
    @Column(name = "processed_size_bytes")
    private Long processedSizeBytes;
    
    @Column(name = "images_extracted")
    private Integer imagesExtracted;
    
    public enum ProcessingStatus {
        PENDING,      // No ha sido procesado
        PROCESSING,   // En proceso
        COMPLETED,    // Completado exitosamente
        FAILED        // Falló después de reintentos
    }
}
