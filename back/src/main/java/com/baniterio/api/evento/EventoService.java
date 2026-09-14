package com.baniterio.api.evento;

import java.time.LocalDate;
import java.util.List;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.cuenta.CuentaConflictoException;
import com.baniterio.api.cuenta.CuentaNoEncontradaException;
import com.baniterio.api.evento.dto.CuentaRef;
import com.baniterio.api.evento.dto.EventoDetalle;
import com.baniterio.api.evento.dto.EventoResumen;
import com.baniterio.api.evento.dto.GuardarEventoRequest;
import com.baniterio.api.evento.dto.ListaEventosResponse;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaPilotoService;
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
    private static final String TITULO_PUSH = "Eventos";
    /** Un evento se considera "pasado" cuando han transcurrido estos días desde su fecha (o fecha_fin). */
    private static final int DIAS_PARA_PASADO = 3;

    private final EventoRepository eventos;
    private final SolicitudEventoRepository solicitudes;
    private final CuentaRepository cuentas;
    private final ServicioPermisos permisos;
    private final PenaPilotoService pena;
    private final UsuarioRepository usuarios;
    private final ApplicationEventPublisher publisher;
    private final AsistenciaService asistencias;
    private final FichaBebidaService fichaBebida;

    public EventoService(EventoRepository eventos, SolicitudEventoRepository solicitudes,
                         CuentaRepository cuentas, ServicioPermisos permisos, PenaPilotoService pena,
                         UsuarioRepository usuarios, ApplicationEventPublisher publisher,
                         AsistenciaService asistencias, FichaBebidaService fichaBebida) {
        this.eventos = eventos;
        this.solicitudes = solicitudes;
        this.cuentas = cuentas;
        this.permisos = permisos;
        this.pena = pena;
        this.usuarios = usuarios;
        this.publisher = publisher;
        this.asistencias = asistencias;
        this.fichaBebida = fichaBebida;
    }

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Cambió el valor de una cuota (comparando por número, no por escala). */
    private static boolean cuotaCambio(java.math.BigDecimal antes, java.math.BigDecimal ahora) {
        if (antes == null || ahora == null) {
            return antes != ahora;
        }
        return antes.compareTo(ahora) != 0;
    }

    Long penaId() {
        return pena.id();
    }

    /** Fecha mínima para que un evento siga contando como "futuro": {@code hoy - DIAS_PARA_PASADO}. */
    private static LocalDate limiteFuturo() {
        return LocalDate.now().minusDays(DIAS_PARA_PASADO);
    }

    private static boolean esPasado(Evento e) {
        LocalDate fin = e.getFechaFin() != null ? e.getFechaFin() : e.getFecha();
        return fin.isBefore(limiteFuturo());
    }

    Evento cargar(Long eventoId) {
        return eventos.findById(eventoId).orElseThrow(EventoNoEncontradoException::new);
    }

    /**
     * La cuenta del evento a partir del request: o una cuenta existente de la peña
     * ({@code cuentaId}), o una nueva con el nombre del evento ({@code cuentaNueva}).
     * La validación de "una y solo una" ya la hace {@link GuardarEventoRequest}.
     */
    private Cuenta resolverCuenta(GuardarEventoRequest req) {
        if (req.quiereCuentaNueva()) {
            Pena penaEntidad = pena.entidad();
            String nombre = req.nombre().trim();
            if (cuentas.existsByPenaIdAndNombreIgnoreCase(penaEntidad.getId(), nombre)) {
                throw new CuentaConflictoException();
            }
            return cuentas.save(Cuenta.builder().pena(penaEntidad).nombre(nombre).build());
        }
        return cuentas.findById(req.cuentaId())
                .filter(c -> c.getPena().getId().equals(penaId()))
                .orElseThrow(CuentaNoEncontradaException::new);
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

    /**
     * Editar y borrar un evento es solo de administradores/superadmin, aunque lo
     * haya creado un miembro con crédito. Antes también podía el creador; se
     * quitó porque el botón «Editar» y el de borrar solo deben verlos y usarlos
     * admin/superadmin.
     */
    boolean puedeGestionar(Long usuarioId, Evento e) {
        return permisos.esAdministrador(usuarioId);
    }

    /** Eventos "abiertos" (no pasados, no ocultos), por fecha ascendente. Para el selector de "enviar a evento". */
    @Transactional(readOnly = true)
    public List<com.baniterio.api.evento.dto.EventoAbiertoDto> abiertos(Long usuarioId) {
        return eventos.futuros(penaId(), limiteFuturo()).stream()
                .sorted(java.util.Comparator.comparing(Evento::getFecha).thenComparing(Evento::getId))
                .map(e -> new com.baniterio.api.evento.dto.EventoAbiertoDto(
                        e.getId(), e.getNombre(), e.getFecha()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ListaEventosResponse listar(Long usuarioId, int pagina) {
        Page<Evento> p = eventos.listar(penaId(), limiteFuturo(),
                PageRequest.of(Math.max(pagina, 0), PAGINA));
        var resumenes = p.getContent().stream().map(EventoService::aResumen).toList();
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

        // Las cuotas solo las fija un administrador; un miembro con crédito no.
        // Solo cubatas es el precio que se pone; las otras 4 se derivan.
        CalculadoraCuota.Cuotas cuotas = CalculadoraCuota.derivar(admin ? req.cuotaCubatas() : null);
        Evento e = eventos.save(Evento.builder()
                .pena(pena.entidad())
                .cuenta(resolverCuenta(req))
                .nombre(req.nombre().trim())
                .descripcion(vacioANull(req.descripcion()))
                .lugar(vacioANull(req.lugar()))
                .fecha(req.fecha())
                .fechaFin(req.fechaFin())
                .cuotaCubatas(cuotas.cubatas())
                .cuotaCervezas(cuotas.cervezas())
                .cuotaCubatas1Dia(cuotas.cubatas1Dia())
                .cuotaCervezas1Dia(cuotas.cervezas1Dia())
                .cuotaEmbarazada(cuotas.embarazada())
                .precioCamiseta(admin ? req.precioCamiseta() : null)
                .precioSudadera(admin ? req.precioSudadera() : null)
                .creadoPor(usuario)
                .build());

        if (credito != null) {
            credito.setEvento(e);
            solicitudes.save(credito);
        }
        return aDetalle(usuarioId, e);
    }

    /** Edita un evento. Solo un administrador/superadmin → si no, {@link SinPermisoEventoException}. */
    @Transactional
    public EventoDetalle editar(Long usuarioId, Long eventoId, GuardarEventoRequest req) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        java.math.BigDecimal cubatasAntes = e.getCuotaCubatas();
        java.math.BigDecimal cervezasAntes = e.getCuotaCervezas();
        java.math.BigDecimal cubatas1DiaAntes = e.getCuotaCubatas1Dia();
        java.math.BigDecimal cervezas1DiaAntes = e.getCuotaCervezas1Dia();
        java.math.BigDecimal embarazadaAntes = e.getCuotaEmbarazada();
        e.setNombre(req.nombre().trim());
        e.setDescripcion(vacioANull(req.descripcion()));
        e.setLugar(vacioANull(req.lugar()));
        e.setFecha(req.fecha());
        e.setFechaFin(req.fechaFin());
        e.setCuenta(resolverCuenta(req));
        // Las cuotas solo las cambia un administrador; si edita el creador, se dejan como están.
        // Solo cubatas es el precio que se pone; las otras 4 se derivan.
        if (permisos.esAdministrador(usuarioId)) {
            CalculadoraCuota.Cuotas cuotas = CalculadoraCuota.derivar(req.cuotaCubatas());
            e.setCuotaCubatas(cuotas.cubatas());
            e.setCuotaCervezas(cuotas.cervezas());
            e.setCuotaCubatas1Dia(cuotas.cubatas1Dia());
            e.setCuotaCervezas1Dia(cuotas.cervezas1Dia());
            e.setCuotaEmbarazada(cuotas.embarazada());
            e.setPrecioCamiseta(req.precioCamiseta());
            e.setPrecioSudadera(req.precioSudadera());
        }
        eventos.save(e);
        // Si cambió alguna cuota de un evento con ficha (San Miguel), se recalculan
        // las cuotas de todas las fichas de bebida.
        boolean algunaCuotaCambio = cuotaCambio(cubatasAntes, e.getCuotaCubatas())
                || cuotaCambio(cervezasAntes, e.getCuotaCervezas())
                || cuotaCambio(cubatas1DiaAntes, e.getCuotaCubatas1Dia())
                || cuotaCambio(cervezas1DiaAntes, e.getCuotaCervezas1Dia())
                || cuotaCambio(embarazadaAntes, e.getCuotaEmbarazada());
        if (algunaCuotaCambio && e.getCuenta().isLlevaFichaBebida()) {
            fichaBebida.recalcularCuotas(e.getId());
        }
        return aDetalle(usuarioId, e);
    }

    /**
     * "Borra" un evento: lo oculta (no lo quita de la BBDD), por si ha sido sin
     * querer. Deja de salir en el listado y en "pendientes de respuesta"; se
     * recupera con {@link #recuperar}. Solo admin/superadmin → si no,
     * {@link SinPermisoEventoException}.
     */
    @Transactional
    public void ocultar(Long usuarioId, Long eventoId) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        e.setOculto(true);
        eventos.save(e);
    }

    /** Deshace {@link #ocultar}. Solo admin/superadmin → si no, {@link SinPermisoEventoException}. */
    @Transactional
    public EventoDetalle recuperar(Long usuarioId, Long eventoId) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        e.setOculto(false);
        eventos.save(e);
        return aDetalle(usuarioId, e);
    }

    /** Eventos ocultos ("borrados") de la peña, para poder recuperarlos. Solo admin/superadmin. */
    @Transactional(readOnly = true)
    public List<EventoResumen> listarOcultos(Long usuarioId) {
        permisos.exigirAdmin(usuarioId, SinPermisoEventoException::new);
        return eventos.ocultos(penaId()).stream().map(EventoService::aResumen).toList();
    }

    /**
     * Un miembro normal pide un crédito para crear un evento. Un administrador no
     * lo necesita → 409 {@code SOLICITUD_EVENTO_NO_APLICA}. Ya con una CREAR
     * pendiente → 409 {@code SOLICITUD_EVENTO_YA_PENDIENTE}. Con un crédito
     * aprobado sin usar → 409 {@code CREDITO_SIN_CONSUMIR}. Publica push a los
     * administradores. Devuelve el id de la solicitud creada.
     */
    @Transactional
    public Long solicitarCredito(Long usuarioId, String mensaje) {
        if (permisos.esAdministrador(usuarioId)) {
            throw new SolicitudEventoConflictoException("SOLICITUD_EVENTO_NO_APLICA");
        }
        if (solicitudes.existsBySolicitanteIdAndTipoAndEstado(
                usuarioId, TipoSolicitudEvento.CREAR, EstadoSolicitud.PENDIENTE)) {
            throw new SolicitudEventoConflictoException("SOLICITUD_EVENTO_YA_PENDIENTE");
        }
        if (solicitudes.findFirstBySolicitanteIdAndTipoAndEstadoAndEventoIsNullOrderByIdAsc(
                usuarioId, TipoSolicitudEvento.CREAR, EstadoSolicitud.APROBADA).isPresent()) {
            throw new SolicitudEventoConflictoException("CREDITO_SIN_CONSUMIR");
        }
        Usuario u = usuarios.findById(usuarioId).orElseThrow();
        SolicitudEvento sol = solicitudes.save(SolicitudEvento.builder()
                .pena(pena.entidad())
                .solicitante(u)
                .tipo(TipoSolicitudEvento.CREAR)
                .mensaje(vacioANull(mensaje))
                .estado(EstadoSolicitud.PENDIENTE)
                .build());
        publisher.publishEvent(new AvisoPushEvent(new Audiencia.Administradores(),
                TITULO_PUSH, u.getNombre() + " quiere crear un evento."));
        return sol.getId();
    }

    private static CuentaRef aCuentaRef(Evento e) {
        Cuenta c = e.getCuenta();
        return new CuentaRef(c.getId(), c.getNombre());
    }

    /** Ficha corta de un evento. La usa el listado y {@code AsistenciaController} (pendientes de respuesta). */
    static EventoResumen aResumen(Evento e) {
        return new EventoResumen(e.getId(), e.getNombre(), e.getFecha(), e.getFechaFin(),
                e.getLugar(), esPasado(e), aCuentaRef(e));
    }

    EventoDetalle aDetalle(Long usuarioId, Evento e) {
        Usuario creador = e.getCreadoPor();
        var creadoPor = creador == null ? null
                : new EventoDetalle.CreadoPor(creador.getId(), creador.getNombre());
        boolean gestiona = puedeGestionar(usuarioId, e);
        return new EventoDetalle(e.getId(), e.getNombre(), e.getDescripcion(), e.getLugar(),
                e.getFecha(), e.getFechaFin(), esPasado(e), aCuentaRef(e),
                e.getCuotaCubatas(), e.getCuotaCervezas(), e.getCuotaCubatas1Dia(),
                e.getCuotaCervezas1Dia(), e.getCuotaEmbarazada(),
                e.getPrecioCamiseta(), e.getPrecioSudadera(),
                creadoPor, gestiona, gestiona, e.isOculto(),
                asistencias.detalleDe(usuarioId, e));
    }
}
