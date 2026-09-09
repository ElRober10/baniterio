package com.baniterio.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la documentación OpenAPI que genera springdoc.
 *
 * <p>El esquema {@code bearer-jwt} hace aparecer el botón <b>Authorize</b> en
 * Swagger UI: pegas ahí el token que devuelve {@code POST /api/v1/auth/login}
 * y a partir de ese momento la UI manda la cabecera
 * {@code Authorization: Bearer ...} en cada petición, así puedes probar los
 * endpoints protegidos.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Bañiterio API", version = "v1",
                description = "API de la plataforma de la peña."),
        security = @SecurityRequirement(name = "bearer-jwt"))
@SecurityScheme(name = "bearer-jwt", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
