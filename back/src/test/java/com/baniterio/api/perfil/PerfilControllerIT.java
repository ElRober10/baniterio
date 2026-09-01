package com.baniterio.api.perfil;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;

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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Test de integración del editor de perfil: {@code GET/PUT /api/v1/perfil},
 * {@code POST /api/v1/perfil/foto} y {@code GET /api/v1/perfil/avatares}, de
 * punta a punta contra la app y el Postgres reales.
 *
 * <p>Como en {@link com.baniterio.api.admin.AdminMiembrosIT}, cada token se
 * fabrica creando el usuario y su membresía por repos y haciendo login por HTTP.
 */
class PerfilControllerIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    PasswordEncoder passwordEncoder;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    // --- Helpers ---

    private record Miembro(Long id, String telefono, String token) {
    }

    private String telefonoLibre() {
        String numero;
        do {
            numero = (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                    + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (usuarios.existsByTelefono(numero));
        return numero;
    }

    private Miembro crearMiembro() {
        String telefono = telefonoLibre();
        Usuario usuario = usuarios.save(Usuario.builder()
                .telefono(telefono)
                .email(telefono + "@perfil.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("Persona")
                .apellidos("De Test " + telefono)
                .esSuperadmin(false)
                .activo(true)
                .build());
        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        membresias.save(Membresia.builder()
                .usuario(usuario)
                .pena(pena)
                .rol(RolMembresia.MIEMBRO)
                .activa(true)
                .build());
        return new Miembro(usuario.getId(), telefono, login(telefono));
    }

    private String login(String telefono) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", telefono, "password", "secreto1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        return (String) body.get("token");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPerfil(String token) {
        return http.get().uri("/api/v1/perfil")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
    }

    /** Cuerpo base de un PUT /perfil válido; el test ajusta lo que necesite. */
    private Map<String, Object> putBase() {
        Map<String, Object> req = new HashMap<>();
        req.put("nombre", "Ana");
        req.put("apellidos", "García");
        req.put("mote", "Mote");
        req.put("sobreMi", "hola");
        req.put("imagenTipo", "AVATAR");
        req.put("imagenRef", "01_chico");
        req.put("tienePareja", false);
        req.put("hijos", List.of());
        return req;
    }

    private RestTestClient.ResponseSpec put(String token, Map<String, Object> req) {
        return http.put().uri("/api/v1/perfil")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(req)
                .exchange();
    }

    private byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        img.getGraphics().fillRect(0, 0, w, h);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private String subirFoto(String token) throws Exception {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("archivo", new ByteArrayResource(png(300, 200)) {
            @Override
            public String getFilename() {
                return "foto.png";
            }
        });
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/perfil/foto")
                .header(AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        return (String) body.get("imagenRef");
    }

    // --- Casos ---

    @Test
    void get_perfil_de_un_usuario_nuevo_devuelve_forma_vacia_no_completada() {
        Miembro m = crearMiembro();

        Map<String, Object> body = getPerfil(m.token());

        assertThat(body.get("completado")).isEqualTo(false);
        assertThat(body.get("usuarioId")).isEqualTo(m.id().intValue());
        assertThat(body.get("nombre")).isEqualTo("Persona");
        assertThat(body.get("imagenRef")).isNull();
        assertThat(body.get("imagenUrl")).isNull();
        assertThat(body.get("pareja")).isNull();
        assertThat(body.get("vinculoPendiente")).isNull();
        assertThat((List<?>) body.get("hijos")).isEmpty();
    }

    @Test
    void put_perfil_con_avatar_marca_completado_y_devuelve_imagenUrl() {
        Miembro m = crearMiembro();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = put(m.token(), putBase())
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("completado")).isEqualTo(true);
        assertThat(body.get("imagenTipo")).isEqualTo("AVATAR");
        assertThat(body.get("imagenUrl")).isEqualTo("/api/v1/media/avatares/01_chico.png");

        Map<String, Object> got = getPerfil(m.token());
        assertThat(got.get("completado")).isEqualTo(true);
        assertThat(got.get("nombre")).isEqualTo("Ana");
        assertThat(got.get("apellidos")).isEqualTo("García");
        assertThat(got.get("mote")).isEqualTo("Mote");
        assertThat(got.get("sobreMi")).isEqualTo("hola");
        assertThat(got.get("imagenRef")).isEqualTo("01_chico");
    }

    @Test
    void put_perfil_con_avatar_inexistente_da_400() {
        Miembro m = crearMiembro();
        Map<String, Object> req = putBase();
        req.put("imagenRef", "99_x");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = put(m.token(), req)
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(body.get("codigo")).isEqualTo("AVATAR_INEXISTENTE");
    }

    @Test
    void put_perfil_sin_responder_pareja_da_400() {
        Miembro m = crearMiembro();
        Map<String, Object> req = putBase();
        req.remove("tienePareja");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = put(m.token(), req)
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(body.get("codigo")).isEqualTo("VALIDACION");
    }

    @Test
    void put_perfil_con_sobre_mi_de_600_chars_da_400() {
        Miembro m = crearMiembro();
        Map<String, Object> req = putBase();
        req.put("sobreMi", "x".repeat(600));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = put(m.token(), req)
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(body.get("codigo")).isEqualTo("VALIDACION");
    }

    @Test
    void subir_foto_devuelve_ref_y_se_puede_recuperar() throws Exception {
        Miembro m = crearMiembro();

        String ref = subirFoto(m.token());
        assertThat(ref).matches("[0-9a-fA-F-]{36}\\.jpg");

        Map<String, Object> req = putBase();
        req.put("imagenTipo", "FOTO");
        req.put("imagenRef", ref);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = put(m.token(), req)
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(body.get("imagenUrl")).isEqualTo("/api/v1/media/fotos/" + ref);

        http.get().uri("/api/v1/media/fotos/" + ref)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("image/jpeg");
    }

    @Test
    void cambiar_de_foto_a_avatar_borra_el_fichero_anterior() throws Exception {
        Miembro m = crearMiembro();

        String ref = subirFoto(m.token());
        Map<String, Object> conFoto = putBase();
        conFoto.put("imagenTipo", "FOTO");
        conFoto.put("imagenRef", ref);
        put(m.token(), conFoto).expectStatus().isOk();

        http.get().uri("/api/v1/media/fotos/" + ref).exchange().expectStatus().isOk();

        put(m.token(), putBase()).expectStatus().isOk();

        http.get().uri("/api/v1/media/fotos/" + ref).exchange().expectStatus().isNotFound();
    }

    @Test
    void put_perfil_con_foto_ref_malformada_da_400() {
        Miembro m = crearMiembro();
        Map<String, Object> req = putBase();
        req.put("imagenTipo", "FOTO");
        req.put("imagenRef", "no-es-uuid.jpg");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = put(m.token(), req)
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(body.get("codigo")).isEqualTo("IMAGEN_REF_INVALIDA");
    }

    @Test
    void put_perfil_con_foto_ref_inexistente_da_400() {
        Miembro m = crearMiembro();
        Map<String, Object> req = putBase();
        req.put("imagenTipo", "FOTO");
        req.put("imagenRef", java.util.UUID.randomUUID() + ".jpg");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = put(m.token(), req)
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(body.get("codigo")).isEqualTo("IMAGEN_REF_INVALIDA");
    }

    @Test
    void subir_foto_con_tipo_no_soportado_da_415() {
        Miembro m = crearMiembro();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("archivo", new ByteArrayResource("no soy una imagen".getBytes()) {
            @Override
            public String getFilename() {
                return "notas.txt";
            }
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/perfil/foto")
                .header(AUTHORIZATION, "Bearer " + m.token())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(body.get("codigo")).isEqualTo("IMAGEN_NO_SOPORTADA");
    }

    @Test
    void get_perfil_avatares_devuelve_el_catalogo() {
        Miembro m = crearMiembro();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = http.get().uri("/api/v1/perfil/avatares")
                .header(AUTHORIZATION, "Bearer " + m.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class)
                .returnResult().getResponseBody();

        assertThat(lista).isNotEmpty();
        assertThat(lista).allSatisfy(item -> {
            assertThat(item.get("id")).isNotNull();
            assertThat(item.get("genero")).isNotNull();
        });
    }
}
