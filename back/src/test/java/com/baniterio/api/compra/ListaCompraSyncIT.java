package com.baniterio.api.compra;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoAsistencia;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PermisoArea;
import com.baniterio.api.identidad.PermisoAreaRepository;
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
 * Lista de la compra, bloque 1.5: sincronización de {@code linea_compra_evento},
 * botón "Comprado" (envío al inventario de la fiesta), bloqueo del auto-cálculo
 * y "Devolver a la lista".
 */
class ListaCompraSyncIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired PermisoAreaRepository permisos;
    @Autowired CuentaRepository cuentas;
    @Autowired EventoRepository eventos;
    @Autowired AsistenciaEventoRepository asistencias;
    @Autowired LineaCompraEventoRepository lineas;
    @Autowired PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    String token(RolMembresia rol, boolean conArea) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@sync.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("SY").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        if (conArea) {
            permisos.save(PermisoArea.builder().usuario(u).area(AreaProtegida.INVENTARIO).build());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("token");
    }

    long crearEventoDeUnDia(String nombre) {
        Cuenta c = cuentas.save(Cuenta.builder().pena(pena()).nombre(nombre + " cuenta")
                .llevaFichaBebida(false).build());
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(c).nombre(nombre)
                .fecha(LocalDate.now().minusDays(60)).oculto(false).build());
        return e.getId();
    }

    void apuntar(long eventoId, int cuantos) {
        var evento = eventos.findById(eventoId).orElseThrow();
        for (int i = 0; i < cuantos; i++) {
            String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
            var u = usuarios.save(Usuario.builder()
                    .telefono(tel).email(tel + "@sync.test")
                    .passwordHash(passwordEncoder.encode("secreto1"))
                    .nombre("S" + tel).apellidos("Y").esSuperadmin(false).activo(true).build());
            asistencias.save(AsistenciaEvento.builder()
                    .evento(evento).usuario(u).estado(EstadoAsistencia.APUNTADO).build());
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> getMap(String uri, String token) {
        return http.get().uri(uri).header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> reglasAdmin(long eventoId, String token) {
        Map<String, Object> body = getMap("/api/v1/admin/lista-compra/eventos/" + eventoId, token);
        return (List<Map<String, Object>>) body.get("reglas");
    }

    long idDeRegla(List<Map<String, Object>> reglas, String nombre) {
        return ((Number) reglas.stream().filter(r -> nombre.equals(r.get("nombre")))
                .findFirst().orElseThrow().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    Double cantidadLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) {
                continue;
            }
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    return ((Number) l.get("cantidad")).doubleValue();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    long idLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) {
                continue;
            }
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    return ((Number) l.get("id")).longValue();
                }
            }
        }
        throw new AssertionError("línea no encontrada: " + categoria + "/" + nombre);
    }

    @SuppressWarnings("unchecked")
    Boolean compradaLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) {
                continue;
            }
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    return (Boolean) l.get("comprada");
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    Double cantidadFiesta(Map<String, Object> body, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            for (Map<String, Object> a : (List<Map<String, Object>>) cat.get("articulos")) {
                if (nombre.equals(a.get("nombre"))) {
                    return ((Number) a.get("cantidad")).doubleValue();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    long idArticuloFiesta(Map<String, Object> body, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            for (Map<String, Object> a : (List<Map<String, Object>>) cat.get("articulos")) {
                if (nombre.equals(a.get("nombre"))) {
                    return ((Number) a.get("id")).longValue();
                }
            }
        }
        throw new AssertionError("artículo de fiesta no encontrado: " + nombre);
    }

    // --- Task 2: sincronización ---------------------------------------------

    @Test
    void la_lectura_materializa_las_lineas_y_las_actualiza_al_cambiar_los_apuntados() {
        long eventoId = crearEventoDeUnDia("Sync IT materializa");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 10);

        var body1 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body1, "LIMPIEZA", "Platos")).isEqualTo(30.0);

        apuntar(eventoId, 10);
        var body2 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body2, "LIMPIEZA", "Platos")).isEqualTo(60.0);
    }

    @Test
    void quitar_una_regla_borra_su_linea_no_comprada() {
        long eventoId = crearEventoDeUnDia("Sync IT borra");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 5);
        getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);

        long platosRegla = idDeRegla(reglasAdmin(eventoId, admin), "Platos");
        http.put().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + platosRegla)
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("activa", false))
                .exchange().expectStatus().isNoContent();

        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isNull();
    }

    @Test
    void la_respuesta_trae_bloqueada_false_y_las_lineas_traen_id() {
        long eventoId = crearEventoDeUnDia("Sync IT campos");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 3);
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(body.get("bloqueada")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        var cats = (List<Map<String, Object>>) body.get("categorias");
        @SuppressWarnings("unchecked")
        var lineas0 = (List<Map<String, Object>>) cats.get(0).get("lineas");
        assertThat(lineas0.get(0)).containsKeys("id", "comprada");
    }

    // --- Task 3: marcar comprada ------------------------------------------

    @Test
    void marcar_comprada_mueve_la_cantidad_al_inventario_de_la_fiesta() {
        long eventoId = crearEventoDeUnDia("Sync IT comprado");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        long platosLinea = idLinea(body, "LIMPIEZA", "Platos");

        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNoContent();

        var fiesta = getMap("/api/v1/inventario/evento/" + eventoId, admin);
        assertThat(cantidadFiesta(fiesta, "Platos")).isEqualTo(12.0);

        var body2 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(compradaLinea(body2, "LIMPIEZA", "Platos")).isTrue();
    }

    @Test
    void una_linea_comprada_no_cambia_aunque_cambien_los_apuntados() {
        long eventoId = crearEventoDeUnDia("Sync IT congela");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        long platosLinea = idLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Platos");
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + admin).exchange().expectStatus().isNoContent();

        apuntar(eventoId, 10);
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);
    }

    @Test
    void marcar_comprada_una_linea_de_otro_evento_es_404() {
        long eventoId = crearEventoDeUnDia("Sync IT 404");
        String admin = token(RolMembresia.ADMIN, false);
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/99999999/comprado")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("LINEA_COMPRA_NO_ENCONTRADA");
    }

    @Test
    void marcar_comprada_sin_area_es_403() {
        long eventoId = crearEventoDeUnDia("Sync IT comprado 403");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 3);
        long platosLinea = idLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Platos");
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + token(RolMembresia.MIEMBRO, false))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_INVENTARIO");
    }

    // --- Task 4: bloqueo -------------------------------------------------

    @Test
    void bloquear_detiene_la_sincronizacion() {
        long eventoId = crearEventoDeUnDia("Sync IT bloqueo");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);

        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", true))
                .exchange().expectStatus().isNoContent();

        apuntar(eventoId, 8);
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(body.get("bloqueada")).isEqualTo(true);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);

        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", false))
                .exchange().expectStatus().isNoContent();
        var body2 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body2, "LIMPIEZA", "Platos")).isEqualTo(36.0);
    }

    @Test
    void bloquear_sincroniza_una_ultima_vez() {
        long eventoId = crearEventoDeUnDia("Sync IT bloqueo sync");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", true))
                .exchange().expectStatus().isNoContent();
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);
    }

    @Test
    void bloquear_sin_area_es_403() {
        long eventoId = crearEventoDeUnDia("Sync IT bloqueo 403");
        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + token(RolMembresia.MIEMBRO, false))
                .body(Map.of("bloqueada", true))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_INVENTARIO");
    }

    // --- Task 5: devolver a la lista ------------------------------------

    @Test
    void devolver_a_la_lista_revierte_solo_lo_comprado_y_la_linea_vuelve_a_pendiente() {
        long eventoId = crearEventoDeUnDia("Sync IT devolver");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        long platosLinea = idLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Platos");
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + admin).exchange().expectStatus().isNoContent();

        long artId = idArticuloFiesta(getMap("/api/v1/inventario/evento/" + eventoId, admin), "Platos");

        http.post().uri("/api/v1/inventario/evento/" + eventoId + "/" + artId + "/devolver-a-lista")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNoContent();

        assertThat(cantidadFiesta(getMap("/api/v1/inventario/evento/" + eventoId, admin), "Platos")).isNull();
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(compradaLinea(body, "LIMPIEZA", "Platos")).isFalse();
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);
    }

    @Test
    void devolver_a_la_lista_articulo_inexistente_es_404() {
        long eventoId = crearEventoDeUnDia("Sync IT devolver 404");
        String admin = token(RolMembresia.ADMIN, false);
        http.post().uri("/api/v1/inventario/evento/" + eventoId + "/99999999/devolver-a-lista")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("ARTICULO_EVENTO_NO_ENCONTRADO");
    }
}
