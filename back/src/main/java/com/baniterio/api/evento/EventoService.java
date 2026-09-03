package com.baniterio.api.evento;

import java.time.Instant;
import java.time.LocalDate;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.evento.dto.EventoDetalle;
import com.baniterio.api.evento.dto.EventoResumen;
import com.baniterio.api.evento.dto.GuardarEventoRequest;
import com.baniterio.api.evento.dto.ListaEventosResponse;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.SolicitudEvento;
import com.baniterio.api.identidad.SolicitudEventoRepository;
import com.baniterio.api.identidad.TipoSolicitudEvento;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.push.Audiencia;
import com.baniterio.api.push.AvisoPushEvent;
import org.springframework.context.ApplicationEventPublisher;
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
    private static final String TITULO_PUSH = "Eventos";
    private static final Sort ORDEN = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));

    private final EventoRepository eventos;
    private final SolicitudEventoRepository solicitudes;
    private final ServicioPermisos permisos;
    private final PenaRepository penas;
    private final UsuarioRepository usuarios;
    private final ApplicationEventPublisher publisher;

    public EventoService(EventoRepository eventos, SolicitudEventoRepository solicitudes,
                         ServicioPermisos permisos, PenaRepository penas,
                         UsuarioRepository usuarios, ApplicationEventPublisher publisher) {
        this.eventos = eventos;
        this.solicitudes = solicitudes;
        this.permisos = permisos;
        this.penas = penas;
        this.usuarios = usuarios;
        this.publisher = publisher;
    }

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
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

    /**
     * Crea un evento. Un administrador lo crea directo; un miembro normal necesita
     * un crédito CREAR aprobado sin consumir, que al crear el evento queda
     * consumido (se le engancha el evento). Sin crédito → {@link SinCreditoEventoException}.
     */
    @Transactional
    public EventoDetalle crear(Long usuarioId, GuardarEventoRequest req) {
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        boolean admin = permisos.esAdministrador(usuarioId);

        SolicitudEvento credito = null;
        if (!admin) {
            credito = solicitudes.findFirstBySolicitanteIdAndTipoAndEstadoAndEventoIsNullOrderByIdAsc(
                    usuarioId, TipoSolicitudEvento.CREAR, EstadoSolicitud.APROBADA)
                    .orElseThrow(SinCreditoEventoException::new);
        }

        Evento e = eventos.save(Evento.builder()
                .pena(penas.findBySlug(SLUG_PENA).orElseThrow())
                .nombre(req.nombre().trim())
                .descripcion(vacioANull(req.descripcion()))
                .lugar(vacioANull(req.lugar()))
                .fecha(req.fecha())
                .fechaFin(req.fechaFin())
                .creadoPor(usuario)
                .build());

        if (credito != null) {
            credito.setEvento(e);
            solicitudes.save(credito);
        }
        return aDetalle(usuarioId, e);
    }

    /** Edita un evento. Solo el administrador o quien lo creó → si no, {@link SinPermisoEventoException}. */
    @Transactional
    public EventoDetalle editar(Long usuarioId, Long eventoId, GuardarEventoRequest req) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        e.setNombre(req.nombre().trim());
        e.setDescripcion(vacioANull(req.descripcion()));
        e.setLugar(vacioANull(req.lugar()));
        e.setFecha(req.fecha());
        e.setFechaFin(req.fechaFin());
        eventos.save(e);
        return aDetalle(usuarioId, e);
    }

    /**
     * Un administrador borra el evento directo (204). Un miembro que lo creó, si
     * no es admin, no lo borra: se crea una {@code solicitud_evento} BORRAR y se
     * avisa por push a los administradores (202). Si ya hay una BORRAR pendiente
     * para ese evento → 409. Cualquier otro → 403.
     */
    @Transactional
    public ResultadoBorrado borrar(Long usuarioId, Long eventoId) {
        Evento e = cargar(eventoId);
        boolean admin = permisos.esAdministrador(usuarioId);

        if (admin) {
            solicitudes.findByEventoIdAndTipoAndEstado(eventoId, TipoSolicitudEvento.BORRAR,
                    EstadoSolicitud.PENDIENTE).ifPresent(sol -> {
                sol.setEstado(EstadoSolicitud.APROBADA);
                sol.setResueltaPor(usuarios.findById(usuarioId).orElseThrow());
                sol.setResueltaAt(Instant.now());
                sol.setEvento(null);
                solicitudes.save(sol);
                publisher.publishEvent(new AvisoPushEvent(
                        new Audiencia.UsuarioUnico(sol.getSolicitante().getId()),
                        TITULO_PUSH, "Se ha borrado el evento «" + e.getNombre() + "»."));
            });
            eventos.delete(e);
            return ResultadoBorrado.BORRADO;
        }

        if (e.getCreadoPor() != null && e.getCreadoPor().getId().equals(usuarioId)) {
            if (solicitudes.existsByEventoIdAndTipoAndEstado(eventoId, TipoSolicitudEvento.BORRAR,
                    EstadoSolicitud.PENDIENTE)) {
                throw new SolicitudEventoConflictoException("SOLICITUD_EVENTO_YA_PENDIENTE");
            }
            solicitudes.save(SolicitudEvento.builder()
                    .pena(e.getPena())
                    .solicitante(usuarios.findById(usuarioId).orElseThrow())
                    .tipo(TipoSolicitudEvento.BORRAR)
                    .evento(e)
                    .estado(EstadoSolicitud.PENDIENTE)
                    .build());
            publisher.publishEvent(new AvisoPushEvent(new Audiencia.Administradores(),
                    TITULO_PUSH, "Alguien quiere borrar el evento «" + e.getNombre() + "»."));
            return ResultadoBorrado.SOLICITUD_CREADA;
        }

        throw new SinPermisoEventoException();
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
