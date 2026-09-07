package com.baniterio.api.evento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoPagoCuota;
import com.baniterio.api.identidad.EstadoPagoDeclarado;
import com.baniterio.api.identidad.EstadoVinculo;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.PagoDeclaradoRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.TipoBebida;
import com.baniterio.api.identidad.BebidaRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.identidad.VinculoPareja;
import com.baniterio.api.identidad.VinculoParejaRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/** IT de la declaración de pago y su confirmación/rechazo por el administrador (pieza 5). */
class PagoDeclaradoIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired EventoRepository eventos;
    @Autowired CuentaRepository cuentas;
    @Autowired AsistenciaEventoRepository asistencias;
    @Autowired FichaBebidaRepository fichas;
    @Autowired BebidaRepository bebidas;
    @Autowired PagoDeclaradoRepository pagos;
    @Autowired VinculoParejaRepository vinculos;
    @Autowired PasswordEncoder passwordEncoder;

    RestTestClient http;
    private final List<Long> vinculosCreados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void limpiar() {
        pagos.deleteAllInBatch();
        fichas.deleteAllInBatch();
        asistencias.deleteAllInBatch();
        vinculos.deleteAllById(vinculosCreados);
        vinculosCreados.clear();
        eventos.deleteAll(eventos.findAll().stream()
                .filter(e -> e.getNombre().startsWith("IT-pagodec")).toList());
    }

    record Sesion(Long id, String token) {
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@pagodec.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Pd").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    Evento sanMiguel() {
        CalculadoraCuota.Cuotas c = CalculadoraCuota.derivar(new BigDecimal("26"));
        return eventos.save(Evento.builder().pena(pena())
                .cuenta(cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow())
                .nombre("IT-pagodec-" + ThreadLocalRandom.current().nextInt(1_000_000))
                .fecha(LocalDate.now().plusDays(20)).fechaFin(LocalDate.now().plusDays(21))
                .cuotaCubatas(c.cubatas()).cuotaCervezas(c.cervezas())
                .cuotaCubatas1Dia(c.cubatas1Dia()).cuotaCervezas1Dia(c.cervezas1Dia())
                .cuotaEmbarazada(c.embarazada()).build());
    }

    Long apuntarConFicha(Sesion s, Long eventoId) {
        Long refresco = bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.REFRESCO, "Coca-Cola")
                .orElseThrow().getId();
        Map<String, Object> ficha = new HashMap<>();
        ficha.put("estado", "APUNTADO");
        ficha.put("refrescoBebidaId", refresco);
        ficha.put("alternativa", "NADA");
        ficha.put("asisteDia1", true);
        ficha.put("asisteDia2", true);
        http.put().uri("/api/v1/eventos/" + eventoId + "/ficha-bebida")
                .header(AUTHORIZATION, "Bearer " + s.token()).body(ficha)
                .exchange().expectStatus().isOk();
        return asistencias.findByEventoIdAndUsuarioId(eventoId, s.id()).orElseThrow().getId();
    }

    void vincularPareja(Sesion a, Sesion b) {
        Usuario ua = usuarios.findById(a.id()).orElseThrow();
        Usuario ub = usuarios.findById(b.id()).orElseThrow();
        VinculoPareja v = vinculos.save(VinculoPareja.builder()
                .solicitante(ua).parejaUsuario(ub)
                .parejaNombre(ub.getNombre()).parejaTelefono(ub.getTelefono())
                .estado(EstadoVinculo.ACEPTADO).build());
        vinculosCreados.add(v.getId());
    }

    void declarar(Sesion s, Long eventoId, String metodo, double importe, List<Long> cubreUsuarioIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("importe", importe);
        body.put("metodo", metodo);
        body.put("cubreUsuarioIds", cubreUsuarioIds);
        http.post().uri("/api/v1/eventos/" + eventoId + "/pagos-declarados")
                .header(AUTHORIZATION, "Bearer " + s.token()).body(body)
                .exchange().expectStatus().isOk();
    }

    @Test
    void declarar_crea_pendiente_y_aparece_en_la_cola_del_admin() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        apuntarConFicha(penista, e.getId());

        declarar(penista, e.getId(), "BIZUM", 16.0, List.of());

        http.get().uri("/api/v1/admin/pagos-declarados")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].metodoPago").isEqualTo("BIZUM")
                .jsonPath("$[0].cubre.length()").isEqualTo(1);
    }

    @Test
    void declarar_dos_veces_es_409() {
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "BIZUM", 16.0, List.of());

        http.post().uri("/api/v1/eventos/" + e.getId() + "/pagos-declarados")
                .header(AUTHORIZATION, "Bearer " + penista.token())
                .body(Map.of("importe", 16.0, "metodo", "EFECTIVO"))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("PAGO_DECLARADO_YA_PENDIENTE");
    }

    @Test
    void anular_borra_la_declaracion() {
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "BIZUM", 16.0, List.of());

        http.delete().uri("/api/v1/eventos/" + e.getId() + "/pagos-declarados/mia")
                .header(AUTHORIZATION, "Bearer " + penista.token())
                .exchange().expectStatus().isOk();

        assertThat(pagos.findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado.PENDIENTE)).isEmpty();
    }

    @Test
    void admin_confirma_marca_la_ficha_pagada() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        Long asisId = apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "TRANSFERENCIA", 16.0, List.of());
        Long pagoId = pagos.findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado.PENDIENTE).get(0).getId();

        http.post().uri("/api/v1/admin/pagos-declarados/" + pagoId + "/confirmar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        assertThat(fichas.findByAsistenciaId(asisId)).get()
                .satisfies(f -> assertThat(f.getEstadoPago()).isEqualTo(EstadoPagoCuota.CONFIRMADO_EN_CUENTA));
        http.get().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + penista.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.asistencia.ficha.miFicha.estadoPago").isEqualTo("CONFIRMADO_EN_CUENTA");
    }

    @Test
    void admin_rechaza_deja_la_cuota_pendiente_y_marca_RECHAZADA() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        Long asisId = apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "BIZUM", 16.0, List.of());
        Long pagoId = pagos.findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado.PENDIENTE).get(0).getId();

        http.post().uri("/api/v1/admin/pagos-declarados/" + pagoId + "/rechazar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        assertThat(fichas.findByAsistenciaId(asisId)).get()
                .satisfies(f -> assertThat(f.getEstadoPago()).isEqualTo(EstadoPagoCuota.PENDIENTE_PAGO));
        assertThat(pagos.findById(pagoId)).get()
                .satisfies(p -> assertThat(p.getEstado()).isEqualTo(EstadoPagoDeclarado.RECHAZADA));
        http.get().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + penista.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.asistencia.ficha.miFicha.miPagoDeclarado.estado").isEqualTo("RECHAZADA");
    }

    @Test
    void no_admin_no_ve_la_cola_403() {
        Sesion otro = crearMiembro(RolMembresia.MIEMBRO);
        http.get().uri("/api/v1/admin/pagos-declarados")
                .header(AUTHORIZATION, "Bearer " + otro.token())
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO");
    }

    @Test
    void el_atajo_del_modal_asistentes_cierra_la_declaracion_pendiente() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        Long asisId = apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "BIZUM", 16.0, List.of());
        Long pagoId = pagos.findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado.PENDIENTE).get(0).getId();

        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("metodo", "EFECTIVO"))
                .exchange().expectStatus().isOk();

        assertThat(pagos.findById(pagoId)).get()
                .satisfies(p -> assertThat(p.getEstado()).isEqualTo(EstadoPagoDeclarado.CONFIRMADA));
    }

    @Test
    void declarar_cubriendo_a_la_pareja_confirma_marca_las_dos_fichas() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion yo = crearMiembro(RolMembresia.MIEMBRO);
        Sesion pareja = crearMiembro(RolMembresia.MIEMBRO);
        vincularPareja(yo, pareja);
        Evento e = sanMiguel();
        Long asisMio = apuntarConFicha(yo, e.getId());
        Long asisPareja = apuntarConFicha(pareja, e.getId());

        declarar(yo, e.getId(), "BIZUM", 32.0, List.of(pareja.id()));
        Long pagoId = pagos.findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado.PENDIENTE).get(0).getId();
        http.post().uri("/api/v1/admin/pagos-declarados/" + pagoId + "/confirmar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        assertThat(fichas.findByAsistenciaId(asisMio)).get()
                .satisfies(f -> assertThat(f.getEstadoPago()).isEqualTo(EstadoPagoCuota.CONFIRMADO_EN_CUENTA));
        assertThat(fichas.findByAsistenciaId(asisPareja)).get()
                .satisfies(f -> assertThat(f.getEstadoPago()).isEqualTo(EstadoPagoCuota.CONFIRMADO_EN_CUENTA));
    }
}
