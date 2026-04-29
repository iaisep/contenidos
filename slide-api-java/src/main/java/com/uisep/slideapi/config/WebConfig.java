package com.uisep.slideapi.config;

import com.uisep.slideapi.interceptor.ApiKeyInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ApiKeyInterceptor apiKeyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/v1/admin/health",   // health check público
                "/api-docs/**",           // OpenAPI spec
                "/swagger-ui/**",         // Swagger UI
                "/swagger-ui.html",
                "/actuator/**"
            );
    }
}
