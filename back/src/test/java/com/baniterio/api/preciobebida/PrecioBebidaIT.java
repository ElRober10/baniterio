package com.baniterio.api.preciobebida;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/** Precio bebidas: listado de eventos (años → eventos), visible a cualquier miembro. */
class PrecioBebidaIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired CuentaRepository cuentas;
    @Autowired EventoRepository eventos;
    @Autowired PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    String token() {
        return token(RolMembresia.MIEMBRO);
    }

    String token(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@pb.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("PB").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("token");
    }

    long crearEvento(String nombre, LocalDate fecha, boolean oculto) {
        Cuenta c = cuentas.save(Cuenta.builder().pena(pena()).nombre(nombre + " cuenta")
                .llevaFichaBebida(false).build());
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(c).nombre(nombre)
                .fecha(fecha).oculto(oculto).build());
        return e.getId();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> eventos(String token) {
        return http.get().uri("/api/v1/precio-bebida/eventos")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(List.class).returnResult().getResponseBody();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> getMap(String uri, String token) {
        return http.get().uri(uri).header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> getList(String uri, String token) {
        return http.get().uri(uri).header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(List.class).returnResult().getResponseBody();
    }

    Map<String, Object> alcohol(long eventoId, String token) {
        return getMap("/api/v1/precio-bebida/eventos/" + eventoId + "/alcohol", token);
    }

    @SuppressWarnings("unchecked")
    long idDeBebida(Map<String, Object> grilla, String nombre) {
        return ((Number) ((List<Map<String, Object>>) grilla.get("bebidas")).stream()
                .filter(b -> nombre.equals(b.get("nombre")))
                .findFirst().orElseThrow().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    long idDeTienda(Map<String, Object> grilla, String nombre) {
        return ((Number) ((List<Map<String, Object>>) grilla.get("tiendas")).stream()
                .filter(t -> nombre.equals(t.get("nombre")))
                .findFirst().orElseThrow().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    Double precioDe(Map<String, Object> grilla, long bebidaId, String tamano, long tiendaId) {
        return ((List<Map<String, Object>>) grilla.get("precios")).stream()
                .filter(p -> ((Number) p.get("bebidaId")).longValue() == bebidaId
                        && tamano.equals(p.get("tamano"))
                        && ((Number) p.get("tiendaId")).longValue() == tiendaId)
                .findFirst().map(p -> ((Number) p.get("precio")).doubleValue()).orElse(null);
    }

    @Test
    void un_miembro_sin_area_ve_los_eventos_no_ocultos() {
        String tok = token();
        long id2026 = crearEvento("Precio bebida IT 2026", LocalDate.of(2026, 9, 25), false);
        long idOculto = crearEvento("Precio bebida IT oculto", LocalDate.of(2026, 9, 26), true);

        List<Map<String, Object>> lista = eventos(tok);

        assertThat(lista).extracting(l -> ((Number) l.get("id")).longValue()).contains(id2026);
        assertThat(lista).extracting(l -> ((Number) l.get("id")).longValue()).doesNotContain(idOculto);
    }

    @Test
    void devuelve_eventos_de_varios_anios_para_agrupar_en_el_front() {
        String tok = token();
        long id2025 = crearEvento("Precio bebida IT anio pasado", LocalDate.of(2025, 5, 1), false);
        long id2026 = crearEvento("Precio bebida IT anio actual", LocalDate.of(2026, 9, 25), false);

        List<Map<String, Object>> lista = eventos(tok);
        List<Long> ids = lista.stream().map(l -> ((Number) l.get("id")).longValue()).toList();

        assertThat(ids).contains(id2025, id2026);
    }

    @Test
    void tiendas_lista_las_sembradas() {
        List<Map<String, Object>> lista = getList("/api/v1/precio-bebida/tiendas", token());
        assertThat(lista).extracting(t -> t.get("nombre"))
                .contains("Alcampo", "Makro", "Carrefour", "Mercadona", "Hipercor", "Amazon", "Merkocash");
    }

    @Test
    void crear_tienda_sin_admin_es_403() {
        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + token())
                .body(Map.of("nombre", "Eroski"))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO");
    }

    @Test
    void crear_tienda_admin_la_crea_y_repetida_es_409() {
        String admin = token(RolMembresia.ADMIN);
        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("nombre", "Eroski PB"))
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.nombre").isEqualTo("Eroski PB");

        assertThat(getList("/api/v1/precio-bebida/tiendas", admin))
                .extracting(t -> t.get("nombre")).contains("Eroski PB");

        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("nombre", "Eroski PB"))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("TIENDA_DUPLICADA");
    }

    @Test
    void alcohol_materializa_70cl_y_1l_y_no_los_duplica_en_la_siguiente_lectura() {
        long eventoId = crearEvento("Precio bebida IT alcohol tamanos", LocalDate.of(2026, 9, 25), false);
        String tok = token();

        Map<String, Object> primera = alcohol(eventoId, tok);
        assertThat((List<String>) primera.get("tamanos")).containsExactly("70 cl", "1 L");

        Map<String, Object> segunda = alcohol(eventoId, tok);
        assertThat((List<String>) segunda.get("tamanos")).containsExactly("70 cl", "1 L");
    }

    @Test
    void alcohol_las_marcas_vienen_del_catalogo_aceptado() {
        long eventoId = crearEvento("Precio bebida IT alcohol catalogo", LocalDate.of(2026, 9, 25), false);
        Map<String, Object> grilla = alcohol(eventoId, token());
        assertThat((List<?>) grilla.get("bebidas")).isNotEmpty();
        assertThat(grilla.get("puedoEditar")).isEqualTo(false);
    }

    @Test
    void guardar_precio_sin_admin_es_403() {
        long eventoId = crearEvento("Precio bebida IT precio sin admin", LocalDate.of(2026, 9, 25), false);
        String tok = token();
        Map<String, Object> grilla = alcohol(eventoId, tok);
        long bebidaId = idDeBebida(grilla, "Barceló");
        long tiendaId = idDeTienda(grilla, "Alcampo");

        http.put().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/alcohol/precio")
                .header(AUTHORIZATION, "Bearer " + tok)
                .body(Map.of("bebidaId", bebidaId, "tamano", "70 cl", "tiendaId", tiendaId, "precio", 8.5))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO");
    }

    @Test
    void guardar_precio_admin_lo_refleja_y_precio_null_lo_borra() {
        long eventoId = crearEvento("Precio bebida IT precio admin", LocalDate.of(2026, 9, 25), false);
        String admin = token(RolMembresia.ADMIN);
        Map<String, Object> grilla = alcohol(eventoId, admin);
        long bebidaId = idDeBebida(grilla, "Barceló");
        long tiendaId = idDeTienda(grilla, "Alcampo");

        http.put().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/alcohol/precio")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bebidaId", bebidaId, "tamano", "70 cl", "tiendaId", tiendaId, "precio", 8.5))
                .exchange().expectStatus().isNoContent();

        assertThat(precioDe(alcohol(eventoId, admin), bebidaId, "70 cl", tiendaId)).isEqualTo(8.5);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("bebidaId", bebidaId);
        body.put("tamano", "70 cl");
        body.put("tiendaId", tiendaId);
        body.put("precio", null);
        http.put().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/alcohol/precio")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(body)
                .exchange().expectStatus().isNoContent();

        assertThat(precioDe(alcohol(eventoId, admin), bebidaId, "70 cl", tiendaId)).isNull();
    }

    @Test
    void guardar_precio_con_tamano_no_valido_es_400() {
        long eventoId = crearEvento("Precio bebida IT precio tamano invalido", LocalDate.of(2026, 9, 25), false);
        String admin = token(RolMembresia.ADMIN);
        Map<String, Object> grilla = alcohol(eventoId, admin);
        long bebidaId = idDeBebida(grilla, "Barceló");
        long tiendaId = idDeTienda(grilla, "Alcampo");

        http.put().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/alcohol/precio")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bebidaId", bebidaId, "tamano", "3 L", "tiendaId", tiendaId, "precio", 20))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("TAMANO_PRECIO_BEBIDA_NO_VALIDO");
    }

    @Test
    void anadir_tamano_sin_admin_403_duplicado_409_y_aparece_en_la_grilla() {
        long eventoId = crearEvento("Precio bebida IT anadir tamano", LocalDate.of(2026, 9, 25), false);
        String admin = token(RolMembresia.ADMIN);
        String miembro = token();
        alcohol(eventoId, admin);

        http.post().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/alcohol/tamanos")
                .header(AUTHORIZATION, "Bearer " + miembro)
                .body(Map.of("tamano", "3 L"))
                .exchange().expectStatus().isForbidden();

        http.post().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/alcohol/tamanos")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("tamano", "3 L"))
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$[2]").isEqualTo("3 L");

        assertThat((List<String>) alcohol(eventoId, admin).get("tamanos")).containsExactly("70 cl", "1 L", "3 L");

        http.post().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/alcohol/tamanos")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("tamano", "3 L"))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("TAMANO_PRECIO_BEBIDA_DUPLICADO");
    }

    // --- Rejilla de artículos sin tamaños (refrescos, cerveza, limpieza, comida) ---

    Map<String, Object> articulos(long eventoId, String seccion, String token) {
        return getMap("/api/v1/precio-bebida/eventos/" + eventoId + "/articulos/" + seccion, token);
    }

    @SuppressWarnings("unchecked")
    Double precioArticuloDe(Map<String, Object> grilla, String nombre, long tiendaId) {
        return ((List<Map<String, Object>>) grilla.get("precios")).stream()
                .filter(p -> nombre.equals(p.get("nombreArticulo"))
                        && ((Number) p.get("tiendaId")).longValue() == tiendaId)
                .findFirst().map(p -> ((Number) p.get("precio")).doubleValue()).orElse(null);
    }

    @Test
    void articulos_refrescos_lista_el_catalogo_de_bebidas_refresco() {
        long eventoId = crearEvento("Precio articulo IT refrescos", LocalDate.of(2026, 9, 25), false);
        Map<String, Object> grilla = articulos(eventoId, "REFRESCOS", token());
        assertThat((List<String>) grilla.get("articulos")).contains("Coca-Cola");
    }

    @Test
    void articulos_cerveza_incluye_tinto_de_verano_fijo() {
        long eventoId = crearEvento("Precio articulo IT cerveza", LocalDate.of(2026, 9, 25), false);
        Map<String, Object> grilla = articulos(eventoId, "CERVEZA", token());
        assertThat((List<String>) grilla.get("articulos")).containsExactly(
                "Cerveza", "Cerveza sin alcohol", "Cerveza sin gluten", "Tinto de verano");
    }

    @Test
    void articulos_categoria_no_valida_es_400() {
        long eventoId = crearEvento("Precio articulo IT categoria invalida", LocalDate.of(2026, 9, 25), false);
        http.get().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/articulos/ALCOHOL")
                .header(AUTHORIZATION, "Bearer " + token())
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("CATEGORIA_ARTICULO_NO_VALIDA");
    }

    @Test
    void guardar_precio_articulo_sin_admin_es_403() {
        long eventoId = crearEvento("Precio articulo IT sin admin", LocalDate.of(2026, 9, 25), false);
        String tok = token();
        long tiendaId = idDeTienda(articulos(eventoId, "REFRESCOS", tok), "Alcampo");

        http.put().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/articulos/REFRESCOS/precio")
                .header(AUTHORIZATION, "Bearer " + tok)
                .body(Map.of("nombreArticulo", "Coca-Cola", "tiendaId", tiendaId, "precio", 1.2))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO");
    }

    @Test
    void guardar_precio_articulo_admin_lo_refleja_y_precio_null_lo_borra() {
        long eventoId = crearEvento("Precio articulo IT admin", LocalDate.of(2026, 9, 25), false);
        String admin = token(RolMembresia.ADMIN);
        long tiendaId = idDeTienda(articulos(eventoId, "REFRESCOS", admin), "Alcampo");

        http.put().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/articulos/REFRESCOS/precio")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("nombreArticulo", "Coca-Cola", "tiendaId", tiendaId, "precio", 1.2))
                .exchange().expectStatus().isNoContent();

        assertThat(precioArticuloDe(articulos(eventoId, "REFRESCOS", admin), "Coca-Cola", tiendaId)).isEqualTo(1.2);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("nombreArticulo", "Coca-Cola");
        body.put("tiendaId", tiendaId);
        body.put("precio", null);
        http.put().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/articulos/REFRESCOS/precio")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(body)
                .exchange().expectStatus().isNoContent();

        assertThat(precioArticuloDe(articulos(eventoId, "REFRESCOS", admin), "Coca-Cola", tiendaId)).isNull();
    }

    @Test
    void guardar_precio_articulo_con_nombre_no_valido_es_400() {
        long eventoId = crearEvento("Precio articulo IT nombre invalido", LocalDate.of(2026, 9, 25), false);
        String admin = token(RolMembresia.ADMIN);
        long tiendaId = idDeTienda(articulos(eventoId, "REFRESCOS", admin), "Alcampo");

        http.put().uri("/api/v1/precio-bebida/eventos/" + eventoId + "/articulos/REFRESCOS/precio")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("nombreArticulo", "Bebida inventada", "tiendaId", tiendaId, "precio", 1.2))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("NOMBRE_ARTICULO_NO_VALIDO");
    }
}
