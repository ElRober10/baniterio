package com.baniterio.api.compra;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.inventario.CategoriaInventario;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LineaCompraEventoRepositoryIT extends IntegrationTest {

    @Autowired LineaCompraEventoRepository lineas;
    @Autowired EventoRepository eventos;
    @Autowired CuentaRepository cuentas;
    @Autowired PenaRepository penas;

    @Test
    void guarda_y_recupera_una_linea_del_evento() {
        var pena = penas.findBySlug("baniterio").orElseThrow();
        Cuenta c = cuentas.save(Cuenta.builder().pena(pena).nombre("LCE cuenta")
                .llevaFichaBebida(false).build());
        Evento e = eventos.save(Evento.builder().pena(pena).cuenta(c).nombre("LCE evento")
                .fecha(LocalDate.now().minusDays(90)).oculto(false).build());

        assertThat(e.isListaCompraBloqueada()).isFalse();

        lineas.save(LineaCompraEvento.builder()
                .evento(e).categoria(CategoriaInventario.COMIDA).nombre("Picos").tamano("paquete")
                .cantidad(new BigDecimal("4.00")).orden(1).build());

        var guardadas = lineas.findByEventoIdOrderByCategoriaAscNombreAsc(e.getId());
        assertThat(guardadas).singleElement()
                .satisfies(l -> {
                    assertThat(l.getNombre()).isEqualTo("Picos");
                    assertThat(l.isComprada()).isFalse();
                    assertThat(l.getArticuloEventoId()).isNull();
                });
    }
}
