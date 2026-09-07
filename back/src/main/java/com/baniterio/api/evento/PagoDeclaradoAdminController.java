package com.baniterio.api.evento;

import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.evento.dto.PagoDeclaradoPendiente;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cola de "Confirmar pagos" del panel de administración: lista las declaraciones
 * de pago {@code PENDIENTE} y las confirma o rechaza. Solo admin/superadmin (lo
 * comprueba {@link PagoDeclaradoService} → 403 {@code SIN_PERMISO}).
 */
@RestController
@RequestMapping("/api/v1/admin/pagos-declarados")
public class PagoDeclaradoAdminController {

    private final PagoDeclaradoService pagoDeclaradoService;

    public PagoDeclaradoAdminController(PagoDeclaradoService pagoDeclaradoService) {
        this.pagoDeclaradoService = pagoDeclaradoService;
    }

    @GetMapping
    public List<PagoDeclaradoPendiente> pendientes(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return pagoDeclaradoService.pendientes(principal.id());
    }

    @PostMapping("/{id}/confirmar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        pagoDeclaradoService.confirmar(principal.id(), id);
    }

    @PostMapping("/{id}/rechazar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        pagoDeclaradoService.rechazar(principal.id(), id);
    }
}
