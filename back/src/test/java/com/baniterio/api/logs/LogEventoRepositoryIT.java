package com.baniterio.api.logs;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class LogEventoRepositoryIT extends IntegrationTest {

    @Autowired LogEventoRepository logs;
    @Autowired PenaRepository penas;

    @AfterEach
    void limpiar() {
        logs.deleteAllInBatch();
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    @Test
    void guardaYBuscaPorPenaOrigenYFecha() {
        Instant ahora = Instant.now();
        logs.save(LogEvento.builder().pena(pena()).origen(OrigenLog.BACKEND)
                .metodo("POST").ruta("/api/v1/cuentas/1/movimientos").estado(200).build());
        logs.save(LogEvento.builder().pena(pena()).origen(OrigenLog.MOBILE)
                .ruta("cuentas.crearMovimiento").mensaje("SinConexion").build());

        Page<LogEvento> soloMobile = logs.buscar(pena().getId(), null, OrigenLog.MOBILE, null, null,
                PageRequest.of(0, 10));
        assertThat(soloMobile.getTotalElements()).isEqualTo(1);
        assertThat(soloMobile.getContent().get(0).getRuta()).isEqualTo("cuentas.crearMovimiento");
        assertThat(soloMobile.getContent().get(0).getCreadoEn()).isNotNull();

        Page<LogEvento> desdeManana = logs.buscar(pena().getId(), null, null,
                ahora.plus(1, ChronoUnit.DAYS), null, PageRequest.of(0, 10));
        assertThat(desdeManana.getTotalElements()).isZero();

        List<LogEvento> todos = logs.buscar(pena().getId(), null, null, null, null,
                PageRequest.of(0, 10)).getContent();
        assertThat(todos).hasSize(2);
    }
}
