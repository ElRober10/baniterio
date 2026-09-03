package com.baniterio.api.evento;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.EstadoAsistencia;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.NotificacionEvento;
import com.baniterio.api.identidad.NotificacionEventoRepository;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.push.Audiencia;
import com.baniterio.api.push.AvisoPushEvent;
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
    /** Horas que hay que esperar entre un envío de notificación y el siguiente. */
    private static final int HORAS_ENTRE_ENVIOS = 48;

    private final AsistenciaEventoRepository asistencias;
    private final NotificacionEventoRepository notificaciones;
    private final EventoRepository eventos;
    private final UsuarioRepository usuarios;
    private final ServicioPermisos permisos;
    private final ApplicationEventPublisher publisher;

    public AsistenciaService(AsistenciaEventoRepository asistencias,
                             NotificacionEventoRepository notificaciones, EventoRepository eventos,
                             UsuarioRepository usuarios, ServicioPermisos permisos,
                             ApplicationEventPublisher publisher) {
        this.asistencias = asistencias;
        this.notificaciones = notificaciones;
        this.eventos = eventos;
        this.usuarios = usuarios;
        this.permisos = permisos;
        this.publisher = publisher;
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
}
