package com.baniterio.api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Identidad identidad, Cors cors) {

    public record Jwt(String secret, int expiracionDias) {
    }

    public record Identidad(String telefonoFundador) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
