package com.baniterio.api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración propia de la aplicación, tipada. Spring lee del fichero
 * {@code application.yml} lo que cuelga de la clave {@code app:} y lo vuelca
 * en este record (por eso {@code @ConfigurationProperties("app")}). El
 * escaneo lo activa {@code @ConfigurationPropertiesScan} en la clase principal.
 *
 * <p>Ventaja frente a leer cada valor con {@code @Value("${...}")} suelto: aquí
 * están reunidos, con tipos, y el compilador avisa si te equivocas de nombre.
 *
 * <p>Mapeo yaml → Java ("relaxed binding"): {@code app.jwt.expiracion-dias} →
 * {@code jwt().expiracionDias()}, {@code app.identidad.telefono-fundador} →
 * {@code identidad().telefonoFundador()}, etc.
 */
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Identidad identidad, Cors cors) {

    public record Jwt(String secret, int expiracionDias) {
    }

    public record Identidad(String telefonoFundador) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
