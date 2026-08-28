package com.baniterio.api.auth;

import java.util.Base64;
import java.util.UUID;

import com.baniterio.api.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET =
        Base64.getEncoder().encodeToString("clave-de-test-con-mas-de-32-bytes-para-hs256".getBytes());

    private final JwtService jwt = new JwtService(
        new AppProperties(new AppProperties.Jwt(SECRET, 7), null, null));

    @Test
    void genera_y_verifica_un_token() {
        UUID id = UUID.randomUUID();

        String token = jwt.generar(id, true);
        var principal = jwt.verificar(token);

        assertThat(principal).isPresent();
        assertThat(principal.get().id()).isEqualTo(id);
        assertThat(principal.get().esSuperadmin()).isTrue();
    }

    @Test
    void rechaza_un_token_manipulado() {
        String token = jwt.generar(UUID.randomUUID(), false);
        assertThat(jwt.verificar(token + "x")).isEmpty();
    }

    @Test
    void rechaza_basura() {
        assertThat(jwt.verificar("no-es-un-jwt")).isEmpty();
    }
}
