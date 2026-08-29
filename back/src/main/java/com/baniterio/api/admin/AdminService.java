package com.baniterio.api.admin;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.baniterio.api.admin.dto.AprobarResponse;
import com.baniterio.api.admin.dto.SolicitudResumen;
import com.baniterio.api.auth.RegistroConflictoException;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.SolicitudIngreso;
import com.baniterio.api.identidad.SolicitudIngresoRepository;
import com.baniterio.api.identidad.TelefonoAutorizado;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Lógica del panel de administración relacionada con las solicitudes de ingreso:
 * listarlas, aprobarlas (autorizando el teléfono y, si la solicitud traía
 * contraseña, creando ya el usuario) y rechazarlas con un motivo.
 *
 * <p>{@link #aprobarSolicitud} y {@link #rechazarSolicitud} son
 * {@code @Transactional}: todos los cambios (usuario, membresía, teléfono,
 * estado de la solicitud) se confirman juntos o no se confirma ninguno. El
 * correo se manda DESPUÉS del commit, vía {@link SolicitudResueltaEvent} y
 * {@link ManejadorCorreoSolicitud}, para que un fallo de envío no revierta la
 * resolución.
 *
 * <p>La Task 7 ampliará esta clase con la gestión de miembros/permisos.
 */
@Service
public class AdminService {

    /** Peña piloto. Con el alcance de una sola peña, se resuelve por slug. */
    private static final String SLUG_PENA = "baniterio";

    /**
     * Texto de rechazo cuando quien administra no escribe un motivo propio
     * (spec §2). Neutro y sin acusaciones: la persona puede hablar con la peña.
     */
    static final String MOTIVO_RECHAZO_POR_DEFECTO =
            "No hemos podido confirmar tu vinculación con la peña, así que de momento no "
            + "podemos darte acceso. Si crees que es un error, habla con alguien de la peña.";

    private final SolicitudIngresoRepository solicitudes;
    private final UsuarioRepository usuarios;
    private final MembresiaRepository membresias;
    private final TelefonoAutorizadoRepository telefonos;
    private final PenaRepository penas;
    private final ApplicationEventPublisher publisher;

    public AdminService(SolicitudIngresoRepository solicitudes, UsuarioRepository usuarios,
                        MembresiaRepository membresias, TelefonoAutorizadoRepository telefonos,
                        PenaRepository penas, ApplicationEventPublisher publisher) {
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.membresias = membresias;
        this.telefonos = telefonos;
        this.penas = penas;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public List<SolicitudResumen> listarSolicitudes(EstadoSolicitud estado) {
        Long penaId = penas.findBySlug(SLUG_PENA).orElseThrow().getId();
        return solicitudes.findByPenaIdAndEstado(penaId, estado).stream()
                .sorted(Comparator.comparing(SolicitudIngreso::getCreatedAt))
                .map(s -> new SolicitudResumen(s.getId(), s.getNombre(), s.getApellidos(),
                        s.getTelefono(), s.getEmail(), s.getMotivo(), s.getRelacion(), s.getConocidos(),
                        s.getPasswordHash() != null, s.getEstado().name(), s.getCreatedAt()))
                .toList();
    }

    @Transactional
    public AprobarResponse aprobarSolicitud(Long solicitudId, Long adminId) {
        // No filtramos "no existe" vs "ya resuelta": ambos devuelven 409.
        SolicitudIngreso sol = solicitudes.findById(solicitudId)
                .orElseThrow(SolicitudYaResueltaException::new);
        if (sol.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new SolicitudYaResueltaException();
        }
        if (usuarios.existsByTelefono(sol.getTelefono()) || usuarios.existsByEmail(sol.getEmail())) {
            throw new RegistroConflictoException();
        }

        Usuario admin = usuarios.findById(adminId).orElseThrow();
        Pena pena = sol.getPena();

        // Reutiliza la fila de telefono_autorizado si ya existe (columna UNIQUE).
        TelefonoAutorizado tel = telefonos.findByTelefono(sol.getTelefono())
                .orElseGet(() -> TelefonoAutorizado.builder()
                        .telefono(sol.getTelefono())
                        .pena(pena)
                        .usado(false)
                        .build());
        tel.setAutorizadoPor(admin);

        boolean cuentaCreada;
        if (sol.getPasswordHash() != null) {
            Usuario usuario = usuarios.save(Usuario.builder()
                    .telefono(sol.getTelefono())
                    .email(sol.getEmail())
                    .passwordHash(sol.getPasswordHash())
                    .nombre(sol.getNombre())
                    .apellidos(sol.getApellidos())
                    .mote(null)
                    .esSuperadmin(false)
                    .activo(true)
                    .build());
            membresias.save(Membresia.builder()
                    .usuario(usuario)
                    .pena(pena)
                    .rol(RolMembresia.MIEMBRO)
                    .activa(true)
                    .build());
            tel.setUsado(true);
            cuentaCreada = true;
        } else {
            tel.setUsado(false);
            cuentaCreada = false;
        }
        telefonos.save(tel);

        sol.setEstado(EstadoSolicitud.APROBADA);
        sol.setResueltaPor(admin);
        sol.setResueltaAt(Instant.now());

        publisher.publishEvent(new SolicitudResueltaEvent(
                sol.getEmail(), sol.getNombre(), true, cuentaCreada, null));
        return new AprobarResponse(cuentaCreada ? "CUENTA_CREADA" : "TELEFONO_AUTORIZADO");
    }

    @Transactional
    public void rechazarSolicitud(Long solicitudId, Long adminId, String motivo) {
        SolicitudIngreso sol = solicitudes.findById(solicitudId)
                .orElseThrow(SolicitudYaResueltaException::new);
        if (sol.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new SolicitudYaResueltaException();
        }
        String texto = StringUtils.hasText(motivo) ? motivo : MOTIVO_RECHAZO_POR_DEFECTO;

        sol.setEstado(EstadoSolicitud.RECHAZADA);
        sol.setMotivoRechazo(texto);
        sol.setResueltaPor(usuarios.findById(adminId).orElseThrow());
        sol.setResueltaAt(Instant.now());

        publisher.publishEvent(new SolicitudResueltaEvent(
                sol.getEmail(), sol.getNombre(), false, false, texto));
    }
}
