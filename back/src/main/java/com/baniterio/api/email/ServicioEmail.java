package com.baniterio.api.email;

/**
 * Envío de un correo de texto plano. Es una abstracción a propósito minúscula:
 * quien la usa no sabe (ni le importa) si el correo sale de verdad o solo se
 * escribe en el log.
 *
 * <p>Hay dos implementaciones y Spring elige una según {@code app.email.modo}:
 * <ul>
 *   <li>{@link EmailLog} — por defecto; solo escribe el correo en el log. Ideal
 *       para desarrollo y para los tests (no hace falta un servidor SMTP).
 *   <li>{@link EmailSmtp} — envía de verdad por SMTP (necesita {@code spring.mail.*}).
 * </ul>
 */
public interface ServicioEmail {

    /**
     * @param destinatario dirección de correo de quien recibe
     * @param asunto       asunto del correo
     * @param cuerpo       cuerpo en texto plano
     */
    void enviar(String destinatario, String asunto, String cuerpo);
}
