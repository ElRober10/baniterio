package com.baniterio.api.evento;

/**
 * Se intenta reenviar la notificación de un evento antes de que pasen 48 h desde
 * el último envío. La traduce {@code ApiExceptionHandler} a 409
 * {@code NOTIFICACION_REENVIO_PRONTO}.
 */
public class NotificacionReenvioProntoException extends RuntimeException {
}
