package com.baniterio.api.evento;

import java.time.LocalDate;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.evento.dto.EventoDetalle;
import com.baniterio.api.evento.dto.EventoResumen;
import com.baniterio.api.evento.dto.ListaEventosResponse;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.SolicitudEventoRepository;
import com.baniterio.api.identidad.TipoSolicitudEvento;
import com.baniterio.api.identidad.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de la sección Eventos: listado paginado, detalle y (en tareas
 * siguientes) crear/editar/borrar. La peña se resuelve por slug como en
 * {@code AdminService}. "Administrador de verdad" = {@link ServicioPermisos#esAdministrador}.
 */
@Service
public class EventoService {

    static final int PAGINA = 8;
    private static final String SLUG_PENA = "baniterio";
    private static final Sort ORDEN = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));

    private final EventoRepository eventos;
    private final SolicitudEventoRepository solicitudes;
    private final ServicioPermisos permisos;
    private final PenaRepository penas;

    public EventoService(EventoRepository eventos, SolicitudEventoRepository solicitudes,
                         ServicioPermisos permisos, PenaRepository penas) {
        this.eventos = eventos;
        this.solicitudes = solicitudes;
        this.permisos = permisos;
        this.penas = penas;
    }

    Long penaId() {
        return penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
    }

    private static boolean esPasado(Evento e) {
        LocalDate limite = e.getFechaFin() != null ? e.getFechaFin() : e.getFecha();
        return limite.isBefore(LocalDate.now());
    }

    Evento cargar(Long eventoId) {
        return eventos.findById(eventoId).orElseThrow(EventoNoEncontradoException::new);
    }

    /** Tiene un crédito CREAR aprobado sin consumir, o es administrador. */
    boolean puedeCrear(Long usuarioId) {
        return permisos.esAdministrador(usuarioId)
                || solicitudes.findFirstBySolicitanteIdAndTipoAndEstadoAndEventoIsNullOrderByIdAsc(
                        usuarioId, TipoSolicitudEvento.CREAR, EstadoSolicitud.APROBADA).isPresent();
    }

    private boolean puedeSolicitar(Long usuarioId) {
        if (permisos.esAdministrador(usuarioId) || puedeCrear(usuarioId)) {
            return false;
        }
        return !solicitudes.existsBySolicitanteIdAndTipoAndEstado(
                usuarioId, TipoSolicitudEvento.CREAR, EstadoSolicitud.PENDIENTE);
    }

    boolean puedeGestionar(Long usuarioId, Evento e) {
        return permisos.esAdministrador(usuarioId)
                || (e.getCreadoPor() != null && e.getCreadoPor().getId().equals(usuarioId));
    }

    @Transactional(readOnly = true)
    public ListaEventosResponse listar(Long usuarioId, int pagina) {
        Page<Evento> p = eventos.findByPenaId(penaId(),
                PageRequest.of(Math.max(pagina, 0), PAGINA, ORDEN));
        var resumenes = p.getContent().stream()
                .map(e -> new EventoResumen(e.getId(), e.getNombre(), e.getFecha(), e.getFechaFin(),
                        e.getLugar(), esPasado(e)))
                .toList();
        return new ListaEventosResponse(resumenes, p.getNumber(), p.getTotalPages(),
                puedeCrear(usuarioId), puedeSolicitar(usuarioId));
    }

    @Transactional(readOnly = true)
    public EventoDetalle detalle(Long usuarioId, Long eventoId) {
        return aDetalle(usuarioId, cargar(eventoId));
    }

    EventoDetalle aDetalle(Long usuarioId, Evento e) {
        Usuario creador = e.getCreadoPor();
        var creadoPor = creador == null ? null
                : new EventoDetalle.CreadoPor(creador.getId(), creador.getNombre());
        boolean gestiona = puedeGestionar(usuarioId, e);
        boolean borradoPendiente = solicitudes.existsByEventoIdAndTipoAndEstado(
                e.getId(), TipoSolicitudEvento.BORRAR, EstadoSolicitud.PENDIENTE);
        return new EventoDetalle(e.getId(), e.getNombre(), e.getDescripcion(), e.getLugar(),
                e.getFecha(), e.getFechaFin(), esPasado(e), creadoPor, gestiona, gestiona,
                borradoPendiente);
    }
}
