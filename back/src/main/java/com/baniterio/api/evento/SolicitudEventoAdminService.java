package com.baniterio.api.evento;

import java.time.Instant;
import java.util.List;

import com.baniterio.api.admin.SinPermisoException;
import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.evento.dto.SolicitudEventoResumen;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Bloque del panel de administración para las solicitudes de evento. Solo un
 * administrador de verdad (rol ADMIN o superadmin) puede listar y resolver; un
 * área concedida NO basta. El push al solicitante se publica dentro de la
 * transacción y se entrega {@code AFTER_COMMIT}.
 */
@Service
public class SolicitudEventoAdminService {

    private static final String SLUG_PENA = "baniterio";
    private static final String TITULO_PUSH = "Eventos";
    static final String MOTIVO_RECHAZO_POR_DEFECTO =
            "Tu solicitud de evento no se ha aprobado. Si tienes dudas, habla con la organización.";

    private final SolicitudEventoRepository solicitudes;
    private final EventoRepository eventos;
    private final UsuarioRepository usuarios;
    private final PenaRepository penas;
    private final ServicioPermisos permisos;
    private final ApplicationEventPublisher publisher;

    public SolicitudEventoAdminService(SolicitudEventoRepository solicitudes, EventoRepository eventos,
            UsuarioRepository usuarios, PenaRepository penas, ServicioPermisos permisos,
            ApplicationEventPublisher publisher) {
        this.solicitudes = solicitudes;
        this.eventos = eventos;
        this.usuarios = usuarios;
        this.penas = penas;
        this.permisos = permisos;
        this.publisher = publisher;
    }

    private void exigirAdmin(Long usuarioId) {
        if (usuarioId == null || !permisos.esAdministrador(usuarioId)) {
            throw new SinPermisoException();
        }
    }

    private Long penaId() {
        return penas.findBySlug(SLUG_PENA).orElseThrow(
                () -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'")).getId();
    }

    @Transactional(readOnly = true)
    public List<SolicitudEventoResumen> listar(Long adminId, EstadoSolicitud estado) {
        exigirAdmin(adminId);
        return solicitudes.findByPenaIdAndEstadoOrderByCreatedAtAsc(penaId(), estado).stream()
                .map(this::aResumen)
                .toList();
    }

    private SolicitudEventoResumen aResumen(SolicitudEvento s) {
        Usuario u = s.getSolicitante();
        Evento e = s.getEvento();
        var ev = e == null ? null
                : new SolicitudEventoResumen.EventoRef(e.getId(), e.getNombre(), e.getFecha());
        return new SolicitudEventoResumen(s.getId(), s.getTipo().name(), s.getEstado().name(),
                new SolicitudEventoResumen.Solicitante(u.getId(), u.getNombre(), u.getApellidos()),
                ev, s.getMensaje(), s.getCreatedAt());
    }

    @Transactional
    public void aprobar(Long solicitudId, Long adminId) {
        exigirAdmin(adminId);
        SolicitudEvento s = pendiente(solicitudId);
        Usuario admin = usuarios.findById(adminId).orElseThrow();

        if (s.getTipo() == TipoSolicitudEvento.BORRAR) {
            Evento e = s.getEvento();
            s.setEvento(null);
            resolver(s, admin, EstadoSolicitud.APROBADA, null);
            if (e != null) {
                eventos.delete(e);
                push(s, "Se ha borrado el evento «" + e.getNombre() + "».");
            } else {
                push(s, "Se ha borrado el evento.");
            }
        } else {
            // CREAR: queda como crédito (evento_id NULL).
            resolver(s, admin, EstadoSolicitud.APROBADA, null);
            push(s, "Ya puedes crear tu evento.");
        }
    }

    @Transactional
    public void rechazar(Long solicitudId, Long adminId, String motivo) {
        exigirAdmin(adminId);
        SolicitudEvento s = pendiente(solicitudId);
        Usuario admin = usuarios.findById(adminId).orElseThrow();
        String texto = StringUtils.hasText(motivo) ? motivo : MOTIVO_RECHAZO_POR_DEFECTO;
        resolver(s, admin, EstadoSolicitud.RECHAZADA, texto);
        push(s, texto);
    }

    private SolicitudEvento pendiente(Long id) {
        SolicitudEvento s = solicitudes.findById(id).orElseThrow(SolicitudEventoYaResueltaException::new);
        if (s.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new SolicitudEventoYaResueltaException();
        }
        return s;
    }

    private void resolver(SolicitudEvento s, Usuario admin, EstadoSolicitud estado, String motivo) {
        s.setEstado(estado);
        s.setResueltaPor(admin);
        s.setResueltaAt(Instant.now());
        s.setMotivoRechazo(motivo);
        solicitudes.save(s);
    }

    private void push(SolicitudEvento s, String cuerpo) {
        publisher.publishEvent(new AvisoPushEvent(
                new Audiencia.UsuarioUnico(s.getSolicitante().getId()), TITULO_PUSH, cuerpo));
    }
}
