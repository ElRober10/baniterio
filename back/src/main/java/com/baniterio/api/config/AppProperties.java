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
public record AppProperties(Jwt jwt, Identidad identidad, Cors cors, Email email, Push push, Media media) {

    public record Jwt(String secret, int expiracionDias) {
    }

    public record Identidad(String telefonoFundador) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * Config del envío de correo. {@code modo} elige la implementación de
     * {@code ServicioEmail} ({@code "log"} = solo escribe en el log, por defecto;
     * {@code "smtp"} = envía de verdad). {@code from} es el remitente que se pone
     * en los correos. {@code enlaceRegistro} es la URL del front donde la persona
     * completa el registro / inicia sesión (se mete en las plantillas).
     */
    public record Email(String modo, String from, String enlaceRegistro) {
    }

    /**
     * Config del envío de notificaciones push. {@code modo}: {@code "log"}
     * (por defecto, solo traza en el log — no requiere credenciales) o
     * {@code "fcm"} (envía de verdad vía Firebase). {@code credencialesJson} es
     * la ruta al fichero de cuenta de servicio de Firebase (solo modo fcm).
     */
    public record Push(String modo, String credencialesJson) {
    }

    /**
     * Carpeta donde el backend guarda las fotos de perfil subidas (los avatares
     * por defecto van dentro del jar). En producción debe apuntar a un volumen
     * persistente.
     */
    public record Media(String dir) {
    }
}
