package com.baniterio.api.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint público de "salud": {@code GET /api/v1/health} responde
 * {@code {"status":"UP", ...}}. Sirve para comprobar rápido que la API está
 * levantada (curl, monitores, el balanceador...). Es una de las 3 rutas
 * abiertas en {@link com.baniterio.api.config.SecurityConfig}.
 */
@Tag(name = "Salud", description = "Comprobación de que la API está levantada.")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    /** Comprobación de salud: responde {@code {"status":"UP", ...}}. Público. */
    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", Instant.now());
    }

    public record HealthResponse(String status, Instant timestamp) {
    }
}
