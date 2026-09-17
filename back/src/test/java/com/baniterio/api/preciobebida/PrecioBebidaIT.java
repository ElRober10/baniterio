package com.baniterio.api.preciobebida;

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

/** Precio bebidas: listado de eventos (años → eventos), visible a cualquier miembro. */
class PrecioBebidaIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired CuentaRepository cuentas;
    @Autowired EventoRepository eventos;
    @Autowired PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    String token() {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@pb.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("PB").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(RolMembresia.MIEMBRO).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("token");
    }

    long crearEvento(String nombre, LocalDate fecha, boolean oculto) {
        Cuenta c = cuentas.save(Cuenta.builder().pena(pena()).nombre(nombre + " cuenta")
                .llevaFichaBebida(false).build());
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(c).nombre(nombre)
                .fecha(fecha).oculto(oculto).build());
        return e.getId();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> eventos(String token) {
        return http.get().uri("/api/v1/precio-bebida/eventos")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(List.class).returnResult().getResponseBody();
    }

    @Test
    void un_miembro_sin_area_ve_los_eventos_no_ocultos() {
        String tok = token();
        long id2026 = crearEvento("Precio bebida IT 2026", LocalDate.of(2026, 9, 25), false);
        long idOculto = crearEvento("Precio bebida IT oculto", LocalDate.of(2026, 9, 26), true);

        List<Map<String, Object>> lista = eventos(tok);

        assertThat(lista).extracting(l -> ((Number) l.get("id")).longValue()).contains(id2026);
        assertThat(lista).extracting(l -> ((Number) l.get("id")).longValue()).doesNotContain(idOculto);
    }

    @Test
    void devuelve_eventos_de_varios_anios_para_agrupar_en_el_front() {
        String tok = token();
        long id2025 = crearEvento("Precio bebida IT anio pasado", LocalDate.of(2025, 5, 1), false);
        long id2026 = crearEvento("Precio bebida IT anio actual", LocalDate.of(2026, 9, 25), false);

        List<Map<String, Object>> lista = eventos(tok);
        List<Long> ids = lista.stream().map(l -> ((Number) l.get("id")).longValue()).toList();

        assertThat(ids).contains(id2025, id2026);
    }
}
