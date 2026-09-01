package com.baniterio.api.perfil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.EstadoVinculo;
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
import com.baniterio.api.identidad.VinculoParejaRepository;
import com.baniterio.api.push.ServicioPush;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Test de integración de la máquina de estados del vínculo de pareja: declarar
 * (miembro → PENDIENTE + push; libre → SIN_CUENTA + alta de teléfono), aceptar,
 * rechazar, romper, el 409 de "ese teléfono ya tiene pareja", la detección
 * SIN_CUENTA→PENDIENTE al registrarse (incl. el caso de dos declaraciones del
 * mismo teléfono), que el lado que acepta puede guardar su perfil sin tocar el
 * vínculo, y que un usuario no puede tener dos parejas vivas.
 *
 * <p>{@link ServicioPush} va mockeado; los avisos se comprueban con Awaitility
 * (el manejador corre {@code AFTER_COMMIT}) y capturando los tokens para
 * verificar que el push va a la persona correcta y no a la otra.
 */
class VinculoParejaIT extends IntegrationTest {

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
    VinculoParejaRepository vinculos;

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

    /** Crea un miembro activo con un dispositivo registrado (para poder espiar sus push) y su JWT. */
    private Miembro crearMiembro() {
        String telefono = telefonoLibre();
        String nombre = "Nombre" + ThreadLocalRandom.current().nextInt(1_000_000);
        Usuario usuario = usuarios.save(Usuario.builder()
                .telefono(telefono)
                .email(telefono + "@vinculo.test")
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

    private Map<String, Object> putBase() {
        Map<String, Object> r = new HashMap<>();
        r.put("imagenTipo", "AVATAR");
        r.put("imagenRef", "01_chico");
        r.put("tienePareja", false);
        r.put("hijos", List.of());
        return r;
    }

    private RestTestClient.ResponseSpec declarar(String token, String parejaNombre, String parejaTelefono) {
        Map<String, Object> r = putBase();
        r.put("tienePareja", true);
        r.put("parejaNombre", parejaNombre);
        r.put("parejaTelefono", parejaTelefono);
        return http.put().uri("/api/v1/perfil").header(AUTHORIZATION, "Bearer " + token).body(r).exchange();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPerfil(String token) {
        return http.get().uri("/api/v1/perfil").header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pareja(String token) {
        return (Map<String, Object>) getPerfil(token).get("pareja");
    }

    private void aceptar(String token) {
        http.post().uri("/api/v1/perfil/pareja/aceptar").header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isNoContent();
    }

    /**
     * Espera a que salga el push con ese cuerpo y comprueba que va a
     * {@code tokenDestino} y NO a {@code tokenOtro} (la audiencia ES el meollo de
     * la máquina de estados).
     */
    private void esperarPush(String cuerpo, String tokenDestino, String tokenOtro) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
        await().atMost(ofSeconds(10)).untilAsserted(() ->
                verify(servicioPush).enviar(tokens.capture(), eq("Vínculo de pareja"), eq(cuerpo)));
        assertThat(tokens.getValue()).contains(tokenDestino).doesNotContain(tokenOtro);
    }

    // --- Casos ---

    @Test
    void declarar_pareja_con_telefono_de_miembro_deja_pendiente_y_notifica() {
        Miembro a = crearMiembro();
        Miembro b = crearMiembro();

        declarar(a.token(), "Mi pareja B", b.telefono()).expectStatus().isOk();

        assertThat(pareja(a.token()).get("estado")).isEqualTo("PENDIENTE");
        esperarPush(a.nombre() + " dice que sois pareja", b.pushToken(), a.pushToken());
    }

    @Test
    void declarar_pareja_con_telefono_libre_deja_sin_cuenta_y_autoriza_el_telefono() {
        Miembro a = crearMiembro();
        String libre = telefonoLibre();

        declarar(a.token(), "Pareja sin cuenta", libre).expectStatus().isOk();

        assertThat(pareja(a.token()).get("estado")).isEqualTo("SIN_CUENTA");
        TelefonoAutorizado ta = telefonosAutorizados.findByTelefono(libre).orElseThrow();
        assertThat(ta.isUsado()).isFalse();
        assertThat(ta.getAutorizadoPor().getId()).isEqualTo(a.id());
    }

    @Test
    void declarar_pareja_sin_nombre_da_400_nombre_requerido() {
        Miembro a = crearMiembro();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = declarar(a.token(), "  ", telefonoLibre())
                .expectStatus().isBadRequest()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body.get("codigo")).isEqualTo("NOMBRE_PAREJA_REQUERIDO");
    }

    @Test
    void la_pareja_acepta_y_el_vinculo_es_mutuo() {
        Miembro a = crearMiembro();
        Miembro b = crearMiembro();
        declarar(a.token(), "B", b.telefono()).expectStatus().isOk();

        aceptar(b.token());

        assertThat(pareja(a.token()).get("estado")).isEqualTo("ACEPTADO");
        Map<String, Object> vistaB = pareja(b.token());
        assertThat(vistaB.get("estado")).isEqualTo("ACEPTADO");
        assertThat(vistaB.get("nombre")).isEqualTo(a.nombre());
        assertThat(vistaB.get("telefono")).isEqualTo(a.telefono());
        esperarPush(b.nombre() + " ha aceptado el vínculo de pareja", a.pushToken(), b.pushToken());
    }

    @Test
    void la_pareja_rechaza_y_desaparece_de_a() {
        Miembro a = crearMiembro();
        Miembro b = crearMiembro();
        declarar(a.token(), "B", b.telefono()).expectStatus().isOk();

        http.post().uri("/api/v1/perfil/pareja/rechazar").header(AUTHORIZATION, "Bearer " + b.token())
                .exchange().expectStatus().isNoContent();

        assertThat(getPerfil(a.token()).get("pareja")).isNull();
        esperarPush(b.nombre() + " ha rechazado el vínculo de pareja", a.pushToken(), b.pushToken());
    }

    @Test
    void declarar_telefono_ya_emparejado_da_409() {
        Miembro a = crearMiembro();
        Miembro b = crearMiembro();
        declarar(a.token(), "B", b.telefono()).expectStatus().isOk();
        aceptar(b.token());

        Miembro c = crearMiembro();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = declarar(c.token(), "B otra vez", b.telefono())
                .expectStatus().isEqualTo(409)
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body.get("codigo")).isEqualTo("TELEFONO_YA_EMPAREJADO");
    }

    @Test
    void sin_cuenta_pasa_a_pendiente_cuando_esa_persona_se_registra() {
        Miembro a = crearMiembro();
        String libre = telefonoLibre();
        declarar(a.token(), "Futura pareja", libre).expectStatus().isOk();

        registrar(libre);

        Map<String, Object> perfilNuevo = getPerfil(login(libre));
        @SuppressWarnings("unchecked")
        Map<String, Object> pendiente = (Map<String, Object>) perfilNuevo.get("vinculoPendiente");
        assertThat(pendiente).isNotNull();
        assertThat(pendiente.get("solicitanteNombre")).isEqualTo(a.nombre());
    }

    @Test
    void dos_miembros_declaran_el_mismo_telefono_libre_y_al_registrarse_no_revienta_get_perfil() {
        Miembro a1 = crearMiembro();
        Miembro a2 = crearMiembro();
        String libre = telefonoLibre();
        declarar(a1.token(), "Pareja de A1", libre).expectStatus().isOk();
        declarar(a2.token(), "Pareja de A2", libre).expectStatus().isOk();

        registrar(libre);
        String tokenB = login(libre);
        Long idB = usuarios.findByTelefono(libre).orElseThrow().getId();

        // GET /perfil no da 500 pese a los dos SIN_CUENTA con el mismo teléfono...
        Map<String, Object> perfilB = getPerfil(tokenB);
        assertThat(perfilB.get("vinculoPendiente")).isNotNull();
        // ...y es idempotente en llamadas sucesivas.
        assertThat(getPerfil(tokenB).get("vinculoPendiente")).isNotNull();

        // Exactamente un vínculo vivo apunta a B; el otro sigue SIN_CUENTA.
        long comoPareja = vinculos.findAll().stream()
                .filter(v -> v.getParejaUsuario() != null && v.getParejaUsuario().getId().equals(idB))
                .filter(v -> v.getEstado() != EstadoVinculo.RECHAZADO)
                .count();
        assertThat(comoPareja).isEqualTo(1);
    }

    @Test
    void el_lado_que_acepta_puede_guardar_su_perfil_sin_romper_el_vinculo() {
        Miembro a = crearMiembro();
        Miembro b = crearMiembro();
        declarar(a.token(), "B", b.telefono()).expectStatus().isOk();
        aceptar(b.token());

        // B reenvía en su PUT /perfil los campos de pareja que le devolvió el GET.
        Map<String, Object> vistaB = pareja(b.token());
        Map<String, Object> req = putBase();
        req.put("tienePareja", true);
        req.put("parejaNombre", vistaB.get("nombre"));
        req.put("parejaTelefono", vistaB.get("telefono"));
        req.put("sobreMi", "me actualizo");
        http.put().uri("/api/v1/perfil").header(AUTHORIZATION, "Bearer " + b.token())
                .body(req).exchange().expectStatus().isOk();

        // El vínculo sigue ACEPTADO en los dos lados y no se ha duplicado.
        assertThat(pareja(a.token()).get("estado")).isEqualTo("ACEPTADO");
        assertThat(pareja(b.token()).get("estado")).isEqualTo("ACEPTADO");
        long vivos = vinculos.findAll().stream()
                .filter(v -> v.getSolicitante().getId().equals(a.id()))
                .filter(v -> v.getEstado() != EstadoVinculo.RECHAZADO)
                .count();
        assertThat(vivos).isEqualTo(1);
        assertThat(getPerfil(b.token()).get("sobreMi")).isEqualTo("me actualizo");
    }

    @Test
    void romper_vinculo_aceptado_desde_cualquiera_de_los_dos() {
        Miembro a = crearMiembro();
        Miembro b = crearMiembro();
        declarar(a.token(), "B", b.telefono()).expectStatus().isOk();
        aceptar(b.token());

        http.delete().uri("/api/v1/perfil/pareja").header(AUTHORIZATION, "Bearer " + b.token())
                .exchange().expectStatus().isNoContent();

        assertThat(getPerfil(a.token()).get("pareja")).isNull();
        assertThat(getPerfil(b.token()).get("pareja")).isNull();
        esperarPush(b.nombre() + " ha deshecho el vínculo de pareja", a.pushToken(), b.pushToken());
    }

    @Test
    void un_usuario_no_puede_declarar_dos_parejas() {
        Miembro a = crearMiembro();
        Miembro b = crearMiembro();
        declarar(a.token(), "B", b.telefono()).expectStatus().isOk();

        String otro = telefonoLibre();
        declarar(a.token(), "Otra persona", otro).expectStatus().isOk();

        assertThat(pareja(a.token()).get("telefono")).isEqualTo(otro);
        assertThat(pareja(a.token()).get("estado")).isEqualTo("SIN_CUENTA");
        long vivos = vinculos.findAll().stream()
                .filter(v -> v.getSolicitante().getId().equals(a.id()))
                .filter(v -> v.getEstado() != EstadoVinculo.RECHAZADO)
                .count();
        assertThat(vivos).isEqualTo(1);
        // B queda libre otra vez.
        assertThat(getPerfil(b.token()).get("vinculoPendiente")).isNull();
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
}
