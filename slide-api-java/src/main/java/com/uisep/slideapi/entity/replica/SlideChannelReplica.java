package com.uisep.slideapi.entity.replica;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entidad que mapea la tabla slide_channel (Cursos) de la réplica de Odoo.
 * SOLO LECTURA.
 */
@Entity
@Table(name = "slide_channel", schema = "public")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlideChannelReplica {
    
    @Id
    @Column(name = "id")
    private Integer id;
    
    @Type(JsonType.class)
    @Column(name = "name", columnDefinition = "jsonb")
    private Map<String, String> name;
    
    @Column(name = "active")
    private Boolean active;
    
    @Column(name = "is_published")
    private Boolean isPublished;
    
    @Column(name = "total_views")
    private Integer totalViews;
    
    @Type(JsonType.class)
    @Column(name = "description", columnDefinition = "jsonb")
    private Map<String, String> description;
    
    @Column(name = "create_date")
    private LocalDateTime createDate;
    
    @Column(name = "write_date")
    private LocalDateTime writeDate;
    
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
}
