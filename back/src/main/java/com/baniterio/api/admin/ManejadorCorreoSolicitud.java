package com.baniterio.api.admin;

import com.baniterio.api.email.PlantillasCorreo;
import com.baniterio.api.email.ServicioEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía el correo de aprobación/rechazo DESPUÉS de que la transacción de
 * {@link AdminService} confirme. Si el envío falla, se registra a nivel
 * {@code ERROR} pero NO se revierte la resolución de la solicitud (ya está
 * confirmada en BBDD).
 */
@Component
public class ManejadorCorreoSolicitud {

    private static final Logger log = LoggerFactory.getLogger(ManejadorCorreoSolicitud.class);

    private final ServicioEmail email;
    private final PlantillasCorreo plantillas;

    public ManejadorCorreoSolicitud(ServicioEmail email, PlantillasCorreo plantillas) {
        this.email = email;
        this.plantillas = plantillas;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alResolverSolicitud(SolicitudResueltaEvent e) {
        try {
            PlantillasCorreo.Correo correo;
            if (!e.aprobada()) {
                correo = plantillas.rechazo(e.nombre(), e.motivoRechazo());
            } else if (e.cuentaCreada()) {
                correo = plantillas.aprobacionCuentaCreada(e.nombre());
            } else {
                correo = plantillas.aprobacionCompletaRegistro(e.nombre());
            }
            email.enviar(e.email(), correo.asunto(), correo.cuerpo());
        } catch (Exception ex) {
            log.error("No se pudo enviar el correo de la solicitud a {}: {}", e.email(), ex.toString());
        }
    }
}
