package com.baniterio.api.evento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.BebidaRepository;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoVinculo;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.Hijo;
import com.baniterio.api.identidad.HijoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
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

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/** IT del listado de asistentes + el bloque {@code puedoPagarPor} (pieza 4). */
class ListadoAsistentesIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired EventoRepository eventos;
    @Autowired CuentaRepository cuentas;
    @Autowired AsistenciaEventoRepository asistencias;
    @Autowired FichaBebidaRepository fichas;
    @Autowired BebidaRepository bebidas;
    @Autowired VinculoParejaRepository vinculos;
    @Autowired HijoRepository hijos;
    @Autowired PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void limpiar() {
        hijos.deleteAllInBatch();
        vinculos.deleteAllInBatch();
        fichas.deleteAllInBatch();
        asistencias.deleteAllInBatch();
        eventos.deleteAll(eventos.findAll().stream()
                .filter(e -> e.getNombre().startsWith("IT-lista")).toList());
    }

    private Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    private Cuenta cuenta(String nombre) {
        return cuentas.findByPenaIdAndNombre(pena().getId(), nombre).orElseThrow();
    }

    private record Sesion(Long id, String token) {
    }

    private Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@lista.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Lista").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    private Evento sanMiguel(BigDecimal cubatas) {
        CalculadoraCuota.Cuotas c = CalculadoraCuota.derivar(cubatas);
        return eventos.save(Evento.builder().pena(pena()).cuenta(cuenta("San Miguel"))
                .nombre("IT-lista-" + ThreadLocalRandom.current().nextInt(1_000_000))
                .fecha(LocalDate.now().plusDays(20)).fechaFin(LocalDate.now().plusDays(21))
                .cuotaCubatas(c.cubatas()).cuotaCervezas(c.cervezas())
                .cuotaCubatas1Dia(c.cubatas1Dia()).cuotaCervezas1Dia(c.cervezas1Dia())
                .cuotaEmbarazada(c.embarazada()).build());
    }

    private Long refrescoId(String nombre) {
        return bebidas.findByTipoAndNombreIgnoreCase(
                com.baniterio.api.identidad.TipoBebida.REFRESCO, nombre).orElseThrow().getId();
    }

    private Map<String, Object> fichaBase() {
        Map<String, Object> m = new HashMap<>();
        m.put("estado", "APUNTADO");
        m.put("refrescoBebidaId", refrescoId("Coca-Cola"));
        m.put("alternativa", "NADA");
        m.put("asisteDia1", true);
        m.put("asisteDia2", true);
        return m;
    }

    private void putFicha(Sesion s, Long eventoId, Map<String, Object> ficha) {
        http.put().uri("/api/v1/eventos/" + eventoId + "/ficha-bebida")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(ficha).exchange().expectStatus().isOk();
    }

    private RestTestClient.BodyContentSpec getListado(Sesion s, Long eventoId) {
        return http.get().uri("/api/v1/eventos/" + eventoId + "/asistentes")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isOk().expectBody();
    }

    @Test
    void lista_apuntados_y_enduda_con_su_cuota_no_los_no_voy() {
        Sesion a = crearMiembro(RolMembresia.MIEMBRO);
        Sesion b = crearMiembro(RolMembresia.MIEMBRO);
        Sesion c = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));

        putFicha(a, e.getId(), fichaBase());                       // SOLO_CERVEZA → 16
        Map<String, Object> fichaB = fichaBase();
        fichaB.put("estado", "EN_DUDA");
        putFicha(b, e.getId(), fichaB);
        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencia")
                .header(AUTHORIZATION, "Bearer " + c.token())
                .body(Map.of("estado", "NO_VOY")).exchange().expectStatus().isOk();

        getListado(a, e.getId())
                .jsonPath("$.asistentes.length()").isEqualTo(2)
                .jsonPath("$.asistentes[?(@.estado == 'NO_VOY')]").doesNotExist()
                .jsonPath("$.totalCuotas").isEqualTo(32.00)
                .jsonPath("$.totalPagado").isEqualTo(0)
                .jsonPath("$.miCuota").isEqualTo(16.00);
    }

    @Test
    void evento_que_no_es_san_miguel_409() {
        Sesion a = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(cuenta("Chuletas Santas"))
                .nombre("IT-lista-chuletas").fecha(LocalDate.now().plusDays(20)).build());

        http.get().uri("/api/v1/eventos/" + e.getId() + "/asistentes")
                .header(AUTHORIZATION, "Bearer " + a.token())
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("EVENTO_SIN_FICHA");
    }

    @Test
    void asistente_apuntado_sin_ficha_sale_con_bebida_y_cuota_null() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sanMiguel(new BigDecimal("26"));

        http.post().uri("/api/v1/eventos/" + e.getId() + "/asistencias")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "Sin ficha", "estado", "APUNTADO"))
                .exchange().expectStatus().isCreated();

        getListado(admin, e.getId())
                .jsonPath("$.asistentes[0].nombre").isEqualTo("Sin ficha")
                .jsonPath("$.asistentes[0].cuota").doesNotExist()
                .jsonPath("$.asistentes[0].bebida").doesNotExist()
                .jsonPath("$.asistentes[0].esManual").isEqualTo(true);
    }

    @Test
    void puedoPagarPor_incluye_pareja_apuntada_con_cuota_y_no_a_uno_mismo() {
        Sesion yo = crearMiembro(RolMembresia.MIEMBRO);
        Sesion pareja = crearMiembro(RolMembresia.MIEMBRO);
        vinculos.save(VinculoPareja.builder()
                .solicitante(usuarios.findById(yo.id()).orElseThrow())
                .parejaUsuario(usuarios.findById(pareja.id()).orElseThrow())
                .parejaNombre("La pareja").parejaTelefono("600000000")
                .estado(EstadoVinculo.ACEPTADO).build());
        Evento e = sanMiguel(new BigDecimal("26"));
        putFicha(yo, e.getId(), fichaBase());
        putFicha(pareja, e.getId(), fichaBase());

        getListado(yo, e.getId())
                .jsonPath("$.puedoPagarPor.length()").isEqualTo(1)
                .jsonPath("$.puedoPagarPor[0].relacion").isEqualTo("PAREJA")
                .jsonPath("$.puedoPagarPor[0].cuota").isEqualTo(16.00)
                .jsonPath("$.puedoPagarPor[0].usuarioId").isEqualTo(pareja.id());
    }

    @Test
    void puedoPagarPor_hijo_mayor_con_cuenta_si_pero_hijo_menor_no() {
        Sesion yo = crearMiembro(RolMembresia.MIEMBRO);
        Sesion hijoMayor = crearMiembro(RolMembresia.MIEMBRO);
        Sesion hijoMenor = crearMiembro(RolMembresia.MIEMBRO);
        hijos.save(Hijo.builder().creador(usuarios.findById(yo.id()).orElseThrow())
                .nombre("Mayor").mayorDeEdad(true).visible(true)
                .usuario(usuarios.findById(hijoMayor.id()).orElseThrow()).build());
        hijos.save(Hijo.builder().creador(usuarios.findById(yo.id()).orElseThrow())
                .nombre("Menor").mayorDeEdad(false).visible(true)
                .usuario(usuarios.findById(hijoMenor.id()).orElseThrow()).build());
        Evento e = sanMiguel(new BigDecimal("26"));
        putFicha(yo, e.getId(), fichaBase());
        putFicha(hijoMayor, e.getId(), fichaBase());
        putFicha(hijoMenor, e.getId(), fichaBase());

        getListado(yo, e.getId())
                .jsonPath("$.puedoPagarPor.length()").isEqualTo(1)
                .jsonPath("$.puedoPagarPor[0].relacion").isEqualTo("HIJO")
                .jsonPath("$.puedoPagarPor[0].usuarioId").isEqualTo(hijoMayor.id());
    }

    @Test
    void puedoPagarPor_incluye_invitado_que_anadi_yo_y_no_el_de_otro() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion otroAdmin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sanMiguel(new BigDecimal("26"));
        putFicha(admin, e.getId(), fichaBase());

        http.post().uri("/api/v1/eventos/" + e.getId() + "/asistencias")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "Mi primo", "estado", "APUNTADO", "ficha", fichaBase()))
                .exchange().expectStatus().isCreated();
        http.post().uri("/api/v1/eventos/" + e.getId() + "/asistencias")
                .header(AUTHORIZATION, "Bearer " + otroAdmin.token())
                .body(Map.of("nombre", "Primo ajeno", "estado", "APUNTADO", "ficha", fichaBase()))
                .exchange().expectStatus().isCreated();

        getListado(admin, e.getId())
                .jsonPath("$.puedoPagarPor[?(@.relacion == 'INVITADO')].nombre").isEqualTo("Mi primo")
                .jsonPath("$.puedoPagarPor[?(@.nombre == 'Primo ajeno')]").doesNotExist();
    }
}
