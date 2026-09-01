package com.baniterio.api.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;

/**
 * Lo único que {@link PushFirebase} necesita de Firebase: enviar un mensaje a
 * varios tokens. Aislarlo en una interfaz permite testear {@code PushFirebase}
 * con un doble, sin inicializar un {@code FirebaseApp} real.
 */
@FunctionalInterface
public interface EnvioMulticast {

    BatchResponse enviar(MulticastMessage mensaje) throws FirebaseMessagingException;
}
