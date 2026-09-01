package com.baniterio.api.perfil;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorTelefonoTest {

    @Test
    void deja_pasar_un_movil_espanol_de_9_digitos() {
        assertThat(NormalizadorTelefono.normalizar("612345678")).contains("612345678");
        assertThat(NormalizadorTelefono.normalizar("712345678")).contains("712345678");
    }

    @Test
    void quita_prefijo_y_separadores() {
        assertThat(NormalizadorTelefono.normalizar("+34 612 345 678")).contains("612345678");
        assertThat(NormalizadorTelefono.normalizar("0034-612-345-678")).contains("612345678");
        assertThat(NormalizadorTelefono.normalizar("(612) 345 678")).contains("612345678");
    }

    @Test
    void rechaza_lo_que_no_es_un_movil_espanol() {
        assertThat(NormalizadorTelefono.normalizar("512345678")).isEmpty();     // no empieza por 6/7
        assertThat(NormalizadorTelefono.normalizar("61234567")).isEmpty();      // 8 dígitos
        assertThat(NormalizadorTelefono.normalizar("6123456789")).isEmpty();    // 10 dígitos
        assertThat(NormalizadorTelefono.normalizar("+33612345678")).isEmpty();  // prefijo no ES
        assertThat(NormalizadorTelefono.normalizar("")).isEmpty();
        assertThat(NormalizadorTelefono.normalizar(null)).isEmpty();
    }

    @Test
    void no_confunde_un_6_inicial_legitimo_con_un_prefijo() {
        // "34" en medio no se toca; solo el prefijo al principio
        assertThat(NormalizadorTelefono.normalizar("634512678")).contains("634512678");
    }
}
