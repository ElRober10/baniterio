package com.baniterio.api.perfil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PlataformaDispositivo;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.TelefonoAutorizado;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.baniterio.api.push.ServicioPush;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Test de integración de la reconciliación de hijos del editor de perfil
 * ({@link HijosReconciliador}): altas/bajas/ediciones vía {@code PUT /perfil},
 * alta y poda de teléfonos en {@code telefono_autorizado}, hijos compartidos con
 * la pareja al aceptar el vínculo y su vuelta al creador al romperlo, y el
 * enlace de un hijo que se registra (con aviso push a los padres si es menor).
 *
 * <p>{@link ServicioPush} va mockeado; el aviso del caso del menor se comprueba
 * con Awaitility (el manejador corre {@code AFTER_COMMIT}).
 */
class HijosIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    DispositivoRepository dispositivos;

    @Autowired
    TelefonoAutorizadoRepository telefonosAutorizados;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    ServicioPush servicioPush;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        when(servicioPush.enviar(any(), any(), any())).thenReturn(List.of());
    }

    // --- Helpers ---

    private record Miembro(Long id, String telefono, String nombre, String token, String pushToken) {
    }

    private String telefonoLibre() {
        String numero;
        do {
            numero = (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                    + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (usuarios.existsByTelefono(numero) || telefonosAutorizados.findByTelefono(numero).isPresent());
        return numero;
    }

    private Miembro crearMiembro() {
        String telefono = telefonoLibre();
        String nombre = "Nombre" + ThreadLocalRandom.current().nextInt(1_000_000);
        Usuario usuario = usuarios.save(Usuario.builder()
                .telefono(telefono)
                .email(telefono + "@hijos.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre(nombre)
                .apellidos("De Test")
                .esSuperadmin(false)
                .activo(true)
                .build());
        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        membresias.save(Membresia.builder()
                .usuario(usuario).pena(pena).rol(RolMembresia.MIEMBRO).activa(true).build());
        String pushToken = "tok-" + usuario.getId() + "-" + ThreadLocalRandom.current().nextInt(1_000_000);
        dispositivos.save(Dispositivo.builder()
                .usuario(usuario)
                .token(pushToken)
                .plataforma(PlataformaDispositivo.ANDROID)
                .build());
        return new Miembro(usuario.getId(), telefono, nombre, login(telefono), pushToken);
    }

    private String login(String telefono) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", telefono, "password", "secreto1"))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("token");
    }

    private void registrar(String telefono) {
        http.post().uri("/api/v1/auth/registro").body(Map.of(
                        "telefono", telefono,
                        "email", telefono + "@nuevo.test",
                        "password", "secreto1",
                        "nombre", "Recien",
                        "apellidos", "Llegado"))
                .exchange().expectStatus().isCreated();
    }

    private Map<String, Object> putBase() {
        Map<String, Object> r = new HashMap<>();
        r.put("imagenTipo", "AVATAR");
        r.put("imagenRef", "01_chico");
        r.put("tienePareja", false);
        r.put("hijos", List.of());
        return r;
    }

    private Map<String, Object> hijo(Long id, String nombre, boolean mayorDeEdad, String telefono, boolean visible) {
        Map<String, Object> m = new HashMap<>();
        if (id != null) {
            m.put("id", id);
        }
        m.put("nombre", nombre);
        m.put("mayorDeEdad", mayorDeEdad);
        if (telefono != null) {
            m.put("telefono", telefono);
        }
        m.put("visible", visible);
        return m;
    }

    private RestTestClient.ResponseSpec putHijos(String token, List<Map<String, Object>> hijos) {
        Map<String, Object> r = putBase();
        r.put("hijos", hijos);
        return http.put().uri("/api/v1/perfil").header(AUTHORIZATION, "Bearer " + token).body(r).exchange();
    }

    private RestTestClient.ResponseSpec putConPareja(String token, String parejaNombre, String parejaTelefono,
            List<Map<String, Object>> hijos) {
        Map<String, Object> r = putBase();
        r.put("tienePareja", true);
        r.put("parejaNombre", parejaNombre);
        r.put("parejaTelefono", parejaTelefono);
        r.put("hijos", hijos);
        return http.put().uri("/api/v1/perfil").header(AUTHORIZATION, "Bearer " + token).body(r).exchange();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPerfil(String token) {
        return http.get().uri("/api/v1/perfil").header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> hijosDe(String token) {
        return (List<Map<String, Object>>) getPerfil(token).get("hijos");
    }

    private Map<String, Object> buscarHijo(String token, String nombre) {
        return hijosDe(token).stream()
                .filter(h -> nombre.equals(h.get("nombre")))
                .findFirst().orElseThrow();
    }

    private Long idDelHijo(String token, String nombre) {
        return ((Number) buscarHijo(token, nombre).get("id")).longValue();
    }

    private void aceptarPareja(String token) {
        http.post().uri("/api/v1/perfil/pareja/aceptar").header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isNoContent();
    }

    // --- Casos ---

    @Test
    void anadir_hijo_sin_telefono_aparece_en_mi_perfil() {
        Miembro a = crearMiembro();

        putHijos(a.token(), List.of(hijo(null, "Lucía", false, null, true))).expectStatus().isOk();

        Map<String, Object> h = buscarHijo(a.token(), "Lucía");
        assertThat(h.get("visible")).isEqualTo(true);
        assertThat(h.get("telefono")).isNull();
        assertThat(h.get("registrado")).isEqualTo(false);
    }

    @Test
    void anadir_hijo_con_telefono_lo_da_de_alta_en_autorizados() {
        Miembro a = crearMiembro();
        String tel = telefonoLibre();

        putHijos(a.token(), List.of(hijo(null, "Mateo", false, tel, false))).expectStatus().isOk();

        TelefonoAutorizado ta = telefonosAutorizados.findByTelefono(tel).orElseThrow();
        assertThat(ta.isUsado()).isFalse();
        assertThat(ta.getAutorizadoPor().getId()).isEqualTo(a.id());
    }

    @Test
    void anadir_hijo_con_telefono_invalido_da_400() {
        Miembro a = crearMiembro();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = putHijos(a.token(), List.of(hijo(null, "Nil", false, "12345", false)))
                .expectStatus().isBadRequest()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body.get("codigo")).isEqualTo("TELEFONO_HIJO_INVALIDO");
    }

    @Test
    void quitar_hijo_con_telefono_no_usado_quita_el_autorizado() {
        Miembro a = crearMiembro();
        String tel = telefonoLibre();
        putHijos(a.token(), List.of(hijo(null, "Sara", false, tel, false))).expectStatus().isOk();
        assertThat(telefonosAutorizados.findByTelefono(tel)).isPresent();

        putHijos(a.token(), List.of()).expectStatus().isOk();

        assertThat(telefonosAutorizados.findByTelefono(tel)).isEmpty();
        assertThat(hijosDe(a.token())).isEmpty();
    }

    @Test
    void quitar_hijo_con_telefono_ya_usado_no_toca_el_autorizado() {
        Miembro a = crearMiembro();
        String tel = telefonoLibre();
        putHijos(a.token(), List.of(hijo(null, "Bruno", false, tel, false))).expectStatus().isOk();

        TelefonoAutorizado ta = telefonosAutorizados.findByTelefono(tel).orElseThrow();
        ta.setUsado(true);
        telefonosAutorizados.save(ta);

        putHijos(a.token(), List.of()).expectStatus().isOk();

        assertThat(telefonosAutorizados.findByTelefono(tel)).get()
                .satisfies(fila -> assertThat(fila.isUsado()).isTrue());
    }

    @Test
    void editar_hijo_cambia_nombre_y_visible() {
        Miembro a = crearMiembro();
        putHijos(a.token(), List.of(hijo(null, "Ona", false, null, false))).expectStatus().isOk();
        Long id = idDelHijo(a.token(), "Ona");

        putHijos(a.token(), List.of(hijo(id, "Onalicia", true, null, true))).expectStatus().isOk();

        List<Map<String, Object>> hijos = hijosDe(a.token());
        assertThat(hijos).hasSize(1);
        Map<String, Object> h = hijos.get(0);
        assertThat(h.get("nombre")).isEqualTo("Onalicia");
        assertThat(h.get("mayorDeEdad")).isEqualTo(true);
        assertThat(h.get("visible")).isEqualTo(true);
    }

    @Test
    void hijo_compartido_con_la_pareja_tras_aceptar() {
        Miembro a = crearMiembro();
        Miembro b = crearMiembro();
        putConPareja(a.token(), "B", b.telefono(), List.of()).expectStatus().isOk();
        aceptarPareja(b.token());

        putConPareja(a.token(), "B", b.telefono(), List.of(hijo(null, "Emma", false, null, true)))
                .expectStatus().isOk();

        // B ve el hijo de A y puede editarlo.
        Long id = idDelHijo(b.token(), "Emma");
        putConPareja(b.token(), a.nombre(), a.telefono(), List.of(hijo(id, "Emma María", false, null, true)))
                .expectStatus().isOk();

        assertThat(buscarHijo(a.token(), "Emma María")).isNotNull();
    }

    @Test
    void al_romper_el_vinculo_el_hijo_vuelve_al_creador() {
        Miembro a = crearMiembro();
        Miembro b = crearMiembro();
        putConPareja(a.token(), "B", b.telefono(), List.of()).expectStatus().isOk();
        aceptarPareja(b.token());
        putConPareja(a.token(), "B", b.telefono(), List.of(hijo(null, "Pau", false, null, true)))
                .expectStatus().isOk();
        assertThat(hijosDe(b.token())).extracting(h -> h.get("nombre")).contains("Pau");

        http.delete().uri("/api/v1/perfil/pareja").header(AUTHORIZATION, "Bearer " + b.token())
                .exchange().expectStatus().isNoContent();

        assertThat(hijosDe(a.token())).extracting(h -> h.get("nombre")).contains("Pau");
        assertThat(hijosDe(b.token())).isEmpty();
    }

    @Test
    void hijo_con_telefono_que_se_registra_se_enlaza_y_sale_de_la_lista_del_padre() {
        Miembro a = crearMiembro();
        String tel = telefonoLibre();
        putHijos(a.token(), List.of(hijo(null, "Iván", true, tel, true))).expectStatus().isOk();

        registrar(tel);
        String tokenHijo = login(tel);

        // Primer GET /perfil del hijo: se enlaza.
        getPerfil(tokenHijo);

        assertThat(hijosDe(a.token())).isEmpty();
    }

    @Test
    void hijo_menor_que_se_registra_notifica_a_los_padres() {
        Miembro a = crearMiembro();
        String tel = telefonoLibre();
        putHijos(a.token(), List.of(hijo(null, "Menor", false, tel, true))).expectStatus().isOk();

        registrar(tel);
        getPerfil(login(tel));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
        await().atMost(ofSeconds(10)).untilAsserted(() ->
                verify(servicioPush).enviar(tokens.capture(),
                        eq("Tu hijo se ha registrado"),
                        eq("Menor ya tiene cuenta en la peña")));
        assertThat(tokens.getValue()).contains(a.pushToken());
    }
}
