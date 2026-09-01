package com.baniterio.api.perfil;

import java.util.List;
import java.util.Set;

import com.baniterio.api.perfil.OrdenadorTarjetas.Candidato;
import com.baniterio.api.perfil.OrdenadorTarjetas.ContextoOrden;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Orden de las tarjetas de {@code GET /miembros}: función pura, sin Spring.
 * Grupos: yo (0) → mi pareja (1) → mis hijos registrados (2) → el resto (3).
 * Dentro de cada grupo, alfabético por {@code nombre + " " + apellidos} con un
 * {@link java.text.Collator} español (ignora mayúsculas y acentos).
 */
class OrdenadorTarjetasTest {

    private static Candidato c(long id, String nombre, String apellidos) {
        return new Candidato(id, nombre, apellidos);
    }

    private static List<Long> ids(Long yoId, List<Candidato> candidatos, ContextoOrden ctx) {
        return OrdenadorTarjetas.ordenar(yoId, candidatos, ctx).stream().map(Candidato::id).toList();
    }

    @Test
    void mi_tarjeta_va_siempre_primera() {
        List<Candidato> entrada = List.of(
                c(1, "Aaron", "Aguilar"),
                c(99, "Zoe", "Zuazo"));
        ContextoOrden ctx = new ContextoOrden(null, Set.of());

        assertThat(ids(99L, entrada, ctx)).containsExactly(99L, 1L);
    }

    @Test
    void mi_pareja_va_segunda_aunque_alfabeticamente_fuese_la_ultima() {
        List<Candidato> entrada = List.of(
                c(10, "Ana", "Alonso"),
                c(20, "Zulema", "Zamora"),
                c(99, "Nadia", "Navarro"));
        ContextoOrden ctx = new ContextoOrden(20L, Set.of());

        assertThat(ids(99L, entrada, ctx)).containsExactly(99L, 20L, 10L);
    }

    @Test
    void los_hijos_registrados_van_en_bloque_3_ordenados_entre_si() {
        List<Candidato> entrada = List.of(
                c(1, "Carla", "Pérez"),
                c(2, "Bruno", "Pérez"),
                c(3, "Aaron", "Ajeno"),
                c(99, "Yo", "Mismo"));
        ContextoOrden ctx = new ContextoOrden(null, Set.of(1L, 2L));

        // Aaron Ajeno es del "resto": va detrás de los hijos aunque alfabéticamente iría primero.
        assertThat(ids(99L, entrada, ctx)).containsExactly(99L, 2L, 1L, 3L);
    }

    @Test
    void el_resto_va_alfabetico_ignorando_mayusculas_y_acentos() {
        List<Candidato> entrada = List.of(
                c(1, "ana", "perez"),
                c(2, "Álvaro", "Ruiz"),
                c(3, "Bernardo", "Blanco"));
        ContextoOrden ctx = new ContextoOrden(null, Set.of());

        // Collator es_ES: Á ≈ A y sin distinguir mayúsculas → "Álvaro" < "ana" < "Bernardo".
        assertThat(ids(7L, entrada, ctx)).containsExactly(2L, 1L, 3L);
    }

    @Test
    void sin_pareja_el_bloque_1_queda_vacio_y_se_pasa_directo_al_resto() {
        List<Candidato> entrada = List.of(
                c(1, "Bruno", "Bravo"),
                c(2, "ana", "Abad"),
                c(99, "Yo", "Yuste"));
        ContextoOrden ctx = new ContextoOrden(null, Set.of());

        assertThat(ids(99L, entrada, ctx)).containsExactly(99L, 2L, 1L);
    }
}
