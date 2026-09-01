package com.baniterio.api.auth;

import java.util.Base64;

import com.baniterio.api.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test unitario de {@link JwtService}: se instancia la clase a mano (sin Spring,
 * sin base de datos, sin Docker), por eso es rápido. Cubre el ida y vuelta
 * (generar → verificar), el rechazo de tokens manipulados / caducados / basura,
 * que siempre firma con HS256, y la guardia del secreto de desarrollo.
 */
class JwtServiceTest {

    private static final String SECRET =
        Base64.getEncoder().encodeToString("clave-de-test-con-mas-de-32-bytes-para-hs256".getBytes());

    /** Sin perfiles activos: Spring considera activo el perfil `default`, como en `spring-boot:run`. */
    private static Environment sinPerfiles() {
        return new MockEnvironment();
    }

    private static Environment conPerfiles(String... perfiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(perfiles);
        return env;
    }

    private static JwtService servicio(String secreto, int dias, Environment env) {
        return new JwtService(new AppProperties(new AppProperties.Jwt(secreto, dias), null, null, null, null,
                new AppProperties.Media("./target/media-test")), env);
    }

    private final JwtService jwt = servicio(SECRET, 7, sinPerfiles());

    @Test
    void genera_y_verifica_un_token() {
        Long id = 42L;

        String token = jwt.generar(id, true);
        var principal = jwt.verificar(token);

        assertThat(principal).isPresent();
        assertThat(principal.get().id()).isEqualTo(id);
        assertThat(principal.get().esSuperadmin()).isTrue();
    }

    @Test
    void rechaza_un_token_manipulado() {
        String token = jwt.generar(7L, false);
        assertThat(jwt.verificar(token + "x")).isEmpty();
    }

    @Test
    void rechaza_basura() {
        assertThat(jwt.verificar("no-es-un-jwt")).isEmpty();
    }

    @Test
    void firma_siempre_con_hs256() {
        String token = jwt.generar(7L, false);

        String alg = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .build()
                .parseSignedClaims(token)
                .getHeader()
                .getAlgorithm();

        assertThat(alg).isEqualTo("HS256");
    }

    @Test
    void rechaza_un_token_caducado() {
        // expiracionDias = -1 → el token nace caducado hace un día (determinista, sin sleeps).
        JwtService caducado = servicio(SECRET, -1, sinPerfiles());

        String token = caducado.generar(7L, false);

        assertThat(caducado.verificar(token)).isEmpty();
    }

    // --- Guardia del secreto por defecto de desarrollo ---

    @Test
    void arranca_con_el_secreto_de_desarrollo_si_no_hay_perfiles_activos() {
        assertThat(servicio(JwtService.SECRETO_DEV_POR_DEFECTO, 7, sinPerfiles())).isNotNull();
    }

    @Test
    void arranca_con_el_secreto_de_desarrollo_en_los_perfiles_dev_y_test() {
        assertThat(servicio(JwtService.SECRETO_DEV_POR_DEFECTO, 7, conPerfiles("dev"))).isNotNull();
        assertThat(servicio(JwtService.SECRETO_DEV_POR_DEFECTO, 7, conPerfiles("test"))).isNotNull();
    }

    @Test
    void se_niega_a_arrancar_con_el_secreto_de_desarrollo_fuera_de_desarrollo() {
        assertThatThrownBy(() -> servicio(JwtService.SECRETO_DEV_POR_DEFECTO, 7, conPerfiles("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void arranca_en_produccion_con_un_secreto_propio() {
        assertThat(servicio(SECRET, 7, conPerfiles("prod"))).isNotNull();
    }
}
