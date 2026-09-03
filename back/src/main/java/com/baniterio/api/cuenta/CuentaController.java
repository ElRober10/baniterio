package com.baniterio.api.cuenta;

import java.util.List;

import com.baniterio.api.cuenta.dto.CuentaResumen;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sección Cuentas para cualquier miembro: listado y detalle. Crear, editar,
 * borrar y los movimientos se añaden en tareas siguientes. Solo traduce
 * HTTP ↔ dominio; errores → {@link com.baniterio.api.web.ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentaController {

    private final CuentaService cuentaService;

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @GetMapping
    public List<CuentaResumen> listar() {
        return cuentaService.listar();
    }

    @GetMapping("/{id}")
    public CuentaResumen detalle(@PathVariable Long id) {
        return cuentaService.detalle(id);
    }
}
