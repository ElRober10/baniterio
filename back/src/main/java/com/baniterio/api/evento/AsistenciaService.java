package com.baniterio.api.evento;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.baniterio.api.auth.ServicioPermisos;
import java.util.List;

import com.baniterio.api.evento.dto.AsistenciaDetalle;
import com.baniterio.api.evento.dto.AsistenciaResumen;
import com.baniterio.api.evento.dto.FichaBebidaRequest;
import com.baniterio.api.evento.dto.FichaBebidaResponse;
import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.EstadoAsistencia;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.NotificacionEvento;
import com.baniterio.api.identidad.NotificacionEventoRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.push.Audiencia;
import com.baniterio.api.push.AvisoPushEvent;
import com.baniterio.api.push.ResolutorAudiencia;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de la asistencia a un evento (pieza 3a): responder Me apunto / No voy /
 * En duda, mandar la notificación de convocatoria, añadir gente a mano y saber
 * qué eventos tengo pendientes de contestar. El formulario de bebida y la cuota
 * (San Miguel) son la pieza 3b.
 */
@Service
public class AsistenciaService {

    private static final String TITULO_PUSH = "Eventos";
    private static final String SLUG_PENA = "baniterio";
    /** Horas que hay que esperar entre un envío de notificación y el siguiente. */
    private static final int HORAS_ENTRE_ENVIOS = 48;

    private final AsistenciaEventoRepository asistencias;
    private final NotificacionEventoRepository notificaciones;
    private final EventoRepository eventos;
    private final UsuarioRepository usuarios;
    private final PenaRepository penas;
    private final ServicioPermisos permisos;
    private final ApplicationEventPublisher publisher;
    private final ResolutorAudiencia resolutor;
    private final FichaBebidaService fichaBebida;

    public AsistenciaService(AsistenciaEventoRepository asistencias,
                             NotificacionEventoRepository notificaciones, EventoRepository eventos,
                             UsuarioRepository usuarios, PenaRepository penas, ServicioPermisos permisos,
                             ApplicationEventPublisher publisher, ResolutorAudiencia resolutor,
                             FichaBebidaService fichaBebida) {
        this.asistencias = asistencias;
        this.notificaciones = notificaciones;
        this.eventos = eventos;
        this.usuarios = usuarios;
        this.penas = penas;
        this.permisos = permisos;
        this.publisher = publisher;
        this.resolutor = resolutor;
        this.fichaBebida = fichaBebida;
    }

    /** Un administrador de verdad, o quien organiza (creó) el evento. */
    private boolean puedeGestionar(Long usuarioId, Evento e) {
        return permisos.esAdministrador(usuarioId)
                || (e.getCreadoPor() != null && e.getCreadoPor().getId().equals(usuarioId));
    }

    private Evento cargar(Long eventoId) {
        return eventos.findById(eventoId).orElseThrow(EventoNoEncontradoException::new);
    }

    /** Un evento sigue "en juego" mientras su fecha de inicio no haya pasado. */
    private static void exigirNoPasado(Evento e) {
        if (e.getFecha().isBefore(LocalDate.now())) {
            throw new EventoYaPasadoException();
        }
    }

    /**
     * Mi respuesta a un evento. Crea la fila la primera vez y la actualiza en las
     * siguientes; se puede cambiar hasta el día del evento. Evento pasado →
     * {@link EventoYaPasadoException}.
     */
    @Transactional
    public void responder(Long usuarioId, Long eventoId, EstadoAsistencia estado) {
        Evento e = cargar(eventoId);
        exigirNoPasado(e);
        AsistenciaEvento a = asistencias.findByEventoIdAndUsuarioId(eventoId, usuarioId)
                .orElseGet(() -> AsistenciaEvento.builder()
                        .evento(e)
                        .usuario(usuarios.findById(usuarioId).orElseThrow())
                        .build());
        a.setEstado(estado);
        asistencias.save(a);
    }

    /**
     * Manda (o reenvía) la notificación de convocatoria. Solo un administrador o
     * quien organiza el evento; evento pasado → {@link EventoYaPasadoException};
     * reenvío antes de 48 h desde el último envío → {@link NotificacionReenvioProntoException}.
     * Registra el envío y publica un push a {@link Audiencia.SinRespuestaEvento}
     * (toda la peña menos quien ya respondió).
     */
    @Transactional
    public void mandarNotificacion(Long usuarioId, Long eventoId, String texto) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        exigirNoPasado(e);
        notificaciones.findFirstByEventoIdOrderByEnviadaAtDesc(eventoId).ifPresent(ultima -> {
            Instant limite = Instant.now().minus(HORAS_ENTRE_ENVIOS, ChronoUnit.HOURS);
            if (ultima.getEnviadaAt().isAfter(limite)) {
                throw new NotificacionReenvioProntoException();
            }
        });
        String limpio = (texto == null || texto.isBlank()) ? null : texto.trim();
        notificaciones.save(NotificacionEvento.builder()
                .evento(e)
                .texto(limpio)
                .enviadaPor(usuarios.findById(usuarioId).orElseThrow())
                .build());
        String cuerpo = limpio != null ? limpio
                : "«" + e.getNombre() + "» — ¿te apuntas? Entra en la app y responde.";
        publisher.publishEvent(new AvisoPushEvent(
                new Audiencia.SinRespuestaEvento(eventoId), TITULO_PUSH, cuerpo));
    }

    /**
     * Añade a mano a alguien sin app (invitado, persona sin cuenta). Solo un
     * administrador o quien organiza el evento. La fila queda sin {@code usuario}
     * y con {@code registradoPor} = quien la creó.
     */
    @Transactional
    public AsistenciaResumen anadirAMano(Long usuarioId, Long eventoId, String nombre,
                                         EstadoAsistencia estado, FichaBebidaRequest ficha) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        Usuario registrador = usuarios.findById(usuarioId).orElseThrow();
        AsistenciaEvento a = asistencias.save(AsistenciaEvento.builder()
                .evento(e)
                .nombre(nombre.trim())
                .estado(estado)
                .registradoPor(registrador)
                .build());

        FichaBebidaResponse fr = null;
        boolean llevaFicha = e.getCuenta().isLlevaFichaBebida()
                && (estado == EstadoAsistencia.APUNTADO || estado == EstadoAsistencia.EN_DUDA);
        if (llevaFicha && ficha != null) {
            fr = fichaBebida.guardarAMano(usuarioId, eventoId, a, ficha);
        }
        return new AsistenciaResumen(a.getId(), nombre.trim(), a.getEstado(), true,
                fr != null ? fr.cuota() : null, fr != null ? fr.modalidad() : null);
    }

    /**
     * Quita una asistencia añadida a mano. Solo un administrador o quien organiza
     * el evento; {@link AsistenciaNoEncontradaException} si no existe en ese
     * evento; {@link AsistenciaNoManualException} si la fila es de un usuario real
     * (esas no se borran, la persona cambia su propia respuesta).
     */
    @Transactional
    public void quitarAMano(Long usuarioId, Long eventoId, Long asistenciaId) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        AsistenciaEvento a = asistencias.findByIdAndEventoId(asistenciaId, eventoId)
                .orElseThrow(AsistenciaNoEncontradaException::new);
        if (a.getUsuario() != null) {
            throw new AsistenciaNoManualException();
        }
        asistencias.delete(a);
    }

    /**
     * El bloque de asistencia que va dentro de {@code EventoDetalle} para el
     * usuario que pregunta: su respuesta, si puede mandar la notificación, cuándo
     * se puede reenviar y los recuentos. {@code sinContestar} reutiliza la misma
     * audiencia del push ({@link Audiencia.SinRespuestaEvento}).
     */
    @Transactional(readOnly = true)
    public AsistenciaDetalle detalleDe(Long usuarioId, Evento e) {
        Long eventoId = e.getId();
        String miAsistencia = asistencias.findByEventoIdAndUsuarioId(eventoId, usuarioId)
                .map(a -> a.getEstado().name()).orElse(null);
        boolean puedeNotificar = puedeGestionar(usuarioId, e)
                && !e.getFecha().isBefore(LocalDate.now());
        Instant reenviableAt = notificaciones.findFirstByEventoIdOrderByEnviadaAtDesc(eventoId)
                .map(n -> n.getEnviadaAt().plus(HORAS_ENTRE_ENVIOS, ChronoUnit.HOURS))
                .orElse(null);
        int apuntados = (int) asistencias.countByEventoIdAndEstado(eventoId, EstadoAsistencia.APUNTADO);
        int noVoy = (int) asistencias.countByEventoIdAndEstado(eventoId, EstadoAsistencia.NO_VOY);
        int enDuda = (int) asistencias.countByEventoIdAndEstado(eventoId, EstadoAsistencia.EN_DUDA);
        int sinContestar = resolutor.resolver(new Audiencia.SinRespuestaEvento(eventoId)).size();
        return new AsistenciaDetalle(miAsistencia, puedeNotificar, reenviableAt,
                apuntados, noVoy, enDuda, sinContestar, fichaBebida.detalleDe(usuarioId, e));
    }

    /**
     * Eventos que el usuario tiene pendientes de contestar (hay notificación y no
     * ha respondido), ordenados por fecha ascendente. La pantalla bloqueante los
     * recorre uno a uno.
     */
    @Transactional(readOnly = true)
    public List<Evento> pendientesRespuesta(Long usuarioId) {
        Long penaId = penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
        return eventos.pendientesRespuesta(penaId, usuarioId, LocalDate.now());
    }
}
