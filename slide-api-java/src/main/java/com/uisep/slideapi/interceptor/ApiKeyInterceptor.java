package com.uisep.slideapi.interceptor;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
public class ApiKeyInterceptor implements HandlerInterceptor {

    @Value("${api.keys}")
    private String apiKeysRaw;

    private Set<String> validApiKeys;

    @PostConstruct
    public void init() {
        validApiKeys = new HashSet<>(Arrays.asList(apiKeysRaw.split(",")));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        String key = request.getHeader("X-API-Key");

        if (key != null && validApiKeys.contains(key.trim())) {
            return true;
        }

        log.warn("Unauthorized: {} {} — missing/invalid X-API-Key from {}",
            request.getMethod(), request.getRequestURI(), request.getRemoteAddr());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Valid X-API-Key header required\"}");
        return false;
    }
}
