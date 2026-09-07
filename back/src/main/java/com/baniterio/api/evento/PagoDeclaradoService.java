package com.baniterio.api.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.baniterio.api.admin.SinPermisoException;
import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.evento.dto.CubiertoPago;
import com.baniterio.api.evento.dto.DeclararPagoRequest;
import com.baniterio.api.evento.dto.MiPagoDeclarado;
import com.baniterio.api.evento.dto.PagoDeclaradoPendiente;
import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.EstadoPagoDeclarado;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.MetodoPago;
import com.baniterio.api.identidad.PagoDeclarado;
import com.baniterio.api.identidad.PagoDeclaradoRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.identidad.VinculoFamiliarService;
import com.baniterio.api.push.Audiencia;
import com.baniterio.api.push.AvisoPushEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Declaración de pago y su confirmación por el administrador (pieza 5, parcial).
 * El peñista declara desde el detalle del evento; la declaración queda
 * {@code PENDIENTE} y avisa a los administradores. Un admin la confirma (marca las
 * {@link FichaBebida} cubiertas como pagadas) o la rechaza (avisa al declarante).
 * El atajo del admin en el modal de asistentes ({@code AsistenciaService.confirmarPago})
 * sigue marcando fichas directamente; si cerraba una declaración pendiente, la
 * llama a {@link #confirmarPorAtajo}.
 */
@Service
public class PagoDeclaradoService {

    private static final String TITULO_PUSH = "Pagos";

    private final PagoDeclaradoRepository pagos;
    private final EventoRepository eventos;
    private final AsistenciaEventoRepository asistencias;
    private final FichaBebidaRepository fichas;
    private final UsuarioRepository usuarios;
    private final VinculoFamiliarService vinculoFamiliar;
    private final ServicioPermisos permisos;
    private final ApplicationEventPublisher publisher;

    public PagoDeclaradoService(PagoDeclaradoRepository pagos, EventoRepository eventos,
            AsistenciaEventoRepository asistencias, FichaBebidaRepository fichas,
            UsuarioRepository usuarios, VinculoFamiliarService vinculoFamiliar,
            ServicioPermisos permisos, ApplicationEventPublisher publisher) {
        this.pagos = pagos;
        this.eventos = eventos;
        this.asistencias = asistencias;
        this.fichas = fichas;
        this.usuarios = usuarios;
        this.vinculoFamiliar = vinculoFamiliar;
        this.permisos = permisos;
        this.publisher = publisher;
    }

    // ---- Peñista ----

    /**
     * Crea una declaración {@code PENDIENTE} para {@code usuarioId} en un evento de
     * San Miguel. {@code cubreUsuarioIds} = pareja/hijos con cuenta; {@code
     * cubreAsistenciaIds} = invitados propios. Se añade siempre la asistencia del
     * propio declarante. Cada asistencia cubierta debe tener cuota. Avisa a los
     * administradores.
     */
    @Transactional
    public void declarar(Long usuarioId, Long eventoId, DeclararPagoRequest req) {
        Evento e = cargarSanMiguel(eventoId);
        pagos.findByEventoIdAndDeclaradoPorIdAndEstado(eventoId, usuarioId, EstadoPagoDeclarado.PENDIENTE)
                .ifPresent(p -> {
                    throw new PagoDeclaradoYaPendienteException();
                });

        Set<Long> cubre = new LinkedHashSet<>();
        cubre.add(miAsistencia(eventoId, usuarioId).getId());

        List<Long> familia = vinculoFamiliar.personasParaPago(usuarioId).stream()
                .map(VinculoFamiliarService.Persona::id).toList();
        for (Long otroUsuarioId : nullSafe(req.cubreUsuarioIds())) {
            if (!familia.contains(otroUsuarioId)) {
                throw new SinPermisoEventoException();
            }
            AsistenciaEvento a = asistencias.findByEventoIdAndUsuarioId(eventoId, otroUsuarioId)
                    .orElseThrow(AsistenciaNoEncontradaException::new);
            cubre.add(a.getId());
        }
        for (Long asistenciaId : nullSafe(req.cubreAsistenciaIds())) {
            AsistenciaEvento a = asistencias.findByIdAndEventoId(asistenciaId, eventoId)
                    .orElseThrow(AsistenciaNoEncontradaException::new);
            boolean invitadoMio = a.getUsuario() == null && a.getRegistradoPor() != null
                    && a.getRegistradoPor().getId().equals(usuarioId);
            if (!invitadoMio) {
                throw new SinPermisoEventoException();
            }
            cubre.add(a.getId());
        }

        for (Long asistenciaId : cubre) {
            FichaBebida f = fichas.findByAsistenciaId(asistenciaId)
                    .orElseThrow(FichaSinCuotaException::new);
            if (f.getCuota() == null) {
                throw new FichaSinCuotaException();
            }
        }

        PagoDeclarado p = pagos.save(PagoDeclarado.builder()
                .evento(e)
                .declaradoPor(usuarios.findById(usuarioId).orElseThrow())
                .importe(req.importe())
                .metodoPago(req.metodo())
                .estado(EstadoPagoDeclarado.PENDIENTE)
                .cubre(cubre)
                .build());

        String quien = p.getDeclaradoPor().getNombre();
        publisher.publishEvent(new AvisoPushEvent(new Audiencia.Administradores(),
                TITULO_PUSH, quien + " dice que ha pagado su cuota de «" + e.getNombre() + "»."));
    }

    /** Borra mi declaración {@code PENDIENTE} de ese evento. 404 si no hay. */
    @Transactional
    public void anularMia(Long usuarioId, Long eventoId) {
        PagoDeclarado p = pagos
                .findByEventoIdAndDeclaradoPorIdAndEstado(eventoId, usuarioId, EstadoPagoDeclarado.PENDIENTE)
                .orElseThrow(PagoDeclaradoNoEncontradoException::new);
        pagos.delete(p);
    }

    // ---- Administrador ----

    @Transactional(readOnly = true)
    public List<PagoDeclaradoPendiente> pendientes(Long adminId) {
        exigirAdmin(adminId);
        return pagos.findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado.PENDIENTE).stream()
                .map(this::aPendiente)
                .toList();
    }

    @Transactional
    public void confirmar(Long adminId, Long pagoId) {
        exigirAdmin(adminId);
        PagoDeclarado p = pagos.findById(pagoId).orElseThrow(PagoDeclaradoNoEncontradoException::new);
        exigirPendiente(p);
        Usuario admin = usuarios.findById(adminId).orElseThrow();
        marcarPagadas(p.getCubre(), p.getMetodoPago(), admin);
        resolver(p, EstadoPagoDeclarado.CONFIRMADA, admin);
    }

    @Transactional
    public void rechazar(Long adminId, Long pagoId) {
        exigirAdmin(adminId);
        PagoDeclarado p = pagos.findById(pagoId).orElseThrow(PagoDeclaradoNoEncontradoException::new);
        exigirPendiente(p);
        resolver(p, EstadoPagoDeclarado.RECHAZADA, usuarios.findById(adminId).orElseThrow());
        publisher.publishEvent(new AvisoPushEvent(
                new Audiencia.UsuarioUnico(p.getDeclaradoPor().getId()),
                TITULO_PUSH, "Tu pago de «" + p.getEvento().getNombre() + "» no se pudo confirmar."));
    }

    /**
     * El atajo del admin en el modal de asistentes acaba de marcar {@code asistenciaId}
     * como pagada a mano; si había una declaración {@code PENDIENTE} que la cubría, se
     * cierra como {@code CONFIRMADA} (mismo admin, misma hora) para que no quede en la
     * cola. No toca las otras fichas de esa declaración.
     */
    @Transactional
    public void confirmarPorAtajo(Long eventoId, Long asistenciaId, Usuario admin) {
        for (PagoDeclarado p : pagos.findByEventoIdAndEstado(eventoId, EstadoPagoDeclarado.PENDIENTE)) {
            if (p.getCubre().contains(asistenciaId)) {
                resolver(p, EstadoPagoDeclarado.CONFIRMADA, admin);
            }
        }
    }

    // ---- Lectura para otros servicios ----

    /** Ids de {@code asistencia_evento} cubiertos por alguna declaración PENDIENTE de ese evento. */
    @Transactional(readOnly = true)
    public Set<Long> asistenciasConDeclaracionPendiente(Long eventoId) {
        Set<Long> out = new LinkedHashSet<>();
        for (PagoDeclarado p : pagos.findByEventoIdAndEstado(eventoId, EstadoPagoDeclarado.PENDIENTE)) {
            out.addAll(p.getCubre());
        }
        return out;
    }

    /**
     * La última declaración del usuario en ese evento si está PENDIENTE o RECHAZADA
     * (para el aviso del detalle del evento); {@code null} en cualquier otro caso.
     */
    @Transactional(readOnly = true)
    public MiPagoDeclarado miPagoDeclarado(Long eventoId, Long usuarioId) {
        PagoDeclarado p = pagos
                .findFirstByEventoIdAndDeclaradoPorIdOrderByCreatedAtDesc(eventoId, usuarioId)
                .orElse(null);
        if (p == null || p.getEstado() == EstadoPagoDeclarado.CONFIRMADA) {
            return null;
        }
        return new MiPagoDeclarado(p.getImporte(), p.getMetodoPago().name(), p.getEstado().name(),
                p.getCreatedAt(), cubiertos(p));
    }

    // ---- Helpers ----

    private Evento cargarSanMiguel(Long eventoId) {
        Evento e = eventos.findById(eventoId).orElseThrow(EventoNoEncontradoException::new);
        if (!e.getCuenta().isLlevaFichaBebida()) {
            throw new EventoSinFichaException();
        }
        return e;
    }

    private AsistenciaEvento miAsistencia(Long eventoId, Long usuarioId) {
        return asistencias.findByEventoIdAndUsuarioId(eventoId, usuarioId)
                .orElseThrow(AsistenciaNoEncontradaException::new);
    }

    private void marcarPagadas(Set<Long> asistenciaIds, MetodoPago metodo, Usuario admin) {
        Instant ahora = Instant.now();
        for (Long asistenciaId : asistenciaIds) {
            FichaBebida f = fichas.findByAsistenciaId(asistenciaId)
                    .orElseThrow(FichaSinCuotaException::new);
            f.setPagado(true);
            f.setMetodoPago(metodo);
            f.setPagadoConfirmadoPor(admin);
            f.setPagadoAt(ahora);
            fichas.save(f);
        }
    }

    private void resolver(PagoDeclarado p, EstadoPagoDeclarado estado, Usuario admin) {
        p.setEstado(estado);
        p.setResueltoPor(admin);
        p.setResueltoAt(Instant.now());
        pagos.save(p);
    }

    private PagoDeclaradoPendiente aPendiente(PagoDeclarado p) {
        return new PagoDeclaradoPendiente(p.getId(), p.getEvento().getId(), p.getEvento().getNombre(),
                p.getDeclaradoPor().getNombre(), p.getImporte(), p.getMetodoPago().name(),
                p.getCreatedAt(), cubiertos(p));
    }

    private List<CubiertoPago> cubiertos(PagoDeclarado p) {
        List<CubiertoPago> cubre = new ArrayList<>();
        for (Long asistenciaId : p.getCubre()) {
            AsistenciaEvento a = asistencias.findById(asistenciaId).orElse(null);
            String nombre = a == null ? "(?)"
                    : (a.getUsuario() != null ? a.getUsuario().getNombre() : a.getNombre());
            BigDecimal cuota = fichas.findByAsistenciaId(asistenciaId)
                    .map(FichaBebida::getCuota).orElse(null);
            cubre.add(new CubiertoPago(nombre, cuota));
        }
        return cubre;
    }

    private void exigirAdmin(Long usuarioId) {
        if (!permisos.esAdministrador(usuarioId)) {
            throw new SinPermisoException();
        }
    }

    private static void exigirPendiente(PagoDeclarado p) {
        if (p.getEstado() != EstadoPagoDeclarado.PENDIENTE) {
            throw new PagoDeclaradoYaResueltoException();
        }
    }

    private static <T> List<T> nullSafe(List<T> l) {
        return l == null ? List.of() : l;
    }
}
