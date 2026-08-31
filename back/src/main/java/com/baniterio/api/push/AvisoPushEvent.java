package com.baniterio.api.push;

/**
 * Evento de dominio: "ha pasado algo que hay que avisar por push". Lo publican
 * los servicios (hoy {@code AuthService} al crear una solicitud de acceso) y lo
 * consume {@link ManejadorAvisoPush} tras confirmar la transacción, de modo que
 * un fallo de envío no revierta la operación que lo disparó.
 */
public record AvisoPushEvent(Audiencia audiencia, String titulo, String cuerpo) {
}
