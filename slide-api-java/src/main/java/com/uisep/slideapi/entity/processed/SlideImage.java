package com.uisep.slideapi.entity.processed;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad para almacenar las imágenes extraídas de los slides.
 * Reemplaza las imágenes Base64 embebidas en el HTML.
 */
@Entity
@Table(name = "slide_images", schema = "slide_api", indexes = {
    @Index(name = "idx_slide_images_slide_id", columnList = "slide_id"),
    @Index(name = "idx_slide_images_hash", columnList = "image_hash")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlideImage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "slide_id", nullable = false)
    private Integer slideId;
    
    @Column(name = "image_index")
    private Integer imageIndex;  // Orden de la imagen en el slide
    
    @Column(name = "original_filename", length = 255)
    private String originalFilename;
    
    @Column(name = "mime_type", length = 50)
    private String mimeType;  // image/png, image/jpeg, etc.
    
    @Column(name = "image_data", columnDefinition = "bytea")
    private byte[] imageData;  // Imagen decodificada
    
    @Column(name = "image_hash", length = 64)
    private String imageHash;  // SHA-256 para deduplicación
    
    @Column(name = "size_bytes")
    private Long sizeBytes;
    
    @Column(name = "width")
    private Integer width;
    
    @Column(name = "height")
    private Integer height;
    
    // URL para referenciar la imagen
    @Column(name = "public_url", length = 500)
    private String publicUrl;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
