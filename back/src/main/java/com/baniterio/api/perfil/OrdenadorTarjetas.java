package com.baniterio.api.perfil;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ordena las tarjetas de {@code GET /miembros} de forma útil para el usuario que
 * mira: primero la suya, luego la de su pareja, luego las de sus hijos ya
 * registrados y por último el resto de la peña. Dentro de cada bloque, orden
 * alfabético por {@code nombre + " " + apellidos} con un {@link Collator} español
 * a fuerza {@code SECONDARY} (ignora mayúsculas/minúsculas pero respeta acentos;
 * "Álvaro" y "ana" quedan casi empatados y mandan las letras siguientes).
 *
 * <p>Función pura y sin estado: toda la información de contexto (quién es mi
 * pareja, qué hijos míos tienen cuenta) llega en {@link ContextoOrden}, para
 * poder probarla sin base de datos.
 */
public final class OrdenadorTarjetas {

    private OrdenadorTarjetas() {
    }

    /** Un miembro a ordenar. Solo lo que el orden necesita: id y nombre para el desempate. */
    public record Candidato(Long id, String nombre, String apellidos) {
    }

    /**
     * Contexto del usuario que pide la lista.
     *
     * @param parejaUsuarioId    id del otro lado de un vínculo {@code ACEPTADO}, o
     *                           {@code null} si no tiene pareja con cuenta.
     * @param hijosRegistradosIds ids de usuario de los hijos suyos (o de su
     *                           vínculo aceptado) que ya tienen cuenta.
     */
    public record ContextoOrden(Long parejaUsuarioId, Set<Long> hijosRegistradosIds) {
    }

    public static List<Candidato> ordenar(Long yoId, List<Candidato> candidatos, ContextoOrden ctx) {
        Collator collator = Collator.getInstance(new Locale("es", "ES"));
        collator.setStrength(Collator.SECONDARY);

        Comparator<Candidato> comparador = Comparator
                .comparingInt((Candidato c) -> grupo(c, yoId, ctx))
                .thenComparing(OrdenadorTarjetas::clave, collator);

        return candidatos.stream().sorted(comparador).toList();
    }

    private static int grupo(Candidato c, Long yoId, ContextoOrden ctx) {
        if (c.id().equals(yoId)) {
            return 0;
        }
        if (ctx.parejaUsuarioId() != null && c.id().equals(ctx.parejaUsuarioId())) {
            return 1;
        }
        if (ctx.hijosRegistradosIds().contains(c.id())) {
            return 2;
        }
        return 3;
    }

    private static String clave(Candidato c) {
        return (c.nombre() + " " + c.apellidos()).trim();
    }
}
