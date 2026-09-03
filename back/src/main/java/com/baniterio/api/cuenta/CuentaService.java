package com.baniterio.api.cuenta;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baniterio.api.cuenta.dto.CuentaResumen;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.PenaRepository;
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

    public CuentaService(CuentaRepository cuentas, EventoRepository eventos, PenaRepository penas) {
        this.cuentas = cuentas;
        this.eventos = eventos;
        this.penas = penas;
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
    public CuentaResumen detalle(Long cuentaId) {
        return cuentas.findById(cuentaId)
                .map(CuentaService::aResumen)
                .orElseThrow(CuentaNoEncontradaException::new);
    }
}
