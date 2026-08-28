package com.baniterio.api.web;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint público de "salud": {@code GET /api/v1/health} responde
 * {@code {"status":"UP", ...}}. Sirve para comprobar rápido que la API está
 * levantada (curl, monitores, el balanceador...). Es una de las 3 rutas
 * abiertas en {@link com.baniterio.api.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", Instant.now());
    }

    public record HealthResponse(String status, Instant timestamp) {
    }
}
