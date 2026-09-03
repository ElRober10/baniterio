package com.baniterio.api.cuenta;

import java.util.List;

import com.baniterio.api.cuenta.dto.CuentaResumen;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.PenaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de la sección Cuentas: de momento solo listar y ver el detalle. Crear,
 * editar, borrar y los movimientos llegan en tareas siguientes. La peña se
 * resuelve por slug como en {@code EventoService}.
 */
@Service
public class CuentaService {

    private static final String SLUG_PENA = "baniterio";

    private final CuentaRepository cuentas;
    private final PenaRepository penas;

    public CuentaService(CuentaRepository cuentas, PenaRepository penas) {
        this.cuentas = cuentas;
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

    @Transactional(readOnly = true)
    public List<CuentaResumen> listar() {
        return cuentas.findByPenaIdOrderByNombreAsc(penaId()).stream()
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
