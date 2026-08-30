package com.baniterio.api.admin;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.SolicitudIngresoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pendientes del área {@code ADMIN_SOLICITUDES}: solicitudes de ingreso a la
 * peña que aún no se han aprobado ni rechazado.
 */
@Component
public class SolicitudesPendientesContador implements ContadorPendientes {

    private static final String SLUG_PENA = "baniterio";

    private final SolicitudIngresoRepository solicitudes;
    private final PenaRepository penas;

    public SolicitudesPendientesContador(SolicitudIngresoRepository solicitudes, PenaRepository penas) {
        this.solicitudes = solicitudes;
        this.penas = penas;
    }

    @Override
    public AreaProtegida area() {
        return AreaProtegida.ADMIN_SOLICITUDES;
    }

    @Override
    @Transactional(readOnly = true)
    public long contar() {
        Long penaId = penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
        return solicitudes.countByPenaIdAndEstado(penaId, EstadoSolicitud.PENDIENTE);
    }
}
