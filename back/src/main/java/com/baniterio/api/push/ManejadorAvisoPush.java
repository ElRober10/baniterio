package com.baniterio.api.push;

import java.util.List;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduce un {@link AvisoPushEvent} en envíos de push, DESPUÉS de que la
 * transacción que lo publicó confirme (igual patrón que
 * {@code ManejadorCorreoSolicitud}): así un fallo de push no revierte la
 * operación de dominio. Nunca propaga excepción.
 */
@Component
public class ManejadorAvisoPush {

    private static final Logger log = LoggerFactory.getLogger(ManejadorAvisoPush.class);

    private final ResolutorAudiencia resolutor;
    private final DispositivoRepository dispositivos;
    private final ServicioPush servicioPush;
    private final DispositivoService dispositivoService;

    public ManejadorAvisoPush(ResolutorAudiencia resolutor, DispositivoRepository dispositivos,
                              ServicioPush servicioPush, DispositivoService dispositivoService) {
        this.resolutor = resolutor;
        this.dispositivos = dispositivos;
        this.servicioPush = servicioPush;
        this.dispositivoService = dispositivoService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alAviso(AvisoPushEvent e) {
        try {
            List<Long> usuarios = resolutor.resolver(e.audiencia());
            List<Dispositivo> disp = dispositivos.findByUsuarioIdIn(usuarios);
            if (disp.isEmpty()) {
                return;
            }
            List<String> tokens = disp.stream().map(Dispositivo::getToken).toList();
            List<String> muertos = servicioPush.enviar(tokens, e.titulo(), e.cuerpo());
            if (!muertos.isEmpty()) {
                dispositivoService.podar(muertos);
            }
        } catch (Exception ex) {
            log.error("No se pudo enviar el aviso push \"{}\": {}", e.titulo(), ex.toString());
        }
    }
}
