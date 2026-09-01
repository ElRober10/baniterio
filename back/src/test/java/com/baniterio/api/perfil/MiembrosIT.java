package com.baniterio.api.perfil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
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
 * Test de integración de {@code GET /api/v1/miembros}: orden de las tarjetas
 * (yo → pareja → hijos registrados → resto alfabético), exclusión de perfiles no
 * completados y de hijos no visibles, y que la tarjeta nunca lleva teléfono ni
 * email.
 *
 * <p>La BBDD la comparten todos los {@code *IT} (sin rollback), así que cada
 * caso trabaja con miembros que crea él y comprueba el orden <b>relativo</b> de
 * ese subconjunto, no la lista entera.
 */
class MiembrosIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    TelefonoAutorizadoRepository telefonosAutorizados;

    @Autowired
    PasswordEncoder passwordEncoder;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    // --- Helpers ---

    private record Miembro(Long id, String telefono, String nombre, String token) {
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
                .email(telefono + "@miembros.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre(nombre)
                .apellidos("Sin Perfil")
                .esSuperadmin(false)
                .activo(true)
                .build());
        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        membresias.save(Membresia.builder()
                .usuario(usuario).pena(pena).rol(RolMembresia.MIEMBRO).activa(true).build());
        return new Miembro(usuario.getId(), telefono, nombre, login(telefono));
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

    private Map<String, Object> base(String nombre, String apellidos) {
        Map<String, Object> r = new HashMap<>();
        r.put("nombre", nombre);
        r.put("apellidos", apellidos);
        r.put("imagenTipo", "AVATAR");
        r.put("imagenRef", "01_chico");
        r.put("tienePareja", false);
        r.put("hijos", List.of());
        return r;
    }

    private void put(String token, Map<String, Object> body) {
        http.put().uri("/api/v1/perfil").header(AUTHORIZATION, "Bearer " + token).body(body)
                .exchange().expectStatus().isOk();
    }

    private void completarPerfil(String token, String nombre, String apellidos) {
        put(token, base(nombre, apellidos));
    }

    private void completarConSobreMi(String token, String nombre, String apellidos, String sobreMi) {
        Map<String, Object> r = base(nombre, apellidos);
        if (sobreMi != null) {
            r.put("sobreMi", sobreMi);
        }
        put(token, r);
    }

    private Map<String, Object> hijo(String nombre, String telefono, boolean visible) {
        Map<String, Object> m = new HashMap<>();
        m.put("nombre", nombre);
        m.put("mayorDeEdad", false);
        if (telefono != null) {
            m.put("telefono", telefono);
        }
        m.put("visible", visible);
        return m;
    }

    private void completarConHijos(String token, String nombre, String apellidos, List<Map<String, Object>> hijos) {
        Map<String, Object> r = base(nombre, apellidos);
        r.put("hijos", hijos);
        put(token, r);
    }

    private void declararPareja(String token, String nombre, String apellidos, String parejaNombre, String parejaTelefono) {
        Map<String, Object> r = base(nombre, apellidos);
        r.put("tienePareja", true);
        r.put("parejaNombre", parejaNombre);
        r.put("parejaTelefono", parejaTelefono);
        put(token, r);
    }

    private void aceptarPareja(String token) {
        http.post().uri("/api/v1/perfil/pareja/aceptar").header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isNoContent();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listar(String token) {
        return http.get().uri("/api/v1/miembros").header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody(List.class).returnResult().getResponseBody();
    }

    private static long id(Map<String, Object> tarjeta) {
        return ((Number) tarjeta.get("id")).longValue();
    }

    /** El orden en que salen los ids de {@code mios} dentro de la lista completa. */
    private List<Long> ordenRelativo(List<Map<String, Object>> lista, Set<Long> mios) {
        List<Long> r = new ArrayList<>();
        for (Map<String, Object> t : lista) {
            long tid = id(t);
            if (mios.contains(tid)) {
                r.add(tid);
            }
        }
        return r;
    }

    private Map<String, Object> tarjetaDe(List<Map<String, Object>> lista, Long usuarioId) {
        return lista.stream().filter(t -> id(t) == usuarioId).findFirst().orElseThrow();
    }

    // --- Casos ---

    @Test
    void la_primera_tarjeta_es_la_mia() {
        Miembro yo = crearMiembro();
        Miembro otro = crearMiembro();
        completarPerfil(yo.token(), "Zeta", "Zurbarán");
        completarPerfil(otro.token(), "Alba", "Abad");

        List<Map<String, Object>> lista = listar(yo.token());

        assertThat(id(lista.get(0))).isEqualTo(yo.id());
    }

    @Test
    void la_segunda_es_la_de_mi_pareja_aceptada() {
        Miembro yo = crearMiembro();
        Miembro pareja = crearMiembro();
        declararPareja(yo.token(), "Yolanda", "Yuste", pareja.nombre(), pareja.telefono());
        aceptarPareja(pareja.token());
        completarPerfil(pareja.token(), "Wenceslao", "Waldo");

        List<Map<String, Object>> lista = listar(yo.token());

        assertThat(id(lista.get(0))).isEqualTo(yo.id());
        assertThat(id(lista.get(1))).isEqualTo(pareja.id());
        assertThat(tarjetaDe(lista, yo.id()).get("parejaNombre")).isEqualTo(pareja.nombre());
    }

    @Test
    void los_hijos_registrados_van_despues_de_la_pareja_y_antes_del_resto() {
        Miembro yo = crearMiembro();
        Miembro pareja = crearMiembro();
        Miembro resto = crearMiembro();
        completarPerfil(resto.token(), "Aaron", "Ajeno"); // alfabéticamente iría el primero

        String telHijo = telefonoLibre();
        // La pareja completa su perfil ANTES de aceptar: así su editor no reenvía
        // (vacía) la lista de hijos compartidos tras el vínculo.
        completarPerfil(pareja.token(), "Xavier", "Ximénez");
        Map<String, Object> putYo = base("Yago", "Yáñez");
        putYo.put("tienePareja", true);
        putYo.put("parejaNombre", pareja.nombre());
        putYo.put("parejaTelefono", pareja.telefono());
        putYo.put("hijos", List.of(hijo("Críspulo", telHijo, true)));
        put(yo.token(), putYo);
        aceptarPareja(pareja.token());

        registrar(telHijo);
        String tokenHijo = login(telHijo);
        // Primer GET /perfil del hijo: enlaza hijo.usuario_id.
        http.get().uri("/api/v1/perfil").header(AUTHORIZATION, "Bearer " + tokenHijo)
                .exchange().expectStatus().isOk();
        Long hijoId = usuarios.findByTelefono(telHijo).orElseThrow().getId();
        completarPerfil(tokenHijo, "Hugo", "Hidalgo");

        List<Map<String, Object>> lista = listar(yo.token());

        assertThat(ordenRelativo(lista, Set.of(yo.id(), pareja.id(), hijoId, resto.id())))
                .containsExactly(yo.id(), pareja.id(), hijoId, resto.id());
    }

    @Test
    void el_resto_va_alfabetico() {
        Miembro viewer = crearMiembro();
        Miembro alvaro = crearMiembro();
        Miembro ana = crearMiembro();
        Miembro bruno = crearMiembro();
        completarPerfil(viewer.token(), "Viewer", "Vega");
        completarPerfil(alvaro.token(), "Álvaro", "Ruiz");
        completarPerfil(ana.token(), "ana", "perez");
        completarPerfil(bruno.token(), "Bruno", "Blanco");

        List<Map<String, Object>> lista = listar(viewer.token());

        // Collator es_ES: "Álvaro" < "ana" < "Bruno".
        assertThat(ordenRelativo(lista, Set.of(alvaro.id(), ana.id(), bruno.id())))
                .containsExactly(alvaro.id(), ana.id(), bruno.id());
    }

    @Test
    void no_se_listan_perfiles_no_completados() {
        Miembro yo = crearMiembro();
        Miembro sinPerfil = crearMiembro();
        completarPerfil(yo.token(), "Nora", "Núñez");
        // sinPerfil no completa nada.

        List<Map<String, Object>> lista = listar(yo.token());

        assertThat(lista).noneSatisfy(t -> assertThat(id(t)).isEqualTo(sinPerfil.id()));
    }

    @Test
    void la_tarjeta_no_expone_telefono_ni_email() {
        Miembro yo = crearMiembro();
        completarPerfil(yo.token(), "Telmo", "Torres");

        List<Map<String, Object>> lista = listar(yo.token());

        assertThat(lista).isNotEmpty();
        assertThat(lista).allSatisfy(t -> {
            assertThat(t).doesNotContainKey("telefono");
            assertThat(t).doesNotContainKey("email");
        });
    }

    @Test
    void los_hijos_no_visibles_no_aparecen_en_la_tarjeta() {
        Miembro yo = crearMiembro();
        completarConHijos(yo.token(), "Hilda", "Herrero", List.of(
                hijo("Visible", null, true),
                hijo("Oculta", null, false)));

        List<Map<String, Object>> lista = listar(yo.token());

        @SuppressWarnings("unchecked")
        List<String> hijos = (List<String>) tarjetaDe(lista, yo.id()).get("hijos");
        assertThat(hijos).containsExactly("Visible");
    }

    @Test
    void sobre_mi_vacio_no_aparece() {
        Miembro conTexto = crearMiembro();
        Miembro sinTexto = crearMiembro();
        completarConSobreMi(conTexto.token(), "Con", "Texto", "Me gusta el monte");
        completarConSobreMi(sinTexto.token(), "Sin", "Texto", null);

        List<Map<String, Object>> lista = listar(conTexto.token());

        assertThat(tarjetaDe(lista, conTexto.id()).get("sobreMi")).isEqualTo("Me gusta el monte");
        assertThat(tarjetaDe(lista, sinTexto.id()).get("sobreMi")).isNull();
    }
}
