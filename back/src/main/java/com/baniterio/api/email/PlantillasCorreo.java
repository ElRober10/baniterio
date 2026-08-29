package com.baniterio.api.email;

import com.baniterio.api.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Construye el asunto y el cuerpo (texto plano, en español y con tono cercano de
 * peña) de los correos que la aplicación manda a quien solicita entrar en la
 * Bañiterio. No envía nada: solo devuelve el texto en un {@link Correo}; del envío
 * se encarga {@link ServicioEmail}.
 *
 * <p>Necesita {@link AppProperties} para saber a qué URL del front mandar a la
 * persona (registro / inicio de sesión).
 */
@Component
public class PlantillasCorreo {

    private final String enlaceRegistro;
    private final String enlaceLogin;

    public PlantillasCorreo(AppProperties props) {
        this.enlaceRegistro = props.email().enlaceRegistro();
        this.enlaceLogin = derivarEnlaceLogin(this.enlaceRegistro);
    }

    /**
     * Correo cuando se aprueba la solicitud y además ya le hemos creado el usuario:
     * solo tiene que iniciar sesión con su teléfono y su contraseña.
     */
    public Correo aprobacionCuentaCreada(String nombre) {
        String cuerpo = """
                ¡Hola %s!

                Tu solicitud para entrar en la Bañiterio ha sido aprobada. Ya te hemos
                creado el usuario: entra con tu número de teléfono y la contraseña que
                pusiste al solicitar el acceso.

                Puedes iniciar sesión aquí: %s

                ¡Nos vemos en la peña!
                """.formatted(nombre, enlaceLogin);
        return new Correo("Tu acceso a la Bañiterio: aprobado", cuerpo);
    }

    /**
     * Correo cuando se aprueba la solicitud pero la persona todavía no tiene cuenta:
     * tiene que ir al front y completar el registro con su teléfono.
     */
    public Correo aprobacionCompletaRegistro(String nombre) {
        String cuerpo = """
                ¡Hola %s!

                Tu solicitud para entrar en la Bañiterio ha sido aprobada. Para terminar,
                entra en %s y crea tu cuenta con el mismo número de teléfono con el que
                pediste el acceso.

                ¡Nos vemos en la peña!
                """.formatted(nombre, enlaceRegistro);
        return new Correo("Tu acceso a la Bañiterio: aprobado", cuerpo);
    }

    /**
     * Correo cuando se rechaza la solicitud. Incluye el {@code motivo} literal que
     * ha escrito quien administra, para que la persona sepa por qué.
     */
    public Correo rechazo(String nombre, String motivo) {
        String cuerpo = """
                Hola %s:

                Hemos revisado tu solicitud de acceso a la Bañiterio y de momento no
                podemos darte de alta. Motivo:

                %s

                Si crees que es un error, responde a este correo y lo miramos.
                """.formatted(nombre, motivo);
        return new Correo("Sobre tu solicitud de acceso a la Bañiterio", cuerpo);
    }

    /**
     * De {@code http://host/registro} saca {@code http://host/login}. Si el enlace no
     * acaba en {@code /registro}, se devuelve tal cual (mejor un enlace raro que uno roto).
     */
    private static String derivarEnlaceLogin(String enlaceRegistro) {
        if (enlaceRegistro != null && enlaceRegistro.endsWith("/registro")) {
            return enlaceRegistro.substring(0, enlaceRegistro.length() - "/registro".length()) + "/login";
        }
        return enlaceRegistro;
    }

    /**
     * Asunto y cuerpo ya montados de un correo, listos para pasar a
     * {@link ServicioEmail#enviar}.
     */
    public record Correo(String asunto, String cuerpo) {
    }
}
