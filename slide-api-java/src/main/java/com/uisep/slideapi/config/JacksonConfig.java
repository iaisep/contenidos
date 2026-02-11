package com.uisep.slideapi.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Configuración de Jackson para manejar slides con contenido HTML muy grande (>20MB).
 * El límite por defecto de Jackson es 20MB, pero algunos slides tienen imágenes Base64
 * embebidas que superan este límite.
 */
@Configuration
public class JacksonConfig {

    private static final int MAX_STRING_LENGTH = 100 * 1024 * 1024; // 100MB

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.build();
        
        // Configurar StreamReadConstraints para permitir strings grandes
        objectMapper.getFactory().setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxStringLength(MAX_STRING_LENGTH)
                .build()
        );
        
        return objectMapper;
    }
}
