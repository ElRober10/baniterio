package com.baniterio.api.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link ServicioEmail} para desarrollo y tests: no envía nada,
 * solo escribe el correo en el log a nivel {@code INFO}. Así se puede ver qué se
 * habría enviado sin montar un servidor SMTP.
 *
 * <p>Es el bean activo cuando {@code app.email.modo} vale {@code "log"} o no está
 * definido ({@code matchIfMissing = true}), que es el caso por defecto y el de la
 * suite de tests.
 */
@Component
@ConditionalOnProperty(name = "app.email.modo", havingValue = "log", matchIfMissing = true)
public class EmailLog implements ServicioEmail {

    private static final Logger log = LoggerFactory.getLogger(EmailLog.class);

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        log.info("[EMAIL:log] para={} asunto={}\n{}", destinatario, asunto, cuerpo);
    }
}
