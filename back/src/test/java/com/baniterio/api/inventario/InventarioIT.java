package com.baniterio.api.inventario;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PermisoArea;
import com.baniterio.api.identidad.PermisoAreaRepository;
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

/** GET (forma + puedoEditar) y PUT (ok / 403 / 404 / 400) de la sección Inventario. */
class InventarioIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    MembresiaRepository membresias;
    @Autowired
    PenaRepository penas;
    @Autowired
    PermisoAreaRepository permisos;
    @Autowired
    ArticuloInventarioRepository articulos;
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

    /** Crea un usuario con el rol dado y opcionalmente el área INVENTARIO, y devuelve su token. */
    String token(RolMembresia rol, boolean conAreaInventario) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@inv.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Inv").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        if (conAreaInventario) {
            permisos.save(PermisoArea.builder().usuario(u).area(AreaProtegida.INVENTARIO).build());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("token");
    }

    Long idDe(String categoria, String nombre) {
        return articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(pena().getId()).stream()
                .filter(a -> a.getCategoria().name().equals(categoria) && a.getNombre().equals(nombre))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void get_devuelve_las_cinco_categorias_y_puedoEditar_false_para_penista() {
        String token = token(RolMembresia.MIEMBRO, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/inventario")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        assertThat(body.get("puedoEditar")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = (List<Map<String, Object>>) body.get("categorias");
        assertThat(cats).hasSize(5);
        assertThat(cats).extracting(c -> c.get("categoria"))
                .containsExactly("ALCOHOL", "CERVEZA", "REFRESCOS", "LIMPIEZA", "COMIDA");
        Map<String, Object> comida = cats.get(4);
        assertThat((List<?>) comida.get("articulos")).isEmpty();
    }

    @Test
    void get_puedoEditar_true_con_area_inventario() {
        String token = token(RolMembresia.MIEMBRO, true);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/inventario")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        assertThat(body.get("puedoEditar")).isEqualTo(true);
    }

    @Test
    void put_con_permiso_cambia_la_cantidad() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long id = idDe("CERVEZA", "Mixta");

        http.put().uri("/api/v1/inventario/" + id)
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("nombre", "Mixta", "tamano", "lata", "cantidad", 9))
                .exchange().expectStatus().isOk();

        assertThat(articulos.findById(id).orElseThrow().getCantidad().intValue()).isEqualTo(9);
    }

    @Test
    void put_sin_permiso_es_403() {
        String token = token(RolMembresia.MIEMBRO, false);
        Long id = idDe("CERVEZA", "Mixta");

        http.put().uri("/api/v1/inventario/" + id)
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("nombre", "Mixta", "tamano", "lata", "cantidad", 9))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void put_con_tamano_ajeno_a_la_categoria_es_400() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long id = idDe("CERVEZA", "Mixta");

        http.put().uri("/api/v1/inventario/" + id)
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("nombre", "Mixta", "tamano", "garrafa", "cantidad", 9))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void post_con_permiso_crea_el_articulo() {
        String token = token(RolMembresia.MIEMBRO, true);

        http.post().uri("/api/v1/inventario")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("categoria", "ALCOHOL", "nombre", "Ron Barceló Añejo",
                        "tamano", "70 cl", "cantidad", 2))
                .exchange().expectStatus().isCreated();

        assertThat(articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(pena().getId()))
                .anyMatch(a -> a.getNombre().equals("Ron Barceló Añejo")
                        && a.getCategoria().name().equals("ALCOHOL"));
    }

    @Test
    void post_sin_permiso_es_403() {
        String token = token(RolMembresia.MIEMBRO, false);

        http.post().uri("/api/v1/inventario")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("categoria", "ALCOHOL", "nombre", "X", "tamano", "70 cl", "cantidad", 1))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void post_con_tamano_ajeno_a_la_categoria_es_400() {
        String token = token(RolMembresia.MIEMBRO, true);

        http.post().uri("/api/v1/inventario")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("categoria", "ALCOHOL", "nombre", "X", "tamano", "garrafa", "cantidad", 1))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void delete_con_permiso_da_de_baja_el_articulo() {
        String token = token(RolMembresia.MIEMBRO, true);
        // Se crea uno de usar y tirar para no romper a los demás tests, que comparten BBDD.
        http.post().uri("/api/v1/inventario")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("categoria", "CERVEZA", "nombre", "Radler de baja",
                        "tamano", "lata", "cantidad", 1))
                .exchange().expectStatus().isCreated();
        Long id = idDe("CERVEZA", "Radler de baja");

        http.delete().uri("/api/v1/inventario/" + id)
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isNoContent();

        assertThat(articulos.findById(id)).isEmpty();
    }

    @Test
    void delete_sin_permiso_es_403() {
        String token = token(RolMembresia.MIEMBRO, false);
        Long id = idDe("CERVEZA", "Mahou Clásica");

        http.delete().uri("/api/v1/inventario/" + id)
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void delete_a_id_inexistente_es_404() {
        String token = token(RolMembresia.MIEMBRO, true);

        http.delete().uri("/api/v1/inventario/999999")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void put_a_id_inexistente_es_404() {
        String token = token(RolMembresia.MIEMBRO, true);

        http.put().uri("/api/v1/inventario/999999")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("nombre", "X", "tamano", "lata", "cantidad", 1))
                .exchange().expectStatus().isNotFound();
    }
}
