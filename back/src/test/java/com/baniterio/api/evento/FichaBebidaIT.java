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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/** IT de la ficha de bebida y el cálculo de la cuota (pieza 3b). */
class FichaBebidaIT extends IntegrationTest {

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
                .filter(e -> e.getNombre().startsWith("IT-ficha")).toList());
        bebidas.deleteAll(bebidas.findAll().stream()
                .filter(b -> b.getNombre().startsWith("Ron del abuelo") || b.getNombre().startsWith("IT-"))
                .toList());
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
                .telefono(tel).email(tel + "@ficha.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Ficha").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    /**
     * Evento de San Miguel con las 5 cuotas derivadas de {@code m} (cubatas) con
     * {@link CalculadoraCuota#derivar}, la misma fórmula que usa el backend al
     * crear/editar. {@code m} null → las 5 cuotas quedan sin poner.
     */
    private Evento sanMiguel(BigDecimal m) {
        CalculadoraCuota.Cuotas cuotas = CalculadoraCuota.derivar(m);
        return eventos.save(Evento.builder().pena(pena()).cuenta(cuenta("San Miguel"))
                .nombre("IT-ficha-" + ThreadLocalRandom.current().nextInt(1_000_000))
                .fecha(LocalDate.now().plusDays(20)).fechaFin(LocalDate.now().plusDays(21))
                .cuotaCubatas(cuotas.cubatas())
                .cuotaCervezas(cuotas.cervezas())
                .cuotaCubatas1Dia(cuotas.cubatas1Dia())
                .cuotaCervezas1Dia(cuotas.cervezas1Dia())
                .cuotaEmbarazada(cuotas.embarazada())
                .build());
    }

    private Long refrescoId(String nombre) {
        return bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.REFRESCO, nombre).orElseThrow().getId();
    }

    private Long alcoholId(String nombre) {
        return bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.ALCOHOL, nombre).orElseThrow().getId();
    }

    /** Cuerpo base de la ficha: apuntado, solo refresco, los dos días. */
    private Map<String, Object> fichaBase() {
        Map<String, Object> m = new HashMap<>();
        m.put("estado", "APUNTADO");
        m.put("refrescoBebidaId", refrescoId("Coca-Cola"));
        m.put("alternativa", "NADA");
        m.put("asisteDia1", true);
        m.put("asisteDia2", true);
        return m;
    }

    private RestTestClient.ResponseSpec putFicha(Sesion s, Long eventoId, Map<String, Object> ficha) {
        return http.put().uri("/api/v1/eventos/" + eventoId + "/ficha-bebida")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(ficha).exchange();
    }

    @Test
    void guardar_ficha_calcula_y_devuelve_la_cuota() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));

        // fichaBase() no lleva alcohol, así que paga la cuota de "solo cerveza".
        putFicha(s, e.getId(), fichaBase()).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.modalidad").isEqualTo("SOLO_CERVEZA")
                .jsonPath("$.cuota").isEqualTo(16.00)
                .jsonPath("$.cuotaPendiente").isEqualTo(false);

        var a = asistencias.findByEventoIdAndUsuarioId(e.getId(), s.id()).orElseThrow();
        assertThat(fichas.findByAsistenciaId(a.getId())).isPresent();
    }

    @Test
    void ficha_en_evento_que_no_es_san_miguel_409() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(cuenta("Chuletas Santas"))
                .nombre("IT-ficha-chuletas").fecha(LocalDate.now().plusDays(20)).build());

        putFicha(s, e.getId(), fichaBase()).expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("EVENTO_SIN_FICHA");
    }

    @Test
    void ficha_en_evento_pasado_409() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(cuenta("San Miguel"))
                .nombre("IT-ficha-pasado").fecha(LocalDate.now().minusDays(1))
                .fechaFin(LocalDate.now()).cuotaCubatas(new BigDecimal("26")).build());

        putFicha(s, e.getId(), fichaBase()).expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("EVENTO_YA_PASADO");
    }

    @Test
    void estado_NO_VOY_en_la_ficha_es_400() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Map<String, Object> ficha = fichaBase();
        ficha.put("estado", "NO_VOY");

        putFicha(s, e.getId(), ficha).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("VALIDACION");
    }

    @Test
    void alcohol_otra_crea_bebida_pendiente_y_no_sale_en_catalogo() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Map<String, Object> ficha = fichaBase();
        ficha.put("alcoholOtra", "Ron del abuelo");

        putFicha(s, e.getId(), ficha).expectStatus().isOk();

        assertThat(bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.ALCOHOL, "Ron del abuelo")).isPresent();
        http.get().uri("/api/v1/bebidas/catalogo")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.alcohol[?(@.nombre == 'Ron del abuelo')].id").doesNotExist();
    }

    @Test
    void un_dia_bebiendo_alcohol_calcula_M_partido_2_mas_1() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Map<String, Object> ficha = fichaBase();
        ficha.put("alcoholBebidaId", alcoholId("Barceló"));
        ficha.put("asisteDia2", false);

        putFicha(s, e.getId(), ficha).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.modalidad").isEqualTo("UN_DIA")
                .jsonPath("$.cuota").isEqualTo(14.00);
    }

    @Test
    void un_dia_sin_alcohol_calcula_cervezas_partido_2_mas_1() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Map<String, Object> ficha = fichaBase();
        ficha.put("asisteDia2", false);

        putFicha(s, e.getId(), ficha).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.modalidad").isEqualTo("UN_DIA")
                .jsonPath("$.cuota").isEqualTo(9.00);
    }

    @Test
    void embarazada_ignora_alcohol_y_paga_5() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Map<String, Object> ficha = fichaBase();
        ficha.put("embarazada", true);
        // aunque el cliente mande alcohol, con embarazada el backend lo ignora:
        ficha.put("alcoholBebidaId", alcoholId("Barceló"));

        putFicha(s, e.getId(), ficha).expectStatus().isOk()
                .expectBody().jsonPath("$.cuota").isEqualTo(5.00);

        var a = asistencias.findByEventoIdAndUsuarioId(e.getId(), s.id()).orElseThrow();
        assertThat(fichas.findByAsistenciaId(a.getId()).orElseThrow().getAlcohol()).isNull();
    }

    @Test
    void sin_esa_cuota_puesta_guarda_la_ficha_con_cuota_null() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(null);

        putFicha(s, e.getId(), fichaBase()).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cuota").doesNotExist()
                .jsonPath("$.cuotaPendiente").isEqualTo(true);
    }

    @Test
    void detalle_del_evento_trae_mi_ficha_y_los_dias() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Map<String, Object> ficha = fichaBase();
        ficha.put("asisteDia2", false);
        putFicha(s, e.getId(), ficha).expectStatus().isOk();

        http.get().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.asistencia.ficha.llevaFicha").isEqualTo(true)
                .jsonPath("$.asistencia.ficha.diasEvento.length()").isEqualTo(2)
                .jsonPath("$.asistencia.ficha.miFicha.modalidad").isEqualTo("UN_DIA")
                .jsonPath("$.asistencia.ficha.miFicha.cuota").isEqualTo(9.00);
    }

    @Test
    void detalle_de_evento_normal_ficha_llevaFicha_false() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(cuenta("Chuletas Santas"))
                .nombre("IT-ficha-normal").fecha(LocalDate.now().plusDays(20)).build());

        http.get().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.asistencia.ficha.llevaFicha").isEqualTo(false)
                .jsonPath("$.asistencia.ficha.miFicha").doesNotExist();
    }

    @Test
    void anadir_a_mano_con_ficha_calcula_la_cuota() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sanMiguel(new BigDecimal("26"));
        Map<String, Object> ficha = fichaBase();

        http.post().uri("/api/v1/eventos/" + e.getId() + "/asistencias")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "Primo de Juan", "estado", "APUNTADO", "ficha", ficha))
                .exchange().expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.cuota").isEqualTo(16.00)
                .jsonPath("$.modalidad").isEqualTo("SOLO_CERVEZA");
    }

    @Test
    void cambiar_una_cuota_recalcula_las_fichas_que_le_tocan() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sanMiguel(new BigDecimal("26"));
        putFicha(admin, e.getId(), fichaBase()).expectStatus().isOk()
                .expectBody().jsonPath("$.cuota").isEqualTo(16.00);

        http.put().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", e.getNombre(), "fecha", e.getFecha().toString(),
                        "fechaFin", e.getFechaFin().toString(),
                        "cuentaId", cuenta("San Miguel").getId(), "cuotaCubatas", 30))
                .exchange().expectStatus().isOk();

        var a = asistencias.findByEventoIdAndUsuarioId(e.getId(), admin.id()).orElseThrow();
        assertThat(fichas.findByAsistenciaId(a.getId()).orElseThrow().getCuota())
                .isEqualByComparingTo("20.00");
    }
}
