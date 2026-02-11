package com.uisep.slideapi.entity.replica;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entidad que mapea la tabla slide_slide de la réplica de Odoo.
 * SOLO LECTURA - No se deben hacer modificaciones a esta entidad.
 */
@Entity
@Table(name = "slide_slide", schema = "public")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlideSlideReplica {
    
    @Id
    @Column(name = "id")
    private Integer id;
    
    @Column(name = "channel_id")
    private Integer channelId;
    
    @Type(JsonType.class)
    @Column(name = "name", columnDefinition = "jsonb")
    private Map<String, String> name;
    
    @Column(name = "slide_type")
    private String slideType;
    
    @Type(JsonType.class)
    @Column(name = "html_content", columnDefinition = "jsonb")
    private Map<String, String> htmlContent;
    
    @Column(name = "active")
    private Boolean active;
    
    @Column(name = "is_published")
    private Boolean isPublished;
    
    @Column(name = "total_views")
    private Integer totalViews;
    
    @Column(name = "embeddings_json", columnDefinition = "text")
    private String embeddingsJson;
    
    @Column(name = "convert", columnDefinition = "text")
    private String convert;
    
    @Column(name = "preconverthtml", columnDefinition = "text")
    private String preconverthtml;
    
    @Column(name = "preconvertdes", columnDefinition = "text")
    private String preconvertdes;
    
    @Column(name = "description", columnDefinition = "text")
    private String description;
    
    @Type(JsonType.class)
    @Column(name = "html_embed_code", columnDefinition = "jsonb")
    private Map<String, String> htmlEmbedCode;
    
    @Column(name = "create_date")
    private LocalDateTime createDate;
    
    @Column(name = "write_date")
    private LocalDateTime writeDate;
    
    @Column(name = "create_uid")
    private Integer createUid;
    
    @Column(name = "write_uid")
    private Integer writeUid;
    
    /**
     * Obtiene el nombre en español o el primer idioma disponible.
     */
    public String getNameEs() {
        if (name == null) return null;
        return name.getOrDefault("es_MX", 
               name.getOrDefault("es_ES", 
               name.getOrDefault("en_US", 
               name.values().stream().findFirst().orElse(null))));
    }
    
    /**
     * Obtiene el contenido HTML en español o el primer idioma disponible.
     */
    public String getHtmlContentEs() {
        if (htmlContent == null) return null;
        return htmlContent.getOrDefault("es_MX",
               htmlContent.getOrDefault("es_ES",
               htmlContent.getOrDefault("en_US",
               htmlContent.values().stream().findFirst().orElse(null))));
    }
    
    /**
     * Verifica si el contenido tiene imágenes en Base64.
     */
    public boolean hasBase64Images() {
        String content = getHtmlContentEs();
        return content != null && content.contains("data:image") && content.contains("base64");
    }
    
    /**
     * Calcula el tamaño aproximado del contenido HTML en bytes.
     */
    public long getHtmlContentSizeBytes() {
        if (htmlContent == null) return 0;
        return htmlContent.values().stream()
            .filter(v -> v != null)
            .mapToLong(String::length)
            .sum();
    }
}
