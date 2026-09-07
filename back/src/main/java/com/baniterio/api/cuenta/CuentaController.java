package com.baniterio.api.cuenta;

import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.cuenta.dto.CuentaDetalle;
import com.baniterio.api.cuenta.dto.CuentaResumen;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sección Cuentas para cualquier miembro: listado y detalle (con saldo,
 * estimación y libro de movimientos). El botón "He transferido el dinero a la
 * peña" ({@code POST .../transferencia-a-pena}) es solo para administradores.
 * Solo traduce HTTP ↔ dominio; errores → {@link com.baniterio.api.web.ApiExceptionHandler}.
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
    public CuentaDetalle detalle(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        return cuentaService.detalle(principal.id(), id);
    }

    @PostMapping("/{id}/transferencia-a-pena")
    public CuentaDetalle marcarTransferido(@AuthenticationPrincipal UsuarioPrincipal principal,
                                           @PathVariable Long id) {
        return cuentaService.marcarTransferido(principal.id(), id);
    }
}
