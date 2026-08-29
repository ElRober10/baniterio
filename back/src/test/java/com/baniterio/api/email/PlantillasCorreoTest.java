package com.baniterio.api.email;

import org.junit.jupiter.api.Test;

import com.baniterio.api.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitario puro (sin contexto de Spring) de {@link PlantillasCorreo}. Comprueba
 * que cada plantilla mete el nombre de la persona y la información clave (cómo iniciar
 * sesión, cómo completar el registro, el motivo del rechazo) en el cuerpo del correo.
 */
class PlantillasCorreoTest {

    private final PlantillasCorreo plantillas = new PlantillasCorreo(new AppProperties(
            null, null, null,
            new AppProperties.Email("log", "Bañiterio <no-reply@baniterio.local>",
                    "http://localhost:4200/registro")));

    @Test
    void aprobacion_cuenta_creada_incluye_el_nombre_y_habla_de_iniciar_sesion() {
        var c = plantillas.aprobacionCuentaCreada("Marta");
        assertThat(c.asunto()).isNotBlank();
        assertThat(c.cuerpo()).contains("Marta").containsIgnoringCase("iniciar sesión");
    }

    @Test
    void aprobacion_completa_registro_incluye_indicacion_de_registrarse() {
        var c = plantillas.aprobacionCompletaRegistro("Luis");
        assertThat(c.cuerpo()).contains("Luis").containsIgnoringCase("registr");
    }

    @Test
    void rechazo_incluye_el_motivo() {
        var c = plantillas.rechazo("Ana", "No hemos podido confirmar tu vinculación.");
        assertThat(c.cuerpo()).contains("Ana").contains("No hemos podido confirmar tu vinculación.");
    }
}
