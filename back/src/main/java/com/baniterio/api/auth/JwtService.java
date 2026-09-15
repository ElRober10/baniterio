package com.baniterio.api.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import com.baniterio.api.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Emite y valida los JSON Web Tokens (JWT) de la aplicación.
 *
 * <p>Un JWT es un texto firmado con tres partes separadas por puntos
 * ({@code cabecera.datos.firma}). Aquí dentro guardamos como "datos" el id del
 * usuario ({@code sub}) y si es superadmin, más una fecha de caducidad (7 días).
 * La "firma" es un hash HMAC-SHA256 calculado con una clave secreta que solo
 * conoce el servidor: si alguien manipula los datos, la firma deja de cuadrar y
 * el token se rechaza. No hace falta guardar nada en base de datos ("stateless").
 *
 * <ul>
 *   <li>{@link #generar} — lo llama el login tras comprobar la contraseña.
 *   <li>{@link #verificar} — lo llama {@link JwtAuthenticationFilter} en cada
 *       petición que trae cabecera {@code Authorization: Bearer <token>}.
 * </ul>
 *
 * <p>El constructor se niega a arrancar si en producción se está usando el
 * secreto de desarrollo por defecto (que está en el repo y por tanto no es
 * secreto).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /**
     * Valor por defecto de {@code app.jwt.secret} en application.yml. Es público y conocido:
     * sirve para arrancar en local sin configurar nada, y está prohibido fuera de desarrollo.
     */
    public static final String SECRETO_DEV_POR_DEFECTO =
            "ZGV2LW9ubHktYmFuaXRlcmlvLXNlY3JldC1jaGFuZ2UtaW4tcHJvZC0xMjM0NQ==";

    /** Perfiles en los que se tolera el secreto de desarrollo ({@code default} = sin perfiles activos). */
    private static final String[] PERFILES_DE_DESARROLLO = {"dev", "test", "default"};

    private final SecretKey key;
    private final Duration expiracion;

    public JwtService(AppProperties props, Environment env) {
        String secret = props.jwt().secret();
        if (SECRETO_DEV_POR_DEFECTO.equals(secret) && !env.matchesProfiles(PERFILES_DE_DESARROLLO)) {
            throw new IllegalStateException("JWT_SECRET debe configurarse fuera de desarrollo");
        }
        byte[] material = decodificar(secret);
        this.key = Keys.hmacShaKeyFor(material);
        this.expiracion = Duration.ofDays(props.jwt().expiracionDias());
    }

    /** Sobrecarga sin {@code esDemo} (equivale a {@code false}): la usan los usuarios normales. */
    public String generar(Long usuarioId, boolean esSuperadmin) {
        return generar(usuarioId, esSuperadmin, false);
    }

    public String generar(Long usuarioId, boolean esSuperadmin, boolean esDemo) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("esSuperadmin", esSuperadmin)
                .claim("esDemo", esDemo)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expiracion)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<UsuarioPrincipal> verificar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new UsuarioPrincipal(
                    Long.parseLong(claims.getSubject()),
                    Boolean.TRUE.equals(claims.get("esSuperadmin", Boolean.class)),
                    Boolean.TRUE.equals(claims.get("esDemo", Boolean.class))));
        } catch (Exception e) {
            log.debug("JWT rechazado: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static byte[] decodificar(String secret) {
        try {
            return Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException noEsBase64) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
