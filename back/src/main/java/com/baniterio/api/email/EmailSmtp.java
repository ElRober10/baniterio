package com.baniterio.api.email;

import com.baniterio.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link ServicioEmail} que envía de verdad por SMTP usando el
 * {@link JavaMailSender} que autoconfigura Spring Boot a partir de
 * {@code spring.mail.*} (host, usuario, contraseña...).
 *
 * <p>Es el bean activo cuando {@code app.email.modo} vale {@code "smtp"}. Si en ese
 * modo no hay {@code spring.mail.host} configurado, no existe {@code JavaMailSender}
 * y el arranque falla con un error claro de dependencia que no se puede satisfacer:
 * es configuración incorrecta (modo smtp sin SMTP) y es un fallo aceptable.
 */
@Component
@ConditionalOnProperty(name = "app.email.modo", havingValue = "smtp")
public class EmailSmtp implements ServicioEmail {

    private static final Logger log = LoggerFactory.getLogger(EmailSmtp.class);

    private final JavaMailSender mailSender;
    private final AppProperties props;

    public EmailSmtp(JavaMailSender mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(props.email().from());
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        mailSender.send(mensaje);
        log.info("[EMAIL:smtp] enviado para={} asunto={}", destinatario, asunto);
    }
}
