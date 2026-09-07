package com.baniterio.api.cuenta;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.MovimientoCuenta;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.identidad.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe el libro de movimientos de una cuenta. Servicio fino: solo depende del
 * repositorio, así que lo pueden inyectar {@code AsistenciaService},
 * {@code PagoDeclaradoService} y {@code CuentaService} sin ciclos.
 */
@Service
public class MovimientoCuentaService {

    private final MovimientoCuentaRepository movimientos;

    public MovimientoCuentaService(MovimientoCuentaRepository movimientos) {
        this.movimientos = movimientos;
    }

    /** Registra en el libro que la cuota de {@code ficha} ha entrado en la cuenta. Idempotente por ficha. */
    @Transactional
    public void registrarCuota(FichaBebida ficha, Usuario admin) {
        Long asistenciaId = ficha.getAsistenciaId();
        if (movimientos.existsByFichaAsistenciaId(asistenciaId)) {
            return;
        }
        AsistenciaEvento a = ficha.getAsistencia();
        String quien = a.getUsuario() != null ? a.getUsuario().getNombre() : a.getNombre();
        movimientos.save(MovimientoCuenta.builder()
                .cuenta(a.getEvento().getCuenta())
                .concepto("Cuota de " + quien + " — " + a.getEvento().getNombre())
                .importe(ficha.getCuota())
                .fecha(LocalDate.now())
                .origen(OrigenMovimiento.CUOTA)
                .fichaAsistenciaId(asistenciaId)
                .creadoPor(admin)
                .build());
    }

    /** Deshace {@link #registrarCuota} (cuando un admin deshace un pago). */
    @Transactional
    public void revertirCuota(FichaBebida ficha) {
        movimientos.findByFichaAsistenciaId(ficha.getAsistenciaId())
                .ifPresent(movimientos::delete);
    }

    @Transactional(readOnly = true)
    public BigDecimal saldo(Long cuentaId) {
        return movimientos.sumImporte(cuentaId);
    }
}
