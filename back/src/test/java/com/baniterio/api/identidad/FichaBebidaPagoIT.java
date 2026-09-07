package com.baniterio.api.identidad;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Las 4 columnas de pago de V26 se persisten y se leen en `ficha_bebida`. */
class FichaBebidaPagoIT extends IntegrationTest {

    @Autowired FichaBebidaRepository fichas;
    @Autowired AsistenciaEventoRepository asistencias;
    @Autowired EventoRepository eventos;
    @Autowired CuentaRepository cuentas;
    @Autowired PenaRepository penas;
    @Autowired BebidaRepository bebidas;

    private Long fichaId;
    private Long eventoId;

    @AfterEach
    void limpiar() {
        if (fichaId != null) {
            fichas.deleteById(fichaId);
        }
        if (eventoId != null) {
            asistencias.deleteAll(asistencias.findByEventoId(eventoId));
            eventos.deleteById(eventoId);
        }
    }

    @Test
    void guarda_y_lee_el_estado_de_pago() {
        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        Cuenta cuenta = cuentas.findByPenaIdAndNombre(pena.getId(), "San Miguel").orElseThrow();
        Evento e = eventos.save(Evento.builder().pena(pena).cuenta(cuenta)
                .nombre("IT-pago-" + ThreadLocalRandom.current().nextInt(1_000_000))
                .fecha(LocalDate.now().plusDays(10)).build());
        eventoId = e.getId();
        AsistenciaEvento a = asistencias.save(AsistenciaEvento.builder()
                .evento(e).nombre("Invitado").estado(EstadoAsistencia.APUNTADO).build());
        Bebida refresco = bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.REFRESCO, "Coca-Cola").orElseThrow();

        FichaBebida f = fichas.save(FichaBebida.builder()
                .asistencia(a).refresco(refresco)
                .alternativa(Alternativa.NADA).modalidad(Modalidad.SOLO_CERVEZA)
                .asisteDia1(true).asisteDia2(true)
                .cuota(new BigDecimal("16.00"))
                .estadoPago(EstadoPagoCuota.CONFIRMADO_EN_CUENTA)
                .metodoPago(MetodoPago.BIZUM).pagadoAt(Instant.now())
                .build());
        fichaId = f.getAsistenciaId();

        FichaBebida leida = fichas.findById(fichaId).orElseThrow();
        assertThat(leida.getEstadoPago()).isEqualTo(EstadoPagoCuota.CONFIRMADO_EN_CUENTA);
        assertThat(leida.getMetodoPago()).isEqualTo(MetodoPago.BIZUM);
        assertThat(leida.getPagadoAt()).isNotNull();
    }
}
