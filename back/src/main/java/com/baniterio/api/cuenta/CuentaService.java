package com.baniterio.api.cuenta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baniterio.api.admin.SinPermisoException;
import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.cuenta.dto.CuentaDetalle;
import com.baniterio.api.cuenta.dto.CuentaResumen;
import com.baniterio.api.cuenta.dto.MovimientoFila;
import com.baniterio.api.cuenta.dto.PenistaCuota;
import com.baniterio.api.cuenta.dto.ResumenGasto;
import com.baniterio.api.identidad.CategoriaMovimiento;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoPagoCuota;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.MovimientoCuenta;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de la sección Cuentas: de momento solo listar y ver el detalle. Crear,
 * editar, borrar y los movimientos llegan en tareas siguientes. Crear cuentas se
 * hace desde el editor de eventos ({@code EventoService}). La peña se resuelve
 * por slug como en {@code EventoService}.
 */
@Service
public class CuentaService {

    private static final String SLUG_PENA = "baniterio";
    /** Igual que {@code EventoService.DIAS_PARA_PASADO}: un evento cuenta como futuro hasta 3 días después de su fecha. */
    private static final int DIAS_PARA_PASADO = 3;

    private final CuentaRepository cuentas;
    private final EventoRepository eventos;
    private final PenaRepository penas;
    private final MovimientoCuentaRepository movimientos;
    private final MovimientoCuentaService movimientoCuenta;
    private final FichaBebidaRepository fichas;
    private final UsuarioRepository usuarios;
    private final ServicioPermisos permisos;

    public CuentaService(CuentaRepository cuentas, EventoRepository eventos, PenaRepository penas,
            MovimientoCuentaRepository movimientos, MovimientoCuentaService movimientoCuenta,
            FichaBebidaRepository fichas, UsuarioRepository usuarios, ServicioPermisos permisos) {
        this.cuentas = cuentas;
        this.eventos = eventos;
        this.penas = penas;
        this.movimientos = movimientos;
        this.movimientoCuenta = movimientoCuenta;
        this.fichas = fichas;
        this.usuarios = usuarios;
        this.permisos = permisos;
    }

    private Long penaId() {
        return penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
    }

    private static CuentaResumen aResumen(Cuenta c) {
        return new CuentaResumen(c.getId(), c.getNombre(), c.getDescripcion());
    }

    private static LocalDate fin(Evento e) {
        return e.getFechaFin() != null ? e.getFechaFin() : e.getFecha();
    }

    /**
     * Cuentas de la peña ordenadas por el evento futuro más próximo de cada una
     * (la que menos falta para su evento, primera). Las que no tienen ningún
     * evento futuro van al final, por nombre.
     */
    @Transactional(readOnly = true)
    public List<CuentaResumen> listar() {
        Long penaId = penaId();
        LocalDate limite = LocalDate.now().minusDays(DIAS_PARA_PASADO);

        Map<Long, LocalDate> proximaPorCuenta = new HashMap<>();
        for (Evento e : eventos.futuros(penaId, limite)) {
            proximaPorCuenta.merge(e.getCuenta().getId(), fin(e),
                    (a, b) -> a.isBefore(b) ? a : b);
        }

        Comparator<Cuenta> orden = Comparator
                .comparing((Cuenta c) -> proximaPorCuenta.get(c.getId()) == null)
                .thenComparing(c -> proximaPorCuenta.getOrDefault(c.getId(), LocalDate.MAX))
                .thenComparing(Cuenta::getNombre, String.CASE_INSENSITIVE_ORDER);

        return cuentas.findByPenaId(penaId).stream()
                .sorted(orden)
                .map(CuentaService::aResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public CuentaDetalle detalle(Long usuarioId, Long cuentaId) {
        return detalle(usuarioId, cuentaId, null);
    }

    /**
     * La hoja de la cuenta para un año. {@code anioPedido} {@code null} = el año en
     * curso ({@code cuenta.anioActual}); cualquier año pasado se puede consultar
     * (solo lectura). Los campos de "año vivo" (estimación, aviso de cobrado sin
     * ingresar) van a cero/null si el año pedido no es el actual.
     */
    @Transactional(readOnly = true)
    public CuentaDetalle detalle(Long usuarioId, Long cuentaId, Integer anioPedido) {
        Cuenta c = cuentas.findById(cuentaId).orElseThrow(CuentaNoEncontradaException::new);
        boolean admin = permisos.esAdministrador(usuarioId);
        int anio = anioPedido != null ? anioPedido : c.getAnioActual();
        boolean esActual = anio == c.getAnioActual();

        List<Integer> anios = new ArrayList<>(movimientos.aniosConMovimientos(cuentaId));
        if (!anios.contains(c.getAnioActual())) {
            anios.add(0, c.getAnioActual());
        }

        BigDecimal saldo = BigDecimal.ZERO;
        BigDecimal saldoInicial = BigDecimal.ZERO;
        List<MovimientoFila> filas = new ArrayList<>();
        Map<String, BigDecimal> gastoPorCategoria = new LinkedHashMap<>();
        // La cuota de un peñista, una vez cobrada, se muestra en su propia fila
        // (no como movimiento aparte): guardamos su importe y el saldo tras ella.
        Map<Long, BigDecimal> cuotaImporte = new HashMap<>();
        Map<Long, BigDecimal> cuotaSaldoTras = new HashMap<>();
        Map<Long, Long> cuotaMovId = new HashMap<>();
        for (MovimientoCuenta m : movimientos.findByCuentaIdAndAnioOrderByFechaAscIdAsc(cuentaId, anio)) {
            saldo = saldo.add(m.getImporte());
            if (m.getOrigen() == OrigenMovimiento.SALDO_INICIAL) {
                saldoInicial = saldoInicial.add(m.getImporte());
            }
            if (m.getOrigen() == OrigenMovimiento.CUOTA && m.getFichaAsistenciaId() != null) {
                cuotaImporte.put(m.getFichaAsistenciaId(), m.getImporte());
                cuotaSaldoTras.put(m.getFichaAsistenciaId(), saldo);
                cuotaMovId.put(m.getFichaAsistenciaId(), m.getId());
            }
            filas.add(new MovimientoFila(m.getId(), m.getConcepto(),
                    m.getCategoria() != null ? m.getCategoria().legible() : null,
                    m.getImporte(), m.getFecha(), saldo, m.getReciboArchivo(),
                    m.getOrigen().esManual(), m.getOrigen().name(),
                    m.getAdelantadoPor() != null ? m.getAdelantadoPor().getNombre() : null));
            if (m.getOrigen() == OrigenMovimiento.GASTO && m.getCategoria() != null) {
                gastoPorCategoria.merge(m.getCategoria().legible(), m.getImporte().abs(), BigDecimal::add);
            }
        }

        List<FichaBebida> conCuota = fichas.findConCuotaDeCuenta(cuentaId);
        if (!esActual) {
            // En un año pasado, solo los peñistas cuya cuota entró ese año.
            conCuota = conCuota.stream()
                    .filter(f -> cuotaImporte.containsKey(f.getAsistenciaId()))
                    .toList();
        }
        List<PenistaCuota> penistas = conCuota.stream()
                .sorted(Comparator
                        .comparing((FichaBebida f) -> cuotaMovId.getOrDefault(f.getAsistenciaId(), Long.MAX_VALUE))
                        .thenComparing(CuentaService::nombreDe, String.CASE_INSENSITIVE_ORDER))
                .map(f -> aPenista(f, cuotaImporte.get(f.getAsistenciaId()),
                        cuotaSaldoTras.get(f.getAsistenciaId())))
                .toList();
        BigDecimal totalCuotas = conCuota.stream()
                .map(FichaBebida::getCuota).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCobrado = conCuota.stream()
                .filter(f -> f.getEstadoPago() != null && f.getEstadoPago().confirmado())
                .map(FichaBebida::getCuota).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal estimacion = esActual ? saldo.add(fichas.sumaCuotasPorEntrar(cuentaId)) : saldo;
        BigDecimal cobradoSinIngresar = (admin && esActual) ? fichas.sumaPorIngresar(cuentaId) : null;

        BigDecimal precioCamiseta = null;
        BigDecimal precioSudadera = null;
        for (Evento e : eventos.findByCuentaId(cuentaId)) {
            if (e.getPrecioCamiseta() != null) {
                precioCamiseta = e.getPrecioCamiseta();
            }
            if (e.getPrecioSudadera() != null) {
                precioSudadera = e.getPrecioSudadera();
            }
        }

        List<ResumenGasto> resumen = gastoPorCategoria.entrySet().stream()
                .map(en -> new ResumenGasto(en.getKey(), en.getValue()))
                .toList();

        return new CuentaDetalle(c.getId(), c.getNombre(), c.getDescripcion(),
                anio, anios, esActual,
                saldo, saldoInicial, estimacion, cobradoSinIngresar, admin,
                precioCamiseta, precioSudadera, penistas, totalCuotas, totalCobrado, filas, resumen);
    }

    /**
     * "Cerrar el año": el admin da por cerrado el año en curso. El saldo que
     * quede se apunta como saldo de partida del año siguiente ("Saldo del año
     * N"), y la cuenta pasa a ese año. 403 {@code SIN_PERMISO} si no es admin.
     */
    @Transactional
    public CuentaDetalle cerrarAnio(Long adminId, Long cuentaId) {
        if (!permisos.esAdministrador(adminId)) {
            throw new SinPermisoException();
        }
        Cuenta c = cuentas.findById(cuentaId).orElseThrow(CuentaNoEncontradaException::new);
        int anioCerrado = c.getAnioActual();
        int anioNuevo = anioCerrado + 1;
        BigDecimal saldoFinal = movimientos.sumImporteAnio(cuentaId, anioCerrado);

        movimientos.save(MovimientoCuenta.builder()
                .cuenta(c)
                .anio(anioNuevo)
                .concepto("Saldo del año " + anioCerrado)
                .importe(saldoFinal)
                .fecha(LocalDate.of(anioNuevo, 1, 1))
                .origen(OrigenMovimiento.SALDO_INICIAL)
                .build());
        c.setAnioActual(anioNuevo);
        cuentas.save(c);
        return detalle(adminId, cuentaId);
    }

    private static String nombreDe(FichaBebida f) {
        var a = f.getAsistencia();
        return a.getUsuario() != null ? a.getUsuario().getNombre() : a.getNombre();
    }

    private static PenistaCuota aPenista(FichaBebida f, BigDecimal ingreso, BigDecimal saldoTras) {
        boolean confirmado = f.getEstadoPago() != null && f.getEstadoPago().confirmado();
        int anio = f.getAsistencia().getEvento().getFecha().getYear();
        return new PenistaCuota(f.getAsistencia().getId(), nombreDe(f), anio, f.getCuota(),
                f.getEstadoPago() == null ? null : f.getEstadoPago().name(),
                confirmado && f.getMetodoPago() != null ? f.getMetodoPago().name() : null,
                f.getCamisetaCantidad(), f.getCamisetaTalla(), f.isCamisetaConfirmada(),
                f.getSudaderaCantidad(), f.getSudaderaTalla(), f.isSudaderaConfirmada(),
                ingreso, saldoTras);
    }

    /**
     * "He transferido el dinero a la peña": el admin ha pasado al banco lo cobrado
     * por bizum/efectivo. Solo apaga el aviso (las fichas {@code CONFIRMADO_PENDIENTE_ENVIO}
     * pasan a {@code CONFIRMADO_EN_CUENTA}); el saldo NO cambia, ese dinero ya
     * contaba desde que se confirmó. 403 {@code SIN_PERMISO} si no es admin.
     */
    @Transactional
    public CuentaDetalle marcarTransferido(Long adminId, Long cuentaId) {
        if (!permisos.esAdministrador(adminId)) {
            throw new SinPermisoException();
        }
        cuentas.findById(cuentaId).orElseThrow(CuentaNoEncontradaException::new);
        for (FichaBebida f : fichas.findByEstadoYCuenta(EstadoPagoCuota.CONFIRMADO_PENDIENTE_ENVIO, cuentaId)) {
            f.setEstadoPago(EstadoPagoCuota.CONFIRMADO_EN_CUENTA);
            fichas.save(f);
        }
        return detalle(adminId, cuentaId);
    }

    /** Alta de un gasto o ingreso manual. 403 si no es admin. */
    @Transactional
    public CuentaDetalle crearMovimiento(Long adminId, Long cuentaId, OrigenMovimiento tipo,
            String concepto, BigDecimal importe, LocalDate fecha, CategoriaMovimiento categoria,
            Long adelantadoPorId, String reciboArchivo) {
        if (!permisos.esAdministrador(adminId)) {
            throw new SinPermisoException();
        }
        Cuenta c = cuentas.findById(cuentaId).orElseThrow(CuentaNoEncontradaException::new);
        Usuario admin = usuarios.findById(adminId).orElseThrow();
        Usuario adelantadoPor = adelantadoPorId != null
                ? usuarios.findById(adelantadoPorId).orElse(null) : null;
        movimientoCuenta.crearManual(c, tipo, concepto, importe, fecha, categoria,
                adelantadoPor, admin, reciboArchivo);
        return detalle(adminId, cuentaId);
    }

    /**
     * El admin apunta cuánta camiseta / sudadera pide un peñista, de qué talla y
     * si ya ha confirmado que el dinero ha entrado. Cada campo {@code null} = "no
     * tocar". Al confirmar una prenda con cantidad, {@code precio (del evento) ×
     * cantidad} entra en el libro y suma al saldo (409 {@code SIN_PRECIO_ROPA} si
     * el evento no tiene precio); al desmarcar o poner cantidad 0, se quita.
     */
    @Transactional
    public CuentaDetalle marcarRopa(Long adminId, Long cuentaId, Long asistenciaId,
            Integer camisetaCantidad, String camisetaTalla, Boolean camisetaConfirmada,
            Integer sudaderaCantidad, String sudaderaTalla, Boolean sudaderaConfirmada) {
        if (!permisos.esAdministrador(adminId)) {
            throw new SinPermisoException();
        }
        FichaBebida f = fichas.findByAsistenciaId(asistenciaId)
                .orElseThrow(MovimientoNoEncontradoException::new);
        Usuario admin = usuarios.findById(adminId).orElseThrow();
        Evento e = f.getAsistencia().getEvento();

        if (camisetaCantidad != null) {
            f.setCamisetaCantidad(Math.max(0, camisetaCantidad));
        }
        if (camisetaTalla != null) {
            f.setCamisetaTalla(camisetaTalla.isBlank() ? null : camisetaTalla.trim());
        }
        if (sudaderaCantidad != null) {
            f.setSudaderaCantidad(Math.max(0, sudaderaCantidad));
        }
        if (sudaderaTalla != null) {
            f.setSudaderaTalla(sudaderaTalla.isBlank() ? null : sudaderaTalla.trim());
        }

        sincronizarRopa(f, OrigenMovimiento.CAMISETA, camisetaConfirmada, f.getCamisetaCantidad(),
                e.getPrecioCamiseta(), f.isCamisetaConfirmada(), f::setCamisetaConfirmada, admin);
        sincronizarRopa(f, OrigenMovimiento.SUDADERA, sudaderaConfirmada, f.getSudaderaCantidad(),
                e.getPrecioSudadera(), f.isSudaderaConfirmada(), f::setSudaderaConfirmada, admin);

        fichas.save(f);
        return detalle(adminId, cuentaId);
    }

    private void sincronizarRopa(FichaBebida f, OrigenMovimiento tipo, Boolean pedido, int cantidad,
            BigDecimal precio, boolean actual, java.util.function.Consumer<Boolean> setConfirmada,
            Usuario admin) {
        boolean quiere = pedido != null ? pedido : actual;
        if (quiere && cantidad > 0) {
            if (precio == null) {
                throw new com.baniterio.api.evento.SinPrecioRopaException();
            }
            movimientoCuenta.registrarRopa(f, tipo, precio.multiply(BigDecimal.valueOf(cantidad)), admin);
            setConfirmada.accept(true);
        } else {
            movimientoCuenta.revertirRopa(f, tipo);
            setConfirmada.accept(false);
        }
    }

    @Transactional
    public CuentaDetalle borrarMovimiento(Long adminId, Long movId) {
        if (!permisos.esAdministrador(adminId)) {
            throw new SinPermisoException();
        }
        MovimientoCuenta m = movimientos.findById(movId).orElseThrow(MovimientoNoEncontradoException::new);
        if (!m.getOrigen().esManual()) {
            throw new MovimientoNoManualException();
        }
        Long cuentaId = m.getCuenta().getId();
        movimientoCuenta.borrarManual(m);
        return detalle(adminId, cuentaId);
    }

    @Transactional(readOnly = true)
    public MovimientoCuenta movimiento(Long movId) {
        return movimientos.findById(movId).orElseThrow(MovimientoNoEncontradoException::new);
    }

    @Transactional
    public void guardarRecibo(Long adminId, Long movId, String reciboArchivo) {
        if (!permisos.esAdministrador(adminId)) {
            throw new SinPermisoException();
        }
        MovimientoCuenta m = movimientos.findById(movId).orElseThrow(MovimientoNoEncontradoException::new);
        movimientoCuenta.ponerRecibo(m, reciboArchivo);
    }
}
