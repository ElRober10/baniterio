package com.baniterio.api.evento;

import java.math.BigDecimal;
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
 * Test de integración de la sección Eventos: listado paginado, detalle y los
 * caminos de permiso de crear/editar/borrar/solicitar. La BBDD la comparten
 * todos los {@code *IT}: cada caso crea sus propios miembros y eventos.
 */
class EventoIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    EventoRepository eventos;

    @Autowired
    CuentaRepository cuentas;

    @Autowired
    com.baniterio.api.identidad.SolicitudEventoRepository solicitudes;

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

    record Sesion(Long id, String token) {
    }

    Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@evt.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Evt").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    Cuenta cuenta() {
        return cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow();
    }

    Evento sembrarEvento(String nombre, LocalDate fecha, Usuario creador) {
        return eventos.save(Evento.builder().pena(pena()).cuenta(cuenta())
                .nombre(nombre).fecha(fecha).creadoPor(creador).build());
    }

    @Test
    void listado_ordena_los_futuros_por_fecha_ascendente_y_pagina() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        sembrarEvento("IT-list-A", LocalDate.of(2999, 1, 1), null);
        sembrarEvento("IT-list-B", LocalDate.of(2998, 1, 1), null);

        @SuppressWarnings("unchecked")
        Map<String, Object> r = http.get().uri("/api/v1/eventos?pagina=0")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = (List<Map<String, Object>>) r.get("eventos");
        var nombres = lista.stream().map(e -> (String) e.get("nombre")).toList();
        // B (2998) va antes que A (2999): entre futuros, el más cercano primero.
        assertThat(nombres).containsSubsequence("IT-list-B", "IT-list-A");
        assertThat(r.get("puedeCrear")).isEqualTo(false);
        assertThat(r.get("puedeSolicitar")).isEqualTo(true);
    }

    @Test
    void abiertos_incluye_los_futuros_y_no_los_pasados() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        sembrarEvento("IT-abierto-futuro", LocalDate.of(2999, 2, 2), null);
        sembrarEvento("IT-abierto-pasado", LocalDate.now().minusDays(20), null);

        var lista = http.get().uri("/api/v1/eventos/abiertos")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .returnResult().getResponseBody();

        var nombres = lista.stream().map(e -> (String) e.get("nombre")).toList();
        assertThat(nombres).contains("IT-abierto-futuro").doesNotContain("IT-abierto-pasado");
        assertThat(lista).allSatisfy(e -> assertThat(e.get("fecha")).isNotNull());
    }

    @Test
    void un_evento_es_pasado_cuando_han_transcurrido_mas_de_3_dias() {
        Sesion s = crearMiembro(RolMembresia.ADMIN);
        Evento vigente = sembrarEvento("IT-vigente-2d", LocalDate.now().minusDays(2), null);
        Evento pasado = sembrarEvento("IT-pasado-10d", LocalDate.now().minusDays(10), null);

        http.get().uri("/api/v1/eventos/" + vigente.getId())
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.pasado").isEqualTo(false);

        http.get().uri("/api/v1/eventos/" + pasado.getId())
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.pasado").isEqualTo(true);
    }

    @Test
    void detalle_de_evento_inexistente_da_404_EVENTO_NO_ENCONTRADO() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        http.get().uri("/api/v1/eventos/99999999")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("EVENTO_NO_ENCONTRADO");
    }

    @Test
    void admin_ve_puedeCrear_true_y_puedoEditar_en_el_detalle() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sembrarEvento("IT-detalle", LocalDate.of(2999, 5, 5), null);

        http.get().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.puedoEditar").isEqualTo(true)
                .jsonPath("$.puedoBorrar").isEqualTo(true);
    }

    @Test
    void miembro_sin_credito_no_puede_crear_evento_409() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        http.post().uri("/api/v1/eventos")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("nombre", "IT-sin-credito", "fecha", "2999-06-01",
                        "cuentaId", cuenta().getId()))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_CREDITO_EVENTO");
    }

    @Test
    void admin_crea_evento_directo_201() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        http.post().uri("/api/v1/eventos")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "IT-admin-crea", "fecha", "2999-07-01", "lugar", "La sede",
                        "cuentaId", cuenta().getId()))
                .exchange().expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.nombre").isEqualTo("IT-admin-crea")
                .jsonPath("$.cuenta.nombre").isEqualTo("San Miguel")
                .jsonPath("$.puedoEditar").isEqualTo(true);
    }

    @Test
    void crear_sin_cuenta_es_400_VALIDACION() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        http.post().uri("/api/v1/eventos")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "IT-sin-cuenta", "fecha", "2999-07-03"))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("VALIDACION");
    }

    @Test
    void crear_con_cuenta_nueva_la_crea_con_el_nombre_del_evento() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        String nombre = "IT-cuenta-nueva-" + ThreadLocalRandom.current().nextInt(1_000_000);
        http.post().uri("/api/v1/eventos")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", nombre, "fecha", "2999-07-04", "cuentaNueva", true))
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.cuenta.nombre").isEqualTo(nombre);

        assertThat(cuentas.findByPenaIdAndNombre(pena().getId(), nombre)).isPresent();
    }

    @Test
    void crear_con_cuenta_nueva_de_nombre_repetido_es_409() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        http.post().uri("/api/v1/eventos")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "San Miguel", "fecha", "2999-07-05", "cuentaNueva", true))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("CUENTA_YA_EXISTE");
    }

    @Test
    void fecha_fin_anterior_a_fecha_es_validacion_400() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        http.post().uri("/api/v1/eventos")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "IT-fechas", "fecha", "2999-07-02", "fechaFin", "2999-07-01",
                        "cuentaId", cuenta().getId()))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("VALIDACION");
    }

    @Test
    void creador_no_admin_no_puede_editar_su_propio_evento_403() {
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        Usuario creador = usuarios.findById(miembro.id()).orElseThrow();
        Evento e = sembrarEvento("IT-edita-mio", LocalDate.of(2999, 8, 1), creador);

        http.put().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .body(Map.of("nombre", "IT-edita-mio-2", "fecha", "2999-08-02",
                        "cuentaId", cuenta().getId()))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_EVENTO");
    }

    @Test
    void admin_fija_cubatas_al_crear_y_las_otras_4_cuotas_se_derivan() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/eventos")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "IT-cuota", "fecha", "2999-07-20",
                        "cuentaId", cuenta().getId(), "cuotaCubatas", 26))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();

        Long id = ((Number) body.get("id")).longValue();
        assertThat(new BigDecimal(body.get("cuotaCubatas").toString()))
                .isEqualByComparingTo(new BigDecimal("26"));
        Evento guardado = eventos.findById(id).orElseThrow();
        // cubatas = 26 · cervezas = 26-10 = 16 · cubatas 1 día = 26/2+1 = 14 ·
        // cervezas 1 día = 16/2+1 = 9 · embarazada = 5 fijo.
        assertThat(guardado.getCuotaCubatas()).isEqualByComparingTo(new BigDecimal("26.00"));
        assertThat(guardado.getCuotaCervezas()).isEqualByComparingTo(new BigDecimal("16.00"));
        assertThat(guardado.getCuotaCubatas1Dia()).isEqualByComparingTo(new BigDecimal("14.00"));
        assertThat(guardado.getCuotaCervezas1Dia()).isEqualByComparingTo(new BigDecimal("9.00"));
        assertThat(guardado.getCuotaEmbarazada()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void cuota_negativa_es_400_VALIDACION() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        http.post().uri("/api/v1/eventos")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "IT-cuota-neg", "fecha", "2999-07-22",
                        "cuentaId", cuenta().getId(), "cuotaCubatas", -1))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("VALIDACION");
    }

    @Test
    void admin_editar_reenviando_las_cuotas_las_conserva() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sembrarEvento("IT-cuota-edit", LocalDate.of(2999, 8, 5), null);
        e.setCuotaCubatas(new BigDecimal("26.00"));
        eventos.save(e);

        // El editor precarga las cuotas y las reenvía aunque solo cambie el nombre.
        http.put().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "IT-cuota-edit-2", "fecha", "2999-08-05",
                        "cuentaId", cuenta().getId(), "cuotaCubatas", 26))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.cuotaCubatas").isEqualTo(26);

        assertThat(eventos.findById(e.getId()).orElseThrow().getCuotaCubatas())
                .isEqualByComparingTo(new BigDecimal("26.00"));
    }

    @Test
    void editar_cambia_la_cuenta_del_evento() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sembrarEvento("IT-cambia-cuenta", LocalDate.of(2999, 8, 10), null);
        Long chuletas = cuentas.findByPenaIdAndNombre(pena().getId(), "Chuletas Santas")
                .orElseThrow().getId();

        http.put().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "IT-cambia-cuenta", "fecha", "2999-08-10", "cuentaId", chuletas))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.cuenta.id").isEqualTo(chuletas.intValue());
    }

    @Test
    void miembro_no_creador_no_puede_editar_403() {
        Sesion otro = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-edita-ajeno", LocalDate.of(2999, 8, 3), null);

        http.put().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + otro.token())
                .body(Map.of("nombre", "no", "fecha", "2999-08-03", "cuentaId", cuenta().getId()))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_EVENTO");
    }

    @Test
    void admin_borra_evento_lo_oculta_sin_quitarlo_de_bbdd_204() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sembrarEvento("IT-oculta-admin", LocalDate.of(2999, 9, 1), null);

        http.delete().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        assertThat(eventos.findById(e.getId())).isPresent();
        assertThat(eventos.findById(e.getId()).orElseThrow().isOculto()).isTrue();
    }

    @Test
    void evento_oculto_no_sale_en_el_listado() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sembrarEvento("IT-oculto-fuera-listado", LocalDate.of(2999, 9, 2), null);

        http.delete().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        http.get().uri("/api/v1/eventos?pagina=0")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.eventos[?(@.id == " + e.getId() + ")]").doesNotExist();
    }

    @Test
    void miembro_no_admin_no_puede_borrar_403() {
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        Usuario creador = usuarios.findById(miembro.id()).orElseThrow();
        Evento e = sembrarEvento("IT-no-puede-borrar", LocalDate.of(2999, 9, 3), creador);

        http.delete().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_EVENTO");

        assertThat(eventos.findById(e.getId()).orElseThrow().isOculto()).isFalse();
    }

    @Test
    void admin_recupera_un_evento_oculto() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sembrarEvento("IT-recupera", LocalDate.of(2999, 9, 4), null);
        e.setOculto(true);
        eventos.save(e);

        http.put().uri("/api/v1/eventos/" + e.getId() + "/recuperar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.oculto").isEqualTo(false);

        assertThat(eventos.findById(e.getId()).orElseThrow().isOculto()).isFalse();
    }

    @Test
    void miembro_no_admin_no_puede_recuperar_403() {
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-no-puede-recuperar", LocalDate.of(2999, 9, 5), null);
        e.setOculto(true);
        eventos.save(e);

        http.put().uri("/api/v1/eventos/" + e.getId() + "/recuperar")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_EVENTO");
    }

    @Test
    void listar_ocultos_solo_admin() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-listado-ocultos", LocalDate.of(2999, 9, 6), null);
        e.setOculto(true);
        eventos.save(e);

        http.get().uri("/api/v1/eventos/ocultos")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .exchange().expectStatus().isForbidden();

        http.get().uri("/api/v1/eventos/ocultos")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.eventos[?(@.id == " + e.getId() + ")]").exists();
    }

    @Test
    void miembro_solicita_credito_201_y_luego_no_puede_repetir_409() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        http.post().uri("/api/v1/eventos/solicitudes")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("mensaje", "Quiero organizar la cena de Navidad"))
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.estado").isEqualTo("PENDIENTE");

        http.post().uri("/api/v1/eventos/solicitudes")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of())
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("SOLICITUD_EVENTO_YA_PENDIENTE");
    }

    @Test
    void admin_no_puede_solicitar_credito_409_NO_APLICA() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        http.post().uri("/api/v1/eventos/solicitudes")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of())
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("SOLICITUD_EVENTO_NO_APLICA");
    }
}
