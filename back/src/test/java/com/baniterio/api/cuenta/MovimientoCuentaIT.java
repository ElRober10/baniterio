package com.baniterio.api.cuenta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.evento.CalculadoraCuota;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.BebidaRepository;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoPagoDeclarado;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PagoDeclaradoRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/** IT del libro de movimientos y el saldo de una cuenta (V28). */
class MovimientoCuentaIT extends IntegrationTest {

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
    @Autowired MovimientoCuentaRepository movimientos;
    @Autowired PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void limpiar() {
        movimientos.deleteAll(movimientos.findAll().stream()
                .filter(m -> m.getOrigen() != OrigenMovimiento.SALDO_INICIAL || m.getAnio() != 2026)
                .toList());
        cuentas.findById(cuentaSanMiguelId()).filter(cta -> cta.getAnioActual() != 2026).ifPresent(cta -> {
            cta.setAnioActual(2026);
            cuentas.save(cta);
        });
        pagos.deleteAllInBatch();
        fichas.deleteAllInBatch();
        asistencias.deleteAllInBatch();
        eventos.deleteAll(eventos.findAll().stream()
                .filter(e -> e.getNombre().startsWith("IT-mov")).toList());
    }

    record Sesion(Long id, String token) {
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    Long cuentaSanMiguelId() {
        return cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow().getId();
    }

    Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@mov.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Mv").esSuperadmin(false).activo(true).build());
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
                .nombre("IT-mov-" + ThreadLocalRandom.current().nextInt(1_000_000))
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

    void declarar(Sesion s, Long eventoId, String metodo, double importe) {
        Map<String, Object> body = new HashMap<>();
        body.put("importe", importe);
        body.put("metodo", metodo);
        body.put("cubreUsuarioIds", new ArrayList<>());
        http.post().uri("/api/v1/eventos/" + eventoId + "/pagos-declarados")
                .header(AUTHORIZATION, "Bearer " + s.token()).body(body)
                .exchange().expectStatus().isOk();
    }

    Long confirmarUltimaDeclaracion(Sesion admin) {
        Long pagoId = pagos.findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado.PENDIENTE).get(0).getId();
        http.post().uri("/api/v1/admin/pagos-declarados/" + pagoId + "/confirmar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();
        return pagoId;
    }

    @Test
    void la_migracion_siembra_el_saldo_inicial_de_san_miguel() {
        var filas = movimientos.findByCuentaIdOrderByFechaAscIdAsc(cuentaSanMiguelId());
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getOrigen()).isEqualTo(OrigenMovimiento.SALDO_INICIAL);
        assertThat(filas.get(0).getImporte()).isEqualByComparingTo(new BigDecimal("91.13"));
        assertThat(movimientos.sumImporte(cuentaSanMiguelId())).isEqualByComparingTo(new BigDecimal("91.13"));
    }

    @Test
    void detalle_recien_migrado_saldo_9113_y_un_movimiento() {
        String token = crearMiembro(RolMembresia.MIEMBRO).token();
        http.get().uri("/api/v1/cuentas/" + cuentaSanMiguelId())
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(91.13)
                .jsonPath("$.movimientos.length()").isEqualTo(1)
                .jsonPath("$.puedoGestionar").isEqualTo(false)
                .jsonPath("$.cobradoSinIngresar").doesNotExist();
    }

    @Test
    void estimacion_cuenta_a_los_apuntados_sin_pagar() {
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        apuntarConFicha(penista, e.getId());

        http.get().uri("/api/v1/cuentas/" + cuentaSanMiguelId())
                .header(AUTHORIZATION, "Bearer " + penista.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(91.13)
                .jsonPath("$.estimacion").isEqualTo(107.13);
    }

    @Test
    void confirmar_transferencia_sube_el_saldo_y_anade_movimiento() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "TRANSFERENCIA", 26.0);
        confirmarUltimaDeclaracion(admin);

        http.get().uri("/api/v1/cuentas/" + cuentaSanMiguelId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(107.13)
                .jsonPath("$.cobradoSinIngresar").isEqualTo(0.0)
                .jsonPath("$.movimientos.length()").isEqualTo(2);
    }

    @Test
    void confirmar_bizum_sube_el_saldo_y_el_aviso_y_marcar_transferido_solo_apaga_el_aviso() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "BIZUM", 26.0);
        confirmarUltimaDeclaracion(admin);

        http.get().uri("/api/v1/cuentas/" + cuentaSanMiguelId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(107.13)
                .jsonPath("$.cobradoSinIngresar").isEqualTo(16.0);

        http.post().uri("/api/v1/cuentas/" + cuentaSanMiguelId() + "/transferencia-a-pena")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(107.13)
                .jsonPath("$.cobradoSinIngresar").isEqualTo(0.0);
    }

    @Test
    void gasto_con_recibo_aparece_en_el_libro_baja_el_saldo_y_se_puede_borrar() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Long cuentaId = cuentaSanMiguelId();

        var form = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        form.add("tipo", "GASTO");
        form.add("concepto", "Cerveza y Coca-Colas");
        form.add("importe", "24.33");
        form.add("categoria", "CERVEZA_Y_TINTO");
        form.add("recibo", new org.springframework.core.io.ByteArrayResource("%PDF-1.4 test".getBytes()) {
            @Override public String getFilename() {
                return "ticket.pdf";
            }
        });

        http.post().uri("/api/v1/cuentas/" + cuentaId + "/movimientos")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(66.8)
                .jsonPath("$.movimientos[1].concepto").isEqualTo("Cerveza y Coca-Colas")
                .jsonPath("$.movimientos[1].reciboArchivo").isNotEmpty()
                .jsonPath("$.movimientos[1].manual").isEqualTo(true)
                .jsonPath("$.resumenGastos[0].categoria").isEqualTo("Cerveza y tinto");

        Long movId = movimientos.findByCuentaIdOrderByFechaAscIdAsc(cuentaId).stream()
                .filter(m -> m.getOrigen() == com.baniterio.api.identidad.OrigenMovimiento.GASTO)
                .findFirst().orElseThrow().getId();

        http.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/api/v1/cuentas/movimientos/" + movId)
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.saldo").isEqualTo(91.13);
    }

    @Test
    void ropa_cantidad_y_talla_no_tocan_el_saldo_hasta_que_el_admin_confirma() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        Long asisId = apuntarConFicha(penista, e.getId());
        String uri = "/api/v1/cuentas/" + cuentaSanMiguelId() + "/asistencias/" + asisId + "/ropa";

        Map<String, Object> body = new HashMap<>();
        body.put("camisetaCantidad", 2);
        body.put("camisetaTalla", "M chico");
        http.put().uri(uri).header(AUTHORIZATION, "Bearer " + admin.token())
                .body(body).exchange().expectStatus().isOk();

        http.get().uri("/api/v1/cuentas/" + cuentaSanMiguelId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(91.13)
                .jsonPath("$.penistas[0].camisetaCantidad").isEqualTo(2)
                .jsonPath("$.penistas[0].camisetaConfirmada").isEqualTo(false);

        // Confirmar sin precio en el evento: 409.
        http.put().uri(uri).header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("camisetaConfirmada", true))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PRECIO_ROPA");

        eventos.findById(e.getId()).ifPresent(ev -> {
            ev.setPrecioCamiseta(new BigDecimal("10.00"));
            eventos.save(ev);
        });

        http.put().uri(uri).header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("camisetaConfirmada", true))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(111.13)
                .jsonPath("$.penistas[0].camisetaConfirmada").isEqualTo(true);

        // Desmarcar: se quita del saldo.
        http.put().uri(uri).header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("camisetaConfirmada", false))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.saldo").isEqualTo(91.13);
    }

    @Test
    void cerrar_el_anio_arrastra_el_saldo_al_siguiente() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "TRANSFERENCIA", 16.0);
        confirmarUltimaDeclaracion(admin);
        // Año 2026: 91,13 + cuota 16 = 107,13.

        http.post().uri("/api/v1/cuentas/" + cuentaSanMiguelId() + "/cerrar-anio")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.anio").isEqualTo(2027)
                .jsonPath("$.esAnioActual").isEqualTo(true)
                .jsonPath("$.anios.length()").isEqualTo(2)
                .jsonPath("$.anios[0]").isEqualTo(2027)
                .jsonPath("$.anios[1]").isEqualTo(2026)
                .jsonPath("$.saldo").isEqualTo(107.13)
                .jsonPath("$.movimientos.length()").isEqualTo(1)
                .jsonPath("$.movimientos[0].concepto").isEqualTo("Saldo del año 2026");

        // Cualquier usuario puede consultar el año cerrado.
        String miembro = crearMiembro(RolMembresia.MIEMBRO).token();
        http.get().uri("/api/v1/cuentas/" + cuentaSanMiguelId() + "?anio=2026")
                .header(AUTHORIZATION, "Bearer " + miembro)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.anio").isEqualTo(2026)
                .jsonPath("$.esAnioActual").isEqualTo(false)
                .jsonPath("$.saldo").isEqualTo(107.13);
    }

    @Test
    void no_admin_no_puede_cerrar_el_anio_403() {
        String token = crearMiembro(RolMembresia.MIEMBRO).token();
        http.post().uri("/api/v1/cuentas/" + cuentaSanMiguelId() + "/cerrar-anio")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO");
    }

    @Test
    void no_admin_no_puede_marcar_transferido_403() {
        String token = crearMiembro(RolMembresia.MIEMBRO).token();
        http.post().uri("/api/v1/cuentas/" + cuentaSanMiguelId() + "/transferencia-a-pena")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO");
    }
}
