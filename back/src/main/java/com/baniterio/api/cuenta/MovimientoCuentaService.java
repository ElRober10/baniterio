package com.baniterio.api.cuenta;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.CategoriaMovimiento;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.MovimientoCuenta;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.media.AlmacenRecibos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe el libro de movimientos de una cuenta. Servicio fino: depende solo del
 * repositorio y del almacén de recibos, así que lo pueden inyectar
 * {@code AsistenciaService}, {@code PagoDeclaradoService} y {@code CuentaService}
 * sin ciclos.
 */
@Service
public class MovimientoCuentaService {

    private final MovimientoCuentaRepository movimientos;
    private final AlmacenRecibos recibos;

    public MovimientoCuentaService(MovimientoCuentaRepository movimientos, AlmacenRecibos recibos) {
        this.movimientos = movimientos;
        this.recibos = recibos;
    }

    // ---- Cuota (automático al confirmar un pago) ----

    /** Apunta que la cuota de {@code ficha} ha entrado en la cuenta. Idempotente por ficha. */
    @Transactional
    public void registrarCuota(FichaBebida ficha, Usuario admin) {
        registrarDeFicha(ficha, OrigenMovimiento.CUOTA, ficha.getCuota(), "Cuota de ", admin);
    }

    @Transactional
    public void revertirCuota(FichaBebida ficha) {
        revertirDeFicha(ficha, OrigenMovimiento.CUOTA);
    }

    // ---- Ropa (automático al marcar la casilla) ----

    @Transactional
    public void registrarRopa(FichaBebida ficha, OrigenMovimiento tipo, BigDecimal precio, Usuario admin) {
        String prefijo = tipo == OrigenMovimiento.CAMISETA ? "Camiseta de " : "Sudadera de ";
        registrarDeFicha(ficha, tipo, precio, prefijo, admin);
    }

    @Transactional
    public void revertirRopa(FichaBebida ficha, OrigenMovimiento tipo) {
        revertirDeFicha(ficha, tipo);
    }

    // ---- Gastos / ingresos manuales ----

    @Transactional
    public MovimientoCuenta crearManual(Cuenta cuenta, OrigenMovimiento tipo, String concepto,
            BigDecimal importe, LocalDate fecha, CategoriaMovimiento categoria,
            Usuario adelantadoPor, Usuario admin, String reciboArchivo) {
        BigDecimal signo = tipo == OrigenMovimiento.GASTO ? importe.abs().negate() : importe.abs();
        return movimientos.save(MovimientoCuenta.builder()
                .cuenta(cuenta)
                .concepto(concepto.trim())
                .importe(signo)
                .fecha(fecha != null ? fecha : LocalDate.now())
                .origen(tipo)
                .categoria(categoria)
                .adelantadoPor(adelantadoPor)
                .reciboArchivo(reciboArchivo)
                .creadoPor(admin)
                .build());
    }

    @Transactional
    public MovimientoCuenta ponerRecibo(MovimientoCuenta m, String reciboArchivo) {
        if (m.getReciboArchivo() != null) {
            recibos.borrar(m.getReciboArchivo());
        }
        m.setReciboArchivo(reciboArchivo);
        return movimientos.save(m);
    }

    @Transactional
    public void borrarManual(MovimientoCuenta m) {
        if (m.getReciboArchivo() != null) {
            recibos.borrar(m.getReciboArchivo());
        }
        movimientos.delete(m);
    }

    // ---- Lectura ----

    @Transactional(readOnly = true)
    public BigDecimal saldo(Long cuentaId) {
        return movimientos.sumImporte(cuentaId);
    }

    // ---- Helpers ----

    private void registrarDeFicha(FichaBebida ficha, OrigenMovimiento origen, BigDecimal importe,
            String prefijoConcepto, Usuario admin) {
        Long asistenciaId = ficha.getAsistenciaId();
        if (importe == null || movimientos.existsByFichaAsistenciaIdAndOrigen(asistenciaId, origen)) {
            return;
        }
        AsistenciaEvento a = ficha.getAsistencia();
        String quien = a.getUsuario() != null ? a.getUsuario().getNombre() : a.getNombre();
        movimientos.save(MovimientoCuenta.builder()
                .cuenta(a.getEvento().getCuenta())
                .concepto(prefijoConcepto + quien + " — " + a.getEvento().getNombre())
                .importe(importe)
                .fecha(LocalDate.now())
                .origen(origen)
                .fichaAsistenciaId(asistenciaId)
                .creadoPor(admin)
                .build());
    }

    private void revertirDeFicha(FichaBebida ficha, OrigenMovimiento origen) {
        movimientos.findByFichaAsistenciaIdAndOrigen(ficha.getAsistenciaId(), origen)
                .ifPresent(movimientos::delete);
    }
}
