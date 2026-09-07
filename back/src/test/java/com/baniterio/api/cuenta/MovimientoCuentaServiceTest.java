package com.baniterio.api.cuenta;

import java.math.BigDecimal;
import java.util.Optional;

import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.EstadoPagoCuota;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.MovimientoCuenta;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.media.AlmacenRecibos;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovimientoCuentaServiceTest {

    private final MovimientoCuentaRepository repo = mock(MovimientoCuentaRepository.class);
    private final AlmacenRecibos recibos = mock(AlmacenRecibos.class);
    private final MovimientoCuentaService service = new MovimientoCuentaService(repo, recibos);

    private FichaBebida ficha(long asisId, String cuota) {
        Cuenta cuenta = Cuenta.builder().id(9L).nombre("San Miguel").build();
        Evento e = Evento.builder().id(3L).nombre("San Miguel 2026").cuenta(cuenta).build();
        AsistenciaEvento a = AsistenciaEvento.builder().id(asisId).evento(e)
                .usuario(Usuario.builder().id(1L).nombre("Rober").build()).build();
        return FichaBebida.builder().asistenciaId(asisId).asistencia(a)
                .cuota(new BigDecimal(cuota)).estadoPago(EstadoPagoCuota.CONFIRMADO_EN_CUENTA).build();
    }

    @Test
    void registrarCuota_inserta_un_movimiento_con_el_importe_de_la_cuota() {
        when(repo.existsByFichaAsistenciaIdAndOrigen(7L, OrigenMovimiento.CUOTA)).thenReturn(false);

        service.registrarCuota(ficha(7L, "45.00"), null);

        verify(repo).save(argThat(m ->
                m.getImporte().compareTo(new BigDecimal("45.00")) == 0
                        && m.getOrigen() == OrigenMovimiento.CUOTA
                        && m.getFichaAsistenciaId().equals(7L)
                        && m.getCuenta().getId().equals(9L)));
    }

    @Test
    void registrarCuota_no_duplica_si_ya_hay_movimiento_para_esa_ficha() {
        when(repo.existsByFichaAsistenciaIdAndOrigen(7L, OrigenMovimiento.CUOTA)).thenReturn(true);
        service.registrarCuota(ficha(7L, "45.00"), null);
        verify(repo, never()).save(any());
    }

    @Test
    void revertirCuota_borra_el_movimiento_si_existe() {
        MovimientoCuenta m = MovimientoCuenta.builder().id(1L).build();
        when(repo.findByFichaAsistenciaIdAndOrigen(7L, OrigenMovimiento.CUOTA)).thenReturn(Optional.of(m));
        service.revertirCuota(ficha(7L, "45.00"));
        verify(repo).delete(m);
    }
}
