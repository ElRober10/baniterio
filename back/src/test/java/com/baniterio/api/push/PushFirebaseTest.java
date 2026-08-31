package com.baniterio.api.push;

import java.util.List;
import java.util.stream.IntStream;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushFirebaseTest {

    private SendResponse ok() {
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(true);
        return r;
    }

    private SendResponse fallo(MessagingErrorCode codigo) {
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(false);
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(codigo);
        when(r.getException()).thenReturn(ex);
        return r;
    }

    @Test
    void devuelve_los_tokens_con_error_unregistered_o_invalid_argument() throws Exception {
        // Se construyen los dobles ANTES de abrir el when(): stubbear un mock
        // dentro del thenReturn() de otro deja el primero a medio configurar.
        List<SendResponse> respuestas = List.of(
                ok(), fallo(MessagingErrorCode.UNREGISTERED),
                fallo(MessagingErrorCode.INVALID_ARGUMENT), fallo(MessagingErrorCode.INTERNAL));
        BatchResponse resp = mock(BatchResponse.class);
        when(resp.getResponses()).thenReturn(respuestas);
        EnvioMulticast envio = m -> resp;
        PushFirebase push = new PushFirebase(envio);

        List<String> muertos = push.enviar(List.of("a", "b", "c", "d"), "T", "C");

        assertThat(muertos).containsExactlyInAnyOrder("b", "c");
    }

    @Test
    void trocea_en_lotes_de_500() throws Exception {
        int[] llamadas = {0};
        EnvioMulticast envio = m -> {
            llamadas[0]++;
            BatchResponse r = mock(BatchResponse.class);
            when(r.getResponses()).thenReturn(List.of());
            return r;
        };
        PushFirebase push = new PushFirebase(envio);
        List<String> tokens = IntStream.range(0, 1200).mapToObj(i -> "t" + i).toList();

        push.enviar(tokens, "T", "C");

        assertThat(llamadas[0]).isEqualTo(3); // 500 + 500 + 200
    }

    @Test
    void un_fallo_de_firebase_no_propaga_excepcion() {
        EnvioMulticast envio = m -> { throw mock(FirebaseMessagingException.class); };
        PushFirebase push = new PushFirebase(envio);

        assertThat(push.enviar(List.of("a"), "T", "C")).isEmpty();
    }
}
