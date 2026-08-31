package com.baniterio.api.push;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementación por defecto de {@link ServicioPush}: no envía nada, solo deja
 * traza. Activa salvo que {@code app.push.modo=fcm}. Permite desarrollar y
 * testear sin credenciales de Firebase.
 */
@Component
@ConditionalOnProperty(name = "app.push.modo", havingValue = "log", matchIfMissing = true)
public class PushEnLog implements ServicioPush {

    private static final Logger log = LoggerFactory.getLogger(PushEnLog.class);

    @Override
    public List<String> enviar(List<String> tokens, String titulo, String cuerpo) {
        log.info("[push:log] {} dispositivo(s) · \"{}\" — \"{}\"", tokens.size(), titulo, cuerpo);
        return List.of();
    }
}
