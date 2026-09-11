package com.baniterio.api.cuenta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.cuenta.dto.CuentaDetalle;
import com.baniterio.api.cuenta.dto.CuentaResumen;
import com.baniterio.api.cuenta.dto.MovimientoFila;
import com.baniterio.api.cuenta.dto.PenistaCuota;
import com.baniterio.api.cuenta.dto.ResumenGasto;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.MovimientoCuenta;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.identidad.PenaPilotoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura de la sección Cuentas: listar las cuentas de la peña y armar la hoja
 * de una cuenta (peñistas, libro de movimientos con saldo corriente y resumen
 * de gastos). Las mutaciones (cerrar año, movimientos manuales, ropa) están en
 * {@link CuentaAdminService}, que usa {@link #detalle} para devolver la hoja
 * actualizada tras cada cambio.
 */
@Service
public class CuentaConsultaService {

    /** Igual que {@code EventoService.DIAS_PARA_PASADO}: un evento cuenta como futuro hasta 3 días después de su fecha. */
    private static final int DIAS_PARA_PASADO = 3;

    private final CuentaRepository cuentas;
    private final EventoRepository eventos;
    private final PenaPilotoService pena;
    private final MovimientoCuentaRepository movimientos;
    private final FichaBebidaRepository fichas;
    private final ServicioPermisos permisos;

    public CuentaConsultaService(CuentaRepository cuentas, EventoRepository eventos, PenaPilotoService pena,
            MovimientoCuentaRepository movimientos, FichaBebidaRepository fichas, ServicioPermisos permisos) {
        this.cuentas = cuentas;
        this.eventos = eventos;
        this.pena = pena;
        this.movimientos = movimientos;
        this.fichas = fichas;
        this.permisos = permisos;
    }

    private Long penaId() {
        return pena.id();
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
                .map(CuentaConsultaService::aResumen)
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
                        .thenComparing(CuentaConsultaService::nombreDe, String.CASE_INSENSITIVE_ORDER))
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

    @Transactional(readOnly = true)
    public MovimientoCuenta movimiento(Long movId) {
        return movimientos.findById(movId).orElseThrow(MovimientoNoEncontradoException::new);
    }
}
