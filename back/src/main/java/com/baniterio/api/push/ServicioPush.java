package com.baniterio.api.push;

import java.util.List;

/**
 * Envía una notificación push a una lista de tokens de dispositivo.
 *
 * <p>Implementación según {@code app.push.modo}: {@link PushEnLog} (por defecto)
 * o {@link PushFirebase} ({@code fcm}).
 *
 * <p>Contrato: NUNCA lanza — un fallo de envío se registra, no revienta el flujo
 * que lo disparó. Devuelve los tokens que el proveedor ha rechazado por estar
 * muertos (desinstalados / caducados), para que quien llama los borre.
 */
public interface ServicioPush {

    List<String> enviar(List<String> tokens, String titulo, String cuerpo);
}
