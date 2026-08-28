package com.baniterio.api.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.baniterio.api.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration expiracion;

    public JwtService(AppProperties props) {
        byte[] material = decodificar(props.jwt().secret());
        this.key = Keys.hmacShaKeyFor(material);
        this.expiracion = Duration.ofDays(props.jwt().expiracionDias());
    }

    public String generar(UUID usuarioId, boolean esSuperadmin) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("esSuperadmin", esSuperadmin)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expiracion)))
                .signWith(key)
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
                    UUID.fromString(claims.getSubject()),
                    Boolean.TRUE.equals(claims.get("esSuperadmin", Boolean.class))));
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
