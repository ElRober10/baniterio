package com.baniterio.api.evento;

import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.evento.dto.PagoDeclaradoPendiente;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Administración · pagos declarados", description = "Cola de \"Confirmar pagos\": confirmar o rechazar declaraciones de pago.")
@RestController
@RequestMapping("/api/v1/admin/pagos-declarados")
public class PagoDeclaradoAdminController {

    private final PagoDeclaradoService pagoDeclaradoService;

    public PagoDeclaradoAdminController(PagoDeclaradoService pagoDeclaradoService) {
        this.pagoDeclaradoService = pagoDeclaradoService;
    }

    /** Declaraciones de pago pendientes de confirmar. Solo admin/superadmin. */
    @GetMapping
    public List<PagoDeclaradoPendiente> pendientes(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return pagoDeclaradoService.pendientes(principal.id());
    }

    /** Confirma una declaración de pago pendiente (cuenta ya como pagado). Solo admin/superadmin. */
    @PostMapping("/{id}/confirmar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        pagoDeclaradoService.confirmar(principal.id(), id);
    }

    /** Rechaza una declaración de pago pendiente. Solo admin/superadmin. */
    @PostMapping("/{id}/rechazar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        pagoDeclaradoService.rechazar(principal.id(), id);
    }
}
