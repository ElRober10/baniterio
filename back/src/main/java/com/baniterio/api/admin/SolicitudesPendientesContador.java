package com.baniterio.api.admin;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.SolicitudIngresoRepository;
import org.springframework.stereotype.Component;

/**
 * Pendientes del área {@code ADMIN_SOLICITUDES}: solicitudes de ingreso a la
 * peña que aún no se han aprobado ni rechazado.
 */
@Component
public class SolicitudesPendientesContador implements ContadorPendientes {

    private final SolicitudIngresoRepository solicitudes;

    public SolicitudesPendientesContador(SolicitudIngresoRepository solicitudes) {
        this.solicitudes = solicitudes;
    }

    @Override
    public AreaProtegida area() {
        return AreaProtegida.ADMIN_SOLICITUDES;
    }

    @Override
    public long contar(Long penaId) {
        return solicitudes.countByPenaIdAndEstado(penaId, EstadoSolicitud.PENDIENTE);
    }
}
