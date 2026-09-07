package com.baniterio.api.evento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.BebidaRepository;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.TipoBebida;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/** IT de la confirmación de pago por el administrador (pieza 5 recortada). */
class ConfirmacionPagoIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired EventoRepository eventos;
    @Autowired CuentaRepository cuentas;
    @Autowired AsistenciaEventoRepository asistencias;
    @Autowired FichaBebidaRepository fichas;
    @Autowired BebidaRepository bebidas;
    @Autowired PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void limpiar() {
        fichas.deleteAllInBatch();
        asistencias.deleteAllInBatch();
        eventos.deleteAll(eventos.findAll().stream()
                .filter(e -> e.getNombre().startsWith("IT-pagoadm")).toList());
    }

    record Sesion(Long id, String token) {
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@pagoadm.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Pago").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    Evento sanMiguel(BigDecimal cubatas) {
        CalculadoraCuota.Cuotas c = CalculadoraCuota.derivar(cubatas);
        return eventos.save(Evento.builder().pena(pena())
                .cuenta(cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow())
                .nombre("IT-pagoadm-" + ThreadLocalRandom.current().nextInt(1_000_000))
                .fecha(LocalDate.now().plusDays(20)).fechaFin(LocalDate.now().plusDays(21))
                .cuotaCubatas(c.cubatas()).cuotaCervezas(c.cervezas())
                .cuotaCubatas1Dia(c.cubatas1Dia()).cuotaCervezas1Dia(c.cervezas1Dia())
                .cuotaEmbarazada(c.embarazada()).build());
    }

    /** Apunta a `s` con ficha (solo refresco) y devuelve el id de su asistencia. */
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

    @Test
    void admin_confirma_el_pago_y_el_listado_lo_refleja() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Long asisId = apuntarConFicha(penista, e.getId());

        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("metodo", "BIZUM"))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalPagado").isEqualTo(16.0)
                .jsonPath("$.asistentes[0].estadoPago").isEqualTo("CONFIRMADO_EN_CUENTA")
                .jsonPath("$.asistentes[0].metodoPago").isEqualTo("BIZUM")
                .jsonPath("$.asistentes[0].asistenciaId").isEqualTo(asisId.intValue());
    }

    @Test
    void no_admin_no_puede_confirmar_403() {
        Sesion otro = crearMiembro(RolMembresia.MIEMBRO);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Long asisId = apuntarConFicha(penista, e.getId());

        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + otro.token())
                .body(Map.of("metodo", "BIZUM"))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_EVENTO");
    }

    @Test
    void confirmar_sobre_ficha_sin_cuota_es_409_FICHA_SIN_CUOTA() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(null); // 5 cuotas sin poner => ficha.cuota null
        Long asisId = apuntarConFicha(penista, e.getId());

        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("metodo", "EFECTIVO"))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("FICHA_SIN_CUOTA");
    }

    @Test
    void deshacer_vuelve_a_pendiente() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Long asisId = apuntarConFicha(penista, e.getId());
        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("metodo", "BIZUM")).exchange().expectStatus().isOk();

        http.method(HttpMethod.DELETE)
                .uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalPagado").isEqualTo(0.0)
                .jsonPath("$.asistentes[0].estadoPago").isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    void el_listado_trae_puedoConfirmarPagos_segun_el_rol() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        apuntarConFicha(miembro, e.getId());

        http.get().uri("/api/v1/eventos/" + e.getId() + "/asistentes")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.puedoConfirmarPagos").isEqualTo(true);
        http.get().uri("/api/v1/eventos/" + e.getId() + "/asistentes")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.puedoConfirmarPagos").isEqualTo(false);
    }

    @Test
    void el_detalle_del_penista_trae_su_pago_confirmado() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Long asisId = apuntarConFicha(penista, e.getId());
        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("metodo", "TRANSFERENCIA")).exchange().expectStatus().isOk();

        http.get().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + penista.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.asistencia.ficha.miFicha.estadoPago").isEqualTo("CONFIRMADO_EN_CUENTA")
                .jsonPath("$.asistencia.ficha.miFicha.metodoPago").isEqualTo("TRANSFERENCIA")
                .jsonPath("$.asistencia.ficha.miFicha.pagadoPor").isNotEmpty();
    }
}
