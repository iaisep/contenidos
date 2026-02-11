package com.uisep.slideapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SlideApiApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SlideApiApplication.class, args);
    }
}
