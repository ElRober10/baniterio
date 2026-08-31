package com.baniterio.api.push;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PushEnLogTest {

    private final PushEnLog push = new PushEnLog();

    @Test
    void no_lanza_y_no_devuelve_tokens_muertos() {
        assertThatCode(() -> push.enviar(List.of("a", "b"), "T", "C")).doesNotThrowAnyException();
        assertThat(push.enviar(List.of("a", "b"), "T", "C")).isEmpty();
    }

    @Test
    void tolera_lista_vacia() {
        assertThat(push.enviar(List.of(), "T", "C")).isEmpty();
    }
}
