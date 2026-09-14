package com.baniterio.api.compra;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
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

/** Lista de la compra: lectura (materializa, agrupa) y administración (ajuste, alta, baja). */
class ListaCompraIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired PermisoAreaRepository permisos;
    @Autowired CuentaRepository cuentas;
    @Autowired EventoRepository eventos;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ReglaCompraRepository reglaCompraPlantilla;

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
                .telefono(tel).email(tel + "@lc.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("LC").esSuperadmin(false).activo(true).build());
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

    /**
     * Evento propio para cada test. Fecha en el pasado (pero {@code oculto=false}):
     * la lista de la compra solo exige que el evento no esté oculto, y así estos
     * eventos de prueba no se cuelan en la primera página del listado de eventos
     * (que ordena los futuros primero) y no rompen a los tests hermanos.
     */
    long crearEventoDeUnDia(String nombre) {
        Cuenta c = cuentas.save(Cuenta.builder().pena(pena()).nombre(nombre + " cuenta")
                .llevaFichaBebida(false).build());
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(c).nombre(nombre)
                .fecha(LocalDate.now().minusDays(60)).oculto(false).build());
        return e.getId();
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

    long idDeReglaPorFormula(List<Map<String, Object>> reglas, String formula) {
        return ((Number) reglas.stream().filter(r -> formula.equals(r.get("tipoFormula")))
                .findFirst().orElseThrow().get("id")).longValue();
    }

    /** Busca una línea de la lista de la compra por categoría + nombre; devuelve su cantidad o null. */
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

    @Test
    void lectura_materializa_las_reglas_y_agrupa_por_categoria() {
        long eventoId = crearEventoDeUnDia("Lista compra IT ver");
        Map<String, Object> body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra",
                token(RolMembresia.MIEMBRO, false));

        assertThat(body.get("puedoEditar")).isEqualTo(false);
        assertThat(body.get("llevaFicha")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = (List<Map<String, Object>>) body.get("categorias");
        assertThat(cats).extracting(c -> c.get("categoria")).contains("LIMPIEZA", "COMIDA");
    }

    @Test
    void evento_inexistente_es_404() {
        http.get().uri("/api/v1/eventos/999999/lista-compra")
                .header(AUTHORIZATION, "Bearer " + token(RolMembresia.MIEMBRO, false))
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("EVENTO_NO_ENCONTRADO");
    }

    @Test
    void admin_sin_area_es_403() {
        long eventoId = crearEventoDeUnDia("Lista compra IT 403");
        http.get().uri("/api/v1/admin/lista-compra/eventos/" + eventoId)
                .header(AUTHORIZATION, "Bearer " + token(RolMembresia.MIEMBRO, false))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_INVENTARIO");
    }

    @Test
    void ajustar_cambia_la_cantidad_final_y_desactivar_quita_la_linea_de_la_lectura() {
        long eventoId = crearEventoDeUnDia("Lista compra IT ajuste");
        String admin = token(RolMembresia.ADMIN, false);
        long platosId = idDeRegla(reglasAdmin(eventoId, admin), "Platos");

        http.put().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + platosId)
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("cantidadAjustada", 99, "activa", true, "factor", 3))
                .exchange().expectStatus().isNoContent();

        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Platos")).isEqualTo(99.0);

        http.put().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + platosId)
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("activa", false, "factor", 3))
                .exchange().expectStatus().isNoContent();

        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Platos")).isNull();
    }

    @Test
    void ajustar_dinamica_con_cantidad_es_400() {
        long eventoId = crearEventoDeUnDia("Lista compra IT dinamica");
        String admin = token(RolMembresia.ADMIN, false);
        long alcoholId = idDeReglaPorFormula(reglasAdmin(eventoId, admin), "ALCOHOL_SELECCIONADO");
        http.put().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + alcoholId)
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("cantidadAjustada", 5, "activa", true, "factor", 0.5))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("AJUSTE_NO_APLICA");
    }

    @Test
    void crear_regla_manual_aparece_en_la_lectura_y_se_puede_borrar() {
        long eventoId = crearEventoDeUnDia("Lista compra IT manual");
        String admin = token(RolMembresia.ADMIN, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> creada = http.post().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("categoria", "COMIDA", "nombre", "Servilletas", "tamano", "paquete",
                        "tipoFormula", "POR_EVENTO", "factor", 2))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(creada.get("origen")).isEqualTo("MANUAL");
        long nuevaId = ((Number) creada.get("id")).longValue();

        assertThat(cantidadLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "COMIDA", "Servilletas")).isEqualTo(2.0);

        http.delete().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + nuevaId)
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void ajustar_el_factor_de_una_regla_de_plantilla_actualiza_tambien_la_plantilla() {
        long eventoId = crearEventoDeUnDia("Lista compra IT factor plantilla");
        String admin = token(RolMembresia.ADMIN, false);
        long platosId = idDeRegla(reglasAdmin(eventoId, admin), "Platos");

        http.put().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + platosId)
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("activa", true, "factor", 4))
                .exchange().expectStatus().isNoContent();

        Map<String, Object> platosTrasAjuste = reglasAdmin(eventoId, admin).stream()
                .filter(r -> "Platos".equals(r.get("nombre"))).findFirst().orElseThrow();
        assertThat(((Number) platosTrasAjuste.get("factor")).doubleValue()).isEqualTo(4.0);

        ReglaCompra dePlantilla = reglaCompraPlantilla
                .findByPenaIdAndCategoriaAndNombreAndTipoFormula(
                        pena().getId(), com.baniterio.api.inventario.CategoriaInventario.LIMPIEZA,
                        "Platos", TipoFormulaCompra.POR_PENISTA)
                .orElseThrow();
        assertThat(dePlantilla.getFactor()).isEqualByComparingTo("4");

        // La plantilla es global y compartida con el resto de los tests (misma BBDD,
        // sin rollback entre *IT): se deja como estaba para no contaminarlos.
        http.put().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + platosId)
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("activa", true, "factor", 3))
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void borrar_regla_de_plantilla_es_409() {
        long eventoId = crearEventoDeUnDia("Lista compra IT no borrable");
        String admin = token(RolMembresia.ADMIN, false);
        long platosId = idDeRegla(reglasAdmin(eventoId, admin), "Platos");
        http.delete().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + platosId)
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("REGLA_COMPRA_NO_BORRABLE");
    }
}
