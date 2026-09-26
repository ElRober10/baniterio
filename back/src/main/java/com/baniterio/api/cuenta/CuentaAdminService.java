package com.baniterio.api.cuenta;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baniterio.api.admin.SinPermisoException;
import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.cuenta.dto.CuentaDetalle;
import com.baniterio.api.identidad.CategoriaMovimiento;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoPagoCuota;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.FichaBebidaRepository;
import com.baniterio.api.identidad.MovimientoCuenta;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mutaciones de la sección Cuentas: cerrar el año, marcar la transferencia al
 * banco, alta/baja de movimientos manuales y la ropa (camiseta/sudadera) de
 * cada peñista. Todas exigen admin (403 {@code SIN_PERMISO} si no) y devuelven
 * la hoja actualizada vía {@link CuentaConsultaService#detalle}.
 */
@Service
public class CuentaAdminService {

    private final CuentaRepository cuentas;
    private final MovimientoCuentaRepository movimientos;
    private final MovimientoCuentaService movimientoCuenta;
    private final FichaBebidaRepository fichas;
    private final UsuarioRepository usuarios;
    private final ServicioPermisos permisos;
    private final CuentaConsultaService consulta;

    public CuentaAdminService(CuentaRepository cuentas, MovimientoCuentaRepository movimientos,
            MovimientoCuentaService movimientoCuenta, FichaBebidaRepository fichas,
            UsuarioRepository usuarios, ServicioPermisos permisos, CuentaConsultaService consulta) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.movimientoCuenta = movimientoCuenta;
        this.fichas = fichas;
        this.usuarios = usuarios;
        this.permisos = permisos;
        this.consulta = consulta;
    }

    /**
     * "Cerrar el año": el admin da por cerrado el año en curso. El saldo que
     * quede se apunta como saldo de partida del año siguiente ("Saldo del año
     * N"), y la cuenta pasa a ese año. 403 {@code SIN_PERMISO} si no es admin.
     */
    @Transactional
    public CuentaDetalle cerrarAnio(Long adminId, Long cuentaId) {
        permisos.exigirAdmin(adminId, SinPermisoException::new);
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
        return consulta.detalle(adminId, cuentaId);
    }

    /**
     * "He transferido el dinero a la peña": el admin ha pasado al banco lo cobrado
     * por bizum/efectivo. Solo apaga el aviso (las fichas {@code CONFIRMADO_PENDIENTE_ENVIO}
     * pasan a {@code CONFIRMADO_EN_CUENTA}); el saldo NO cambia, ese dinero ya
     * contaba desde que se confirmó. 403 {@code SIN_PERMISO} si no es admin.
     */
    @Transactional
    public CuentaDetalle marcarTransferido(Long adminId, Long cuentaId) {
        permisos.exigirAdmin(adminId, SinPermisoException::new);
        cuentas.findById(cuentaId).orElseThrow(CuentaNoEncontradaException::new);
        for (FichaBebida f : fichas.findByEstadoYCuenta(EstadoPagoCuota.CONFIRMADO_PENDIENTE_ENVIO, cuentaId)) {
            f.setEstadoPago(EstadoPagoCuota.CONFIRMADO_EN_CUENTA);
            f.setImportePendienteEnvio(null);
            fichas.save(f);
        }
        return consulta.detalle(adminId, cuentaId);
    }

    /** Alta de un gasto o ingreso manual. 403 si no es admin. */
    @Transactional
    public CuentaDetalle crearMovimiento(Long adminId, Long cuentaId, OrigenMovimiento tipo,
            String concepto, BigDecimal importe, LocalDate fecha, CategoriaMovimiento categoria,
            Long adelantadoPorId, String reciboArchivo) {
        permisos.exigirAdmin(adminId, SinPermisoException::new);
        Cuenta c = cuentas.findById(cuentaId).orElseThrow(CuentaNoEncontradaException::new);
        Usuario admin = usuarios.findById(adminId).orElseThrow();
        Usuario adelantadoPor = adelantadoPorId != null
                ? usuarios.findById(adelantadoPorId).orElse(null) : null;
        movimientoCuenta.crearManual(c, tipo, concepto, importe, fecha, categoria,
                adelantadoPor, admin, reciboArchivo);
        return consulta.detalle(adminId, cuentaId);
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
        permisos.exigirAdmin(adminId, SinPermisoException::new);
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
        return consulta.detalle(adminId, cuentaId);
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
        permisos.exigirAdmin(adminId, SinPermisoException::new);
        MovimientoCuenta m = movimientos.findById(movId).orElseThrow(MovimientoNoEncontradoException::new);
        if (!m.getOrigen().esManual()) {
            throw new MovimientoNoManualException();
        }
        Long cuentaId = m.getCuenta().getId();
        movimientoCuenta.borrarManual(m);
        return consulta.detalle(adminId, cuentaId);
    }

    @Transactional
    public void guardarRecibo(Long adminId, Long movId, String reciboArchivo) {
        permisos.exigirAdmin(adminId, SinPermisoException::new);
        MovimientoCuenta m = movimientos.findById(movId).orElseThrow(MovimientoNoEncontradoException::new);
        movimientoCuenta.ponerRecibo(m, reciboArchivo);
    }
}
