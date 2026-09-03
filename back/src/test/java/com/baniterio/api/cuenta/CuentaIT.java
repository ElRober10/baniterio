package com.baniterio.api.cuenta;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Test de integración de la sección Cuentas: listado (con las 3 cuentas
 * sembradas en V17), detalle y 404. La BBDD la comparten todos los {@code *IT}:
 * cada caso crea sus propios miembros y cuentas.
 */
class CuentaIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    CuentaRepository cuentas;

    @Autowired
    EventoRepository eventos;

    @Autowired
    PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    String token(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@cta.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Cta").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("token");
    }

    @Test
    void listado_incluye_las_cuentas_sembradas() {
        String token = token(RolMembresia.MIEMBRO);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = http.get().uri("/api/v1/cuentas")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(List.class).returnResult().getResponseBody();

        var nombres = lista.stream().map(c -> (String) c.get("nombre")).toList();
        assertThat(nombres).contains("Chuletas Santas", "Migas Santas", "San Miguel");
    }

    @Test
    void detalle_devuelve_nombre_y_descripcion() {
        String token = token(RolMembresia.MIEMBRO);
        Cuenta c = cuentas.save(Cuenta.builder()
                .pena(pena()).nombre("IT-cuenta-detalle").descripcion("Una cuenta de prueba").build());

        http.get().uri("/api/v1/cuentas/" + c.getId())
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.nombre").isEqualTo("IT-cuenta-detalle")
                .jsonPath("$.descripcion").isEqualTo("Una cuenta de prueba");
    }

    @Test
    void listado_ordena_por_el_evento_futuro_mas_proximo() {
        String token = token(RolMembresia.MIEMBRO);
        long sufijo = ThreadLocalRandom.current().nextLong(1_000_000);
        Cuenta lejos = cuentas.save(Cuenta.builder().pena(pena()).nombre("IT-Cta-Lejos-" + sufijo).build());
        Cuenta cerca = cuentas.save(Cuenta.builder().pena(pena()).nombre("IT-Cta-Cerca-" + sufijo).build());
        Cuenta sinEvento = cuentas.save(Cuenta.builder().pena(pena()).nombre("IT-Cta-SinEvento-" + sufijo).build());

        eventos.save(Evento.builder().pena(pena()).cuenta(lejos)
                .nombre("IT-evt-lejos-" + sufijo).fecha(LocalDate.of(2999, 1, 1)).build());
        eventos.save(Evento.builder().pena(pena()).cuenta(cerca)
                .nombre("IT-evt-cerca-" + sufijo).fecha(LocalDate.of(2998, 1, 1)).build());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = http.get().uri("/api/v1/cuentas")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(List.class).returnResult().getResponseBody();

        var nombres = lista.stream().map(c -> (String) c.get("nombre")).toList();
        assertThat(nombres).containsSubsequence(cerca.getNombre(), lejos.getNombre(), sinEvento.getNombre());
    }

    @Test
    void detalle_de_cuenta_inexistente_da_404_CUENTA_NO_ENCONTRADA() {
        String token = token(RolMembresia.MIEMBRO);
        http.get().uri("/api/v1/cuentas/99999999")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("CUENTA_NO_ENCONTRADA");
    }

    @Test
    void sin_token_da_401() {
        http.get().uri("/api/v1/cuentas")
                .exchange().expectStatus().isUnauthorized();
    }
}
