package com.baniterio.api.compra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Alternativa;
import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.Bebida;
import com.baniterio.api.identidad.BebidaRepository;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoAsistencia;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Modalidad;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PermisoArea;
import com.baniterio.api.identidad.PermisoAreaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.TipoBebida;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.inventario.ArticuloEvento;
import com.baniterio.api.inventario.ArticuloEventoRepository;
import com.baniterio.api.inventario.ArticuloInventario;
import com.baniterio.api.inventario.ArticuloInventarioRepository;
import com.baniterio.api.inventario.CategoriaInventario;
import com.baniterio.api.preciobebida.PrecioArticuloEvento;
import com.baniterio.api.preciobebida.PrecioArticuloEventoRepository;
import com.baniterio.api.preciobebida.ProductoKiloEvento;
import com.baniterio.api.preciobebida.ProductoKiloEventoRepository;
import com.baniterio.api.preciobebida.TamanoArticuloEvento;
import com.baniterio.api.preciobebida.TamanoArticuloEventoRepository;
import com.baniterio.api.preciobebida.Tienda;
import com.baniterio.api.preciobebida.TiendaRepository;
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
 * Lista de la compra, bloque 1.5: sincronización de {@code linea_compra_evento},
 * botón "Comprado" (envío al inventario de la fiesta), bloqueo del auto-cálculo
 * y "Devolver a la lista".
 */
class ListaCompraSyncIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired PermisoAreaRepository permisos;
    @Autowired CuentaRepository cuentas;
    @Autowired EventoRepository eventos;
    @Autowired AsistenciaEventoRepository asistencias;
    @Autowired FichaBebidaRepository fichas;
    @Autowired BebidaRepository bebidas;
    @Autowired LineaCompraEventoRepository lineas;
    @Autowired ArticuloInventarioRepository articulosInventario;
    @Autowired ArticuloEventoRepository articulosEvento;
    @Autowired TiendaRepository tiendas;
    @Autowired PrecioArticuloEventoRepository precioArticulo;
    @Autowired TamanoArticuloEventoRepository tamanosArticulo;
    @Autowired ProductoKiloEventoRepository productosKilo;
    @Autowired PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    String token(RolMembresia rol, boolean conArea) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@sync.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("SY").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        if (conArea) {
            permisos.save(PermisoArea.builder().usuario(u).area(AreaProtegida.INVENTARIO).build());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("token");
    }

    long crearEventoDeUnDia(String nombre) {
        Cuenta c = cuentas.save(Cuenta.builder().pena(pena()).nombre(nombre + " cuenta")
                .llevaFichaBebida(false).build());
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(c).nombre(nombre)
                .fecha(LocalDate.now().minusDays(60)).oculto(false).build());
        return e.getId();
    }

    /** Evento de 2 días con ficha de bebida (para las reglas ALCOHOL_SELECCIONADO/CERVEZA_ALTERNATIVA). */
    long crearEventoConFicha(String nombre) {
        Cuenta c = cuentas.save(Cuenta.builder().pena(pena()).nombre(nombre + " cuenta")
                .llevaFichaBebida(true).build());
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(c).nombre(nombre)
                .fecha(LocalDate.now().minusDays(61)).fechaFin(LocalDate.now().minusDays(60)).oculto(false).build());
        return e.getId();
    }

    /** Apunta una persona los 2 días que bebe {@code marcaAlcohol} (rellena su ficha de bebida). */
    void apuntarConAlcohol(long eventoId, String marcaAlcohol) {
        apuntarConAlcohol(eventoId, marcaAlcohol, "Coca-Cola");
    }

    void apuntarConAlcohol(long eventoId, String marcaAlcohol, String nombreRefresco) {
        Evento evento = eventos.findById(eventoId).orElseThrow();
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@sync.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("A" + tel).apellidos("L").esSuperadmin(false).activo(true).build());
        AsistenciaEvento a = asistencias.save(AsistenciaEvento.builder()
                .evento(evento).usuario(u).estado(EstadoAsistencia.APUNTADO).build());
        Bebida alcohol = bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.ALCOHOL, marcaAlcohol).orElseThrow();
        Bebida refresco = bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.REFRESCO, nombreRefresco).orElseThrow();
        fichas.save(FichaBebida.builder()
                .asistencia(a).alcohol(alcohol).refresco(refresco)
                .alternativa(Alternativa.NADA).modalidad(Modalidad.COMPLETA)
                .asisteDia1(true).asisteDia2(true).embarazada(false).build());
    }

    void apuntar(long eventoId, int cuantos) {
        var evento = eventos.findById(eventoId).orElseThrow();
        for (int i = 0; i < cuantos; i++) {
            String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
            var u = usuarios.save(Usuario.builder()
                    .telefono(tel).email(tel + "@sync.test")
                    .passwordHash(passwordEncoder.encode("secreto1"))
                    .nombre("S" + tel).apellidos("Y").esSuperadmin(false).activo(true).build());
            asistencias.save(AsistenciaEvento.builder()
                    .evento(evento).usuario(u).estado(EstadoAsistencia.APUNTADO).build());
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> getMap(String uri, String token) {
        return http.get().uri(uri).header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> reglasAdmin(long eventoId, String token) {
        Map<String, Object> body = getMap("/api/v1/admin/lista-compra/eventos/" + eventoId, token);
        return (List<Map<String, Object>>) body.get("reglas");
    }

    long idDeRegla(List<Map<String, Object>> reglas, String nombre) {
        return ((Number) reglas.stream().filter(r -> nombre.equals(r.get("nombre")))
                .findFirst().orElseThrow().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    Double cantidadLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) {
                continue;
            }
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    return ((Number) l.get("cantidad")).doubleValue();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    String tamanoLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) {
                continue;
            }
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    return (String) l.get("tamano");
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    long idLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) {
                continue;
            }
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    return ((Number) l.get("id")).longValue();
                }
            }
        }
        throw new AssertionError("línea no encontrada: " + categoria + "/" + nombre);
    }

    @SuppressWarnings("unchecked")
    String tiendaLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) {
                continue;
            }
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    return (String) l.get("tienda");
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    Double precioUnitarioLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) {
                continue;
            }
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    Number p = (Number) l.get("precioUnitario");
                    return p == null ? null : p.doubleValue();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    Boolean compradaLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) {
                continue;
            }
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    return (Boolean) l.get("comprada");
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    Double cantidadFiesta(Map<String, Object> body, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            for (Map<String, Object> a : (List<Map<String, Object>>) cat.get("articulos")) {
                if (nombre.equals(a.get("nombre"))) {
                    return ((Number) a.get("cantidad")).doubleValue();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    long idArticuloFiesta(Map<String, Object> body, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            for (Map<String, Object> a : (List<Map<String, Object>>) cat.get("articulos")) {
                if (nombre.equals(a.get("nombre"))) {
                    return ((Number) a.get("id")).longValue();
                }
            }
        }
        throw new AssertionError("artículo de fiesta no encontrado: " + nombre);
    }

    // --- Task 2: sincronización ---------------------------------------------

    @Test
    void la_lectura_materializa_las_lineas_y_las_actualiza_al_cambiar_los_apuntados() {
        long eventoId = crearEventoDeUnDia("Sync IT materializa");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 10);

        var body1 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body1, "LIMPIEZA", "Platos")).isEqualTo(30.0);

        apuntar(eventoId, 10);
        var body2 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body2, "LIMPIEZA", "Platos")).isEqualTo(60.0);
    }

    @Test
    void quitar_una_regla_borra_su_linea_no_comprada() {
        long eventoId = crearEventoDeUnDia("Sync IT borra");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 5);
        getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);

        long platosRegla = idDeRegla(reglasAdmin(eventoId, admin), "Platos");
        http.put().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + platosRegla)
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("activa", false, "factor", 3))
                .exchange().expectStatus().isNoContent();

        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isNull();
    }

    @Test
    void la_respuesta_trae_bloqueada_false_y_las_lineas_traen_id() {
        long eventoId = crearEventoDeUnDia("Sync IT campos");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 3);
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(body.get("bloqueada")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        var cats = (List<Map<String, Object>>) body.get("categorias");
        @SuppressWarnings("unchecked")
        var lineas0 = (List<Map<String, Object>>) cats.get(0).get("lineas");
        assertThat(lineas0.get(0)).containsKeys("id", "comprada");
    }

    // --- Task 3: marcar comprada ------------------------------------------

    @Test
    void marcar_comprada_mueve_la_cantidad_al_inventario_de_la_fiesta() {
        long eventoId = crearEventoDeUnDia("Sync IT comprado");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        long platosLinea = idLinea(body, "LIMPIEZA", "Platos");

        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNoContent();

        var fiesta = getMap("/api/v1/inventario/evento/" + eventoId, admin);
        assertThat(cantidadFiesta(fiesta, "Platos")).isEqualTo(12.0);

        var body2 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(compradaLinea(body2, "LIMPIEZA", "Platos")).isTrue();
    }

    @Test
    void una_linea_comprada_no_cambia_aunque_cambien_los_apuntados() {
        long eventoId = crearEventoDeUnDia("Sync IT congela");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        long platosLinea = idLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Platos");
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + admin).exchange().expectStatus().isNoContent();

        apuntar(eventoId, 10);
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);
    }

    @Test
    void marcar_comprada_una_linea_de_otro_evento_es_404() {
        long eventoId = crearEventoDeUnDia("Sync IT 404");
        String admin = token(RolMembresia.ADMIN, false);
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/99999999/comprado")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("LINEA_COMPRA_NO_ENCONTRADA");
    }

    @Test
    void marcar_comprada_sin_area_es_403() {
        long eventoId = crearEventoDeUnDia("Sync IT comprado 403");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 3);
        long platosLinea = idLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Platos");
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + token(RolMembresia.MIEMBRO, false))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_INVENTARIO");
    }

    // --- Task 4: bloqueo -------------------------------------------------

    @Test
    void bloquear_detiene_la_sincronizacion() {
        long eventoId = crearEventoDeUnDia("Sync IT bloqueo");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);

        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", true))
                .exchange().expectStatus().isNoContent();

        apuntar(eventoId, 8);
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(body.get("bloqueada")).isEqualTo(true);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);

        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", false))
                .exchange().expectStatus().isNoContent();
        var body2 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body2, "LIMPIEZA", "Platos")).isEqualTo(36.0);
    }

    @Test
    void bloquear_sincroniza_una_ultima_vez() {
        long eventoId = crearEventoDeUnDia("Sync IT bloqueo sync");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", true))
                .exchange().expectStatus().isNoContent();
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);
    }

    @Test
    void bloquear_sin_area_es_403() {
        long eventoId = crearEventoDeUnDia("Sync IT bloqueo 403");
        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + token(RolMembresia.MIEMBRO, false))
                .body(Map.of("bloqueada", true))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_INVENTARIO");
    }

    // --- Task 5: devolver a la lista ------------------------------------

    @Test
    void devolver_a_la_lista_revierte_solo_lo_comprado_y_la_linea_vuelve_a_pendiente() {
        long eventoId = crearEventoDeUnDia("Sync IT devolver");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        long platosLinea = idLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Platos");
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + admin).exchange().expectStatus().isNoContent();

        long artId = idArticuloFiesta(getMap("/api/v1/inventario/evento/" + eventoId, admin), "Platos");

        http.post().uri("/api/v1/inventario/evento/" + eventoId + "/" + artId + "/devolver-a-lista")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNoContent();

        assertThat(cantidadFiesta(getMap("/api/v1/inventario/evento/" + eventoId, admin), "Platos")).isNull();
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(compradaLinea(body, "LIMPIEZA", "Platos")).isFalse();
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);
    }

    @Test
    void devolver_a_la_lista_articulo_inexistente_es_404() {
        long eventoId = crearEventoDeUnDia("Sync IT devolver 404");
        String admin = token(RolMembresia.ADMIN, false);
        http.post().uri("/api/v1/inventario/evento/" + eventoId + "/99999999/devolver-a-lista")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("ARTICULO_EVENTO_NO_ENCONTRADO");
    }

    // --- Task 6: descuento por inventario (bloque 2) ------------------------

    long crearReglaPorEvento(long eventoId, String admin, String categoria, String nombre, int factor) {
        @SuppressWarnings("unchecked")
        Map<String, Object> creada = http.post().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("categoria", categoria, "nombre", nombre, "tamano", "unidad",
                        "tipoFormula", "POR_EVENTO", "factor", factor))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        return ((Number) creada.get("id")).longValue();
    }

    void sumarInventarioGeneral(String nombre, CategoriaInventario categoria, BigDecimal cantidad) {
        articulosInventario.save(ArticuloInventario.builder()
                .pena(pena()).categoria(categoria).nombre(nombre).tamano("unidad")
                .cantidad(cantidad).orden(999).build());
    }

    void sumarInventarioFiesta(long eventoId, String nombre, CategoriaInventario categoria, BigDecimal cantidad) {
        Evento evento = eventos.findById(eventoId).orElseThrow();
        articulosEvento.save(ArticuloEvento.builder()
                .evento(evento).categoria(categoria).nombre(nombre).tamano("unidad")
                .cantidad(cantidad).cantidadComprada(BigDecimal.ZERO).orden(999).build());
    }

    @Test
    void el_inventario_general_de_la_pena_no_descuenta_la_lista() {
        // Solo lo enviado a ESTE evento (inventario de la fiesta) descuenta; lo que
        // haya suelto en el almacén general de la peña no cuenta para este evento.
        long eventoId = crearEventoDeUnDia("Sync IT inventario general no cuenta");
        String admin = token(RolMembresia.ADMIN, false);
        crearReglaPorEvento(eventoId, admin, "COMIDA", "Servilletas", 5);
        sumarInventarioGeneral("Servilletas", CategoriaInventario.COMIDA, new BigDecimal("100"));

        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "COMIDA", "Servilletas")).isEqualTo(5.0);
    }

    @Test
    void el_resto_tras_restar_stock_se_redondea_siempre_al_alza() {
        // Fregasuelos: 0.2 L de necesidad bruta, sin redondear antes de restar (para
        // no comprar de más). Sin stock, lo que sobra (0.2) se redondea a 1 entero
        // (no se compran fracciones de litro sueltas). Si en la fiesta ya hay 0.2 L
        // o más, no sobra nada y no hace falta comprar.
        long eventoId = crearEventoDeUnDia("Sync IT litro");
        String admin = token(RolMembresia.ADMIN, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> creada = http.post().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("categoria", "LIMPIEZA", "nombre", "Fregasuelos evento", "tamano", "litro",
                        "tipoFormula", "POR_EVENTO", "factor", 0.2))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        long reglaId = ((Number) creada.get("id")).longValue();

        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Fregasuelos evento")).isEqualTo(1.0);

        Evento evento = eventos.findById(eventoId).orElseThrow();
        articulosEvento.save(ArticuloEvento.builder()
                .evento(evento).categoria(CategoriaInventario.LIMPIEZA).nombre("Fregasuelos evento")
                .tamano("litro").cantidad(new BigDecimal("0.5")).cantidadComprada(BigDecimal.ZERO)
                .orden(999).build());
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Fregasuelos evento")).isNull();

        http.delete().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + reglaId)
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void alcohol_de_una_sola_persona_compra_la_botella_mas_pequena_que_llegue_al_litro() {
        // 1 persona, 2 días, factor 0.5/persona-día -> hace falta 1 L en total como
        // mínimo. Sin stock se compra la botella de 1 L; si ya hay 0.8 L en la
        // fiesta, con una botella de 70cl se pasa de sobra del litro, así que se
        // compra esa en vez de la de 1 L.
        long eventoId = crearEventoConFicha("Sync IT botella alcohol");
        String admin = token(RolMembresia.ADMIN, false);
        apuntarConAlcohol(eventoId, "Beefeater");

        var body1 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body1, "ALCOHOL", "Beefeater")).isEqualTo(1.0);
        assertThat(tamanoLinea(body1, "ALCOHOL", "Beefeater")).isEqualTo("1 L");

        Evento evento = eventos.findById(eventoId).orElseThrow();
        articulosEvento.save(ArticuloEvento.builder()
                .evento(evento).categoria(CategoriaInventario.ALCOHOL).nombre("Beefeater")
                .tamano("1 L").cantidad(new BigDecimal("0.8")).cantidadComprada(BigDecimal.ZERO)
                .orden(999).build());

        var body2 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body2, "ALCOHOL", "Beefeater")).isEqualTo(1.0);
        assertThat(tamanoLinea(body2, "ALCOHOL", "Beefeater")).isEqualTo("70 cl");
    }

    @Test
    void una_cantidad_a_0_no_aparece_en_la_lista() {
        long eventoId = crearEventoDeUnDia("Sync IT oculta a 0");
        String admin = token(RolMembresia.ADMIN, false);
        crearReglaPorEvento(eventoId, admin, "COMIDA", "Vasos", 5);
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "COMIDA", "Vasos")).isEqualTo(5.0);

        sumarInventarioFiesta(eventoId, "Vasos", CategoriaInventario.COMIDA, new BigDecimal("5"));
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "COMIDA", "Vasos")).isNull();
    }

    @Test
    void descuenta_tambien_lo_ya_enviado_o_comprado_en_el_inventario_de_la_fiesta() {
        long eventoId = crearEventoDeUnDia("Sync IT descuento fiesta");
        String admin = token(RolMembresia.ADMIN, false);
        crearReglaPorEvento(eventoId, admin, "COMIDA", "Platos hondos", 5);

        Evento evento = eventos.findById(eventoId).orElseThrow();
        articulosEvento.save(ArticuloEvento.builder()
                .evento(evento).categoria(CategoriaInventario.COMIDA).nombre("Platos hondos").tamano("unidad")
                .cantidad(new BigDecimal("2")).cantidadComprada(new BigDecimal("1")).orden(1).build());

        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "COMIDA", "Platos hondos")).isEqualTo(2.0);
    }

    @Test
    void bloquear_congela_tambien_el_descuento_por_inventario() {
        long eventoId = crearEventoDeUnDia("Sync IT descuento bloqueo");
        String admin = token(RolMembresia.ADMIN, false);
        crearReglaPorEvento(eventoId, admin, "COMIDA", "Hielo evento", 4);
        getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);

        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", true))
                .exchange().expectStatus().isNoContent();

        sumarInventarioFiesta(eventoId, "Hielo evento", CategoriaInventario.COMIDA, new BigDecimal("4"));
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "COMIDA", "Hielo evento")).isEqualTo(4.0);

        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", false))
                .exchange().expectStatus().isNoContent();
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "COMIDA", "Hielo evento")).isNull();
    }

    @Test
    void la_cerveza_se_descuenta_por_categoria_entera_sin_importar_la_marca() {
        // La plantilla tiene una única línea genérica "Cerveza", pero el inventario de
        // la fiesta guarda una fila por marca (Mahou Clásica, Coronita...): cualquier
        // marca enviada a este evento tiene que cubrir esa necesidad genérica.
        long eventoId = crearEventoDeUnDia("Sync IT cerveza por marca");
        String admin = token(RolMembresia.ADMIN, false);
        long reglaId = crearReglaPorEvento(eventoId, admin, "CERVEZA", "Cerveza especial evento", 10);
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "PARA_ALTERNAR", "Cerveza especial evento")).isEqualTo(10.0);

        sumarInventarioFiesta(eventoId, "Marca A", CategoriaInventario.CERVEZA, new BigDecimal("3"));
        sumarInventarioFiesta(eventoId, "Marca B", CategoriaInventario.CERVEZA, new BigDecimal("4"));
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "PARA_ALTERNAR", "Cerveza especial evento")).isEqualTo(3.0);

        http.delete().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + reglaId)
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void cerveza_alternativa_con_precio_metido_elige_la_tienda_mas_barata() {
        long eventoId = crearEventoConFicha("Sync IT cerveza precio");
        Evento evento = eventos.findById(eventoId).orElseThrow();
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@sync.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("C" + tel).apellidos("Z").esSuperadmin(false).activo(true).build());
        AsistenciaEvento a = asistencias.save(AsistenciaEvento.builder()
                .evento(evento).usuario(u).estado(EstadoAsistencia.APUNTADO).build());
        Bebida refresco = bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.REFRESCO, "Coca-Cola").orElseThrow();
        fichas.save(FichaBebida.builder()
                .asistencia(a).alcohol(null).refresco(refresco)
                .alternativa(Alternativa.CERVEZA).modalidad(Modalidad.COMPLETA)
                .asisteDia1(true).asisteDia2(true).embarazada(false).build());

        Tienda cara = tiendas.findByPenaIdOrderByOrdenAscNombreAsc(pena().getId()).stream()
                .filter(t -> "Hipercor".equals(t.getNombre())).findFirst().orElseThrow();
        Tienda barata = tiendas.findByPenaIdOrderByOrdenAscNombreAsc(pena().getId()).stream()
                .filter(t -> "Alcampo".equals(t.getNombre())).findFirst().orElseThrow();
        precioArticulo.save(PrecioArticuloEvento.builder()
                .evento(evento).categoria(CategoriaInventario.CERVEZA).nombreArticulo("Cerveza")
                .tienda(cara).precio(new BigDecimal("1.50")).build());
        precioArticulo.save(PrecioArticuloEvento.builder()
                .evento(evento).categoria(CategoriaInventario.CERVEZA).nombreArticulo("Cerveza")
                .tienda(barata).precio(new BigDecimal("0.90")).build());

        String tok = token(RolMembresia.MIEMBRO, false);
        Map<String, Object> body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", tok);

        assertThat(tiendaLinea(body, "PARA_ALTERNAR", "Cerveza")).isEqualTo("Alcampo");
        assertThat(precioUnitarioLinea(body, "PARA_ALTERNAR", "Cerveza")).isEqualTo(0.90);
    }

    void apuntarTamano(long eventoId, String refresco, String litros) {
        tamanosArticulo.save(TamanoArticuloEvento.builder().evento(eventos.findById(eventoId).orElseThrow())
                .nombreArticulo(refresco).litros(new BigDecimal(litros)).build());
    }

    @Test
    void refresco_pide_botellas_enteras_segun_el_tamano_de_la_rejilla() {
        String tok = token(RolMembresia.MIEMBRO, false);

        // 1 persona x 2 días = 4 L de refresco. Sin tamaño apuntado: botellas de 2 L.
        long porDefecto = crearEventoConFicha("Sync IT refresco defecto");
        apuntarConAlcohol(porDefecto, "Barceló", "Sprite");
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + porDefecto + "/lista-compra", tok),
                "REFRESCOS", "Sprite")).isEqualTo(2.0);

        long litro = crearEventoConFicha("Sync IT refresco 1L");
        apuntarConAlcohol(litro, "Barceló", "Nestea");
        apuntarTamano(litro, "Nestea", "1");
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + litro + "/lista-compra", tok),
                "REFRESCOS", "Nestea")).isEqualTo(4.0);

        // 4 L / 1,5 L = 2,67 botellas: se sube a la siguiente entera.
        long litroYMedio = crearEventoConFicha("Sync IT refresco 1,5L");
        apuntarConAlcohol(litroYMedio, "Barceló", "Tónica");
        apuntarTamano(litroYMedio, "Tónica", "1.5");
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + litroYMedio + "/lista-compra", tok),
                "REFRESCOS", "Tónica")).isEqualTo(3.0);
    }

    @Test
    void el_tamano_apuntado_en_un_evento_se_hereda_en_el_siguiente() {
        String tok = token(RolMembresia.MIEMBRO, false);
        long anterior = crearEventoConFicha("Sync IT refresco herencia 1");
        apuntarTamano(anterior, "Trina Naranja", "1");

        long nuevo = crearEventoConFicha("Sync IT refresco herencia 2");
        apuntarConAlcohol(nuevo, "Barceló", "Trina Naranja");
        // Sin apuntar nada en este evento, la Trina usa el 1 L del evento anterior: 4 botellas.
        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + nuevo + "/lista-compra", tok),
                "REFRESCOS", "Trina Naranja")).isEqualTo(4.0);
    }

    @Test
    void los_embutidos_de_duriber_salen_a_precio_kilo_por_peso_estimado() {
        long eventoId = crearEventoConFicha("Sync IT duriber");
        productosKilo.save(ProductoKiloEvento.builder().evento(eventos.findById(eventoId).orElseThrow())
                .nombreArticulo("Paletilla ibérica").precioKilo(new BigDecimal("18.50"))
                .pesoKg(new BigDecimal("5")).build());

        Map<String, Object> body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra",
                token(RolMembresia.MIEMBRO, false));

        assertThat(tiendaLinea(body, "COMIDA", "Paletilla ibérica")).isEqualTo("Jamones Duriber");
        assertThat(precioUnitarioLinea(body, "COMIDA", "Paletilla ibérica")).isEqualTo(92.5);
    }

    void apuntarConCervezaEspecial(long eventoId, String texto) {
        Evento evento = eventos.findById(eventoId).orElseThrow();
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@sync.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("E" + tel).apellidos("S").esSuperadmin(false).activo(true).build());
        AsistenciaEvento a = asistencias.save(AsistenciaEvento.builder()
                .evento(evento).usuario(u).estado(EstadoAsistencia.APUNTADO).build());
        fichas.save(FichaBebida.builder()
                .asistencia(a).alcohol(null).refresco(null)
                .alternativa(Alternativa.CERVEZA_ESPECIAL).cervezaEspecial(texto).modalidad(Modalidad.COMPLETA)
                .asisteDia1(true).asisteDia2(true).embarazada(false).build());
    }

    @SuppressWarnings("unchecked")
    String seccionDeLinea(Map<String, Object> body, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    return (String) cat.get("categoria");
                }
            }
        }
        return null;
    }

    @Test
    void las_cervezas_especiales_de_la_ficha_salen_en_para_alternar_con_el_tinto_de_verano() {
        long eventoId = crearEventoConFicha("Sync IT para alternar");
        apuntarConCervezaEspecial(eventoId, "sin gluten");
        apuntarConCervezaEspecial(eventoId, "Sin gluten ");
        apuntarConCervezaEspecial(eventoId, "sin alcohol");

        Map<String, Object> body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra",
                token(RolMembresia.MIEMBRO, false));

        // 2 personas x 2 días x 5 latas = 20; 1 persona x 2 días x 5 = 10.
        assertThat(cantidadLinea(body, "PARA_ALTERNAR", "Cerveza sin gluten")).isEqualTo(20.0);
        assertThat(cantidadLinea(body, "PARA_ALTERNAR", "Cerveza sin alcohol")).isEqualTo(10.0);
        assertThat(seccionDeLinea(body, "Cerveza sin gluten")).isEqualTo("PARA_ALTERNAR");
    }

    @Test
    void la_cerveza_especial_toma_el_precio_de_la_rejilla_aunque_se_escriba_con_otras_mayusculas() {
        long eventoId = crearEventoConFicha("Sync IT especial precio");
        apuntarConCervezaEspecial(eventoId, "Sin Gluten");
        Evento evento = eventos.findById(eventoId).orElseThrow();
        Tienda barata = tiendas.findByPenaIdOrderByOrdenAscNombreAsc(pena().getId()).stream()
                .filter(t -> "Alcampo".equals(t.getNombre())).findFirst().orElseThrow();
        precioArticulo.save(PrecioArticuloEvento.builder()
                .evento(evento).categoria(CategoriaInventario.CERVEZA).nombreArticulo("Cerveza sin gluten")
                .tienda(barata).precio(new BigDecimal("0.81")).build());

        Map<String, Object> body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra",
                token(RolMembresia.MIEMBRO, false));

        assertThat(tiendaLinea(body, "PARA_ALTERNAR", "Cerveza Sin Gluten")).isEqualTo("Alcampo");
        assertThat(precioUnitarioLinea(body, "PARA_ALTERNAR", "Cerveza Sin Gluten")).isEqualTo(0.81);
    }

    void precioDePack(long eventoId, String articulo, String tienda, String precio, int cantidad) {
        Tienda t = tiendas.findByPenaIdOrderByOrdenAscNombreAsc(pena().getId()).stream()
                .filter(x -> tienda.equals(x.getNombre())).findFirst().orElseThrow();
        precioArticulo.save(PrecioArticuloEvento.builder()
                .evento(eventos.findById(eventoId).orElseThrow()).categoria(CategoriaInventario.LIMPIEZA)
                .nombreArticulo(articulo).tienda(t).precio(new BigDecimal(precio)).cantidad(cantidad).build());
    }

    @Test
    void con_packs_elige_la_tienda_con_menor_coste_total_y_compra_packs_enteros() {
        long eventoId = crearEventoDeUnDia("Sync IT packs");
        apuntar(eventoId, 10); // Platos: 3 por peñista = 30 unidades
        precioDePack(eventoId, "Platos", "Alcampo", "1.00", 10);  // 3 packs = 3,00 €
        precioDePack(eventoId, "Platos", "Makro", "1.50", 50);    // 1 pack  = 1,50 €

        Map<String, Object> body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra",
                token(RolMembresia.MIEMBRO, false));

        assertThat(tiendaLinea(body, "LIMPIEZA", "Platos")).isEqualTo("Makro");
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(50.0);
        assertThat(precioUnitarioLinea(body, "LIMPIEZA", "Platos")).isEqualTo(0.03);
    }

    @Test
    void con_packs_pequenos_es_mas_barato_cuando_el_grande_sobra_demasiado() {
        long eventoId = crearEventoDeUnDia("Sync IT packs 2");
        apuntar(eventoId, 10); // 30 platos
        precioDePack(eventoId, "Platos", "Alcampo", "1.00", 10);  // 3 packs = 3,00 €
        precioDePack(eventoId, "Platos", "Makro", "4.00", 50);    // 1 pack  = 4,00 €

        Map<String, Object> body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra",
                token(RolMembresia.MIEMBRO, false));

        assertThat(tiendaLinea(body, "LIMPIEZA", "Platos")).isEqualTo("Alcampo");
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(30.0);
    }

    @Test
    void la_lista_trae_el_presupuesto_de_la_cuenta_del_evento() {
        long eventoId = crearEventoDeUnDia("Sync IT presupuesto");
        Map<String, Object> body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra",
                token(RolMembresia.MIEMBRO, false));
        // Cuenta recién creada, sin movimientos: saldo 0.
        assertThat(((Number) body.get("presupuesto")).doubleValue()).isEqualTo(0.0);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> infoLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) {
                continue;
            }
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) {
                    return (Map<String, Object>) l.get("info");
                }
            }
        }
        return null;
    }

    @Test
    void las_lineas_de_alcohol_traen_personas_que_la_beben_y_stock() {
        long eventoId = crearEventoConFicha("Sync IT info alcohol");
        apuntarConAlcohol(eventoId, "Brugal");     // 2 días de 2 = 1 persona
        apuntarConAlcohol(eventoId, "Brugal");
        sumarInventarioFiesta(eventoId, "Brugal", CategoriaInventario.ALCOHOL, new BigDecimal("2"));

        Map<String, Object> body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra",
                token(RolMembresia.MIEMBRO, false));
        Map<String, Object> info = infoLinea(body, "ALCOHOL", "Brugal");

        assertThat(info).isNotNull();
        assertThat(((Number) info.get("personas")).doubleValue()).isEqualTo(2.0);
        assertThat(((Number) info.get("stock")).doubleValue()).isEqualTo(2.0);
    }
}
