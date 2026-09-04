package com.baniterio.api.push;

import java.util.ArrayList;
import java.util.List;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Envío real de push vía Firebase Cloud Messaging. Activa con
 * {@code app.push.modo=fcm}. Trocea en lotes de {@value #LOTE} (límite de FCM
 * para multicast) y devuelve los tokens que FCM marca como muertos
 * ({@code UNREGISTERED} / {@code INVALID_ARGUMENT}) para podarlos.
 */
@Component
@ConditionalOnProperty(name = "app.push.modo", havingValue = "fcm")
public class PushFirebase implements ServicioPush {

    private static final Logger log = LoggerFactory.getLogger(PushFirebase.class);
    private static final int LOTE = 500;

    private final EnvioMulticast envio;

    public PushFirebase(EnvioMulticast envio) {
        this.envio = envio;
    }

    @Override
    public List<String> enviar(List<String> tokens, String titulo, String cuerpo) {
        List<String> muertos = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += LOTE) {
            List<String> lote = tokens.subList(i, Math.min(i + LOTE, tokens.size()));
            try {
                MulticastMessage mensaje = MulticastMessage.builder()
                        .setNotification(Notification.builder().setTitle(titulo).setBody(cuerpo).build())
                        .addAllTokens(lote)
                        .build();
                BatchResponse resp = envio.enviar(mensaje);
                List<SendResponse> respuestas = resp.getResponses();
                for (int j = 0; j < respuestas.size(); j++) {
                    SendResponse r = respuestas.get(j);
                    if (esTokenMuerto(r)) {
                        muertos.add(lote.get(j));
                    } else if (!r.isSuccessful() && r.getException() != null) {
                        log.warn("[push:fcm] token …{} falló: code={} · {}",
                                sufijo(lote.get(j)), r.getException().getMessagingErrorCode(),
                                r.getException().getMessage());
                    }
                }
                log.info("[push:fcm] \"{}\" — {} enviados, {} fallidos de {} tokens",
                        titulo, resp.getSuccessCount(), resp.getFailureCount(), lote.size());
            } catch (Exception e) {
                log.error("Fallo enviando lote de push ({} tokens): {}", lote.size(), e.toString());
            }
        }
        return muertos;
    }

    /** Últimos 6 caracteres del token, para poder cruzarlo con la tabla sin loguearlo entero. */
    private static String sufijo(String token) {
        return token.length() <= 6 ? token : token.substring(token.length() - 6);
    }

    private boolean esTokenMuerto(SendResponse r) {
        if (r.isSuccessful() || r.getException() == null) {
            return false;
        }
        MessagingErrorCode codigo = r.getException().getMessagingErrorCode();
        return codigo == MessagingErrorCode.UNREGISTERED || codigo == MessagingErrorCode.INVALID_ARGUMENT;
    }
}
