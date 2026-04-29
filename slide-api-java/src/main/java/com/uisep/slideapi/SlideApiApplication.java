package com.uisep.slideapi;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SlideApiApplication {

    static {
        // Must be set before ANY class loading (including hypersistence-utils static init)
        // Odoo slides can have 80MB+ JSONB fields; Jackson's default limit is 20MB
        StreamReadConstraints.overrideDefaultStreamReadConstraints(
            StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build()
        );
    }

    public static void main(String[] args) {
        SpringApplication.run(SlideApiApplication.class, args);
    }
}
