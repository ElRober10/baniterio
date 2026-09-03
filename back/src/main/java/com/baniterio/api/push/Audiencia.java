package com.baniterio.api.push;

/**
 * A quién va dirigido un {@link AvisoPushEvent}. Sellada: hoy solo se usa
 * {@link Administradores} (la solicitud de acceso), pero las otras dos quedan
 * listas para los avisos que vengan (pagos, mensajes a toda la peña, etc.).
 */
public sealed interface Audiencia {

    /** Una sola persona. */
    record UsuarioUnico(Long usuarioId) implements Audiencia {}

    /** Todos los miembros activos de la peña. */
    record TodaLaPena() implements Audiencia {}

    /** Administradores y superadministradores activos de la peña. */
    record Administradores() implements Audiencia {}

    /**
     * Miembros activos de la peña que aún no han respondido a la convocatoria de
     * un evento. Los que dijeron "No voy" ya tienen respuesta → no reciben nada.
     */
    record SinRespuestaEvento(Long eventoId) implements Audiencia {}
}
