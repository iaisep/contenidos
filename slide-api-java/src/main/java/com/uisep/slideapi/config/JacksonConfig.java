package com.uisep.slideapi.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

    private static final int MAX_STRING_LENGTH = Integer.MAX_VALUE;

    // Override global default — affects hypersistence-utils and ALL other ObjectMapper instances
    @PostConstruct
    public void overrideGlobalJacksonConstraints() {
        StreamReadConstraints.overrideDefaultStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxStringLength(MAX_STRING_LENGTH)
                .build()
        );
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.build();
        objectMapper.getFactory().setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxStringLength(MAX_STRING_LENGTH)
                .build()
        );
        return objectMapper;
    }
}
