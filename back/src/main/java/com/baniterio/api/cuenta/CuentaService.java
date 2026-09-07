package com.baniterio.api.cuenta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baniterio.api.admin.SinPermisoException;
import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.cuenta.dto.CuentaDetalle;
import com.baniterio.api.cuenta.dto.CuentaResumen;
import com.baniterio.api.cuenta.dto.MovimientoFila;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoPagoCuota;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.MovimientoCuenta;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
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
        Cuenta c = cuentas.findById(cuentaId).orElseThrow(CuentaNoEncontradaException::new);
        boolean admin = permisos.esAdministrador(usuarioId);

        BigDecimal saldo = BigDecimal.ZERO;
        List<MovimientoFila> filas = new ArrayList<>();
        for (MovimientoCuenta m : movimientos.findByCuentaIdOrderByFechaAscIdAsc(cuentaId)) {
            saldo = saldo.add(m.getImporte());
            filas.add(new MovimientoFila(m.getConcepto(), m.getImporte(), m.getFecha(), saldo));
        }
        BigDecimal estimacion = saldo.add(fichas.sumaCuotasPorEntrar(cuentaId));
        BigDecimal porIngresar = admin ? fichas.sumaPorIngresar(cuentaId) : null;

        return new CuentaDetalle(c.getId(), c.getNombre(), c.getDescripcion(),
                saldo, estimacion, filas, admin, porIngresar);
    }

    /**
     * El admin marca que ha pasado a la cuenta de la peña todo lo que había
     * cobrado por bizum/efectivo: las fichas {@code CONFIRMADO_PENDIENTE_ENVIO}
     * de esa cuenta pasan a {@code CONFIRMADO_EN_CUENTA} y cada una añade su
     * movimiento al libro. 403 {@code SIN_PERMISO} si no es admin.
     */
    @Transactional
    public CuentaDetalle marcarTransferido(Long adminId, Long cuentaId) {
        if (!permisos.esAdministrador(adminId)) {
            throw new SinPermisoException();
        }
        cuentas.findById(cuentaId).orElseThrow(CuentaNoEncontradaException::new);
        Usuario admin = usuarios.findById(adminId).orElseThrow();
        for (FichaBebida f : fichas.findByEstadoYCuenta(EstadoPagoCuota.CONFIRMADO_PENDIENTE_ENVIO, cuentaId)) {
            f.setEstadoPago(EstadoPagoCuota.CONFIRMADO_EN_CUENTA);
            fichas.save(f);
            movimientoCuenta.registrarCuota(f, admin);
        }
        return detalle(adminId, cuentaId);
    }
}
