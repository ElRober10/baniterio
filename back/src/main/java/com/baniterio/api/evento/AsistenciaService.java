package com.baniterio.api.evento;

import java.time.LocalDate;

import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.EstadoAsistencia;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.UsuarioRepository;
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

    private final AsistenciaEventoRepository asistencias;
    private final EventoRepository eventos;
    private final UsuarioRepository usuarios;

    public AsistenciaService(AsistenciaEventoRepository asistencias, EventoRepository eventos,
                             UsuarioRepository usuarios) {
        this.asistencias = asistencias;
        this.eventos = eventos;
        this.usuarios = usuarios;
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
}
