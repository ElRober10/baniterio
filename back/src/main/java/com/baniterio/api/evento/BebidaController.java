package com.baniterio.api.evento;

import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.evento.dto.BebidaPendiente;
import com.baniterio.api.evento.dto.CatalogoBebidas;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de bebidas ({@code GET /catalogo}, cualquier miembro) y su aprobación
 * ({@code GET ?estado=PENDIENTE}, {@code POST /{id}/aceptar|rechazar}, solo
 * admin). Solo traduce HTTP ↔ dominio.
 */
@Tag(name = "Bebidas", description = "Catálogo de bebidas y aprobación de las propuestas.")
@RestController
@RequestMapping("/api/v1/bebidas")
public class BebidaController {

    private final BebidaService bebidaService;

    public BebidaController(BebidaService bebidaService) {
        this.bebidaService = bebidaService;
    }

    /** Catálogo de bebidas aprobadas (cualquier miembro). */
    @GetMapping("/catalogo")
    public CatalogoBebidas catalogo() {
        return bebidaService.catalogo();
    }

    /** Bebidas propuestas pendientes de aprobar. Solo admin. */
    @GetMapping(params = "estado")
    public List<BebidaPendiente> pendientes(@AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam String estado) {
        // De momento solo se listan las PENDIENTE; el parámetro deja la puerta abierta.
        return bebidaService.pendientes(principal.id());
    }

    /** Acepta una bebida propuesta y la mete en el catálogo. Solo admin. */
    @PostMapping("/{id}/aceptar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void aceptar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        bebidaService.aceptar(principal.id(), id);
    }

    /** Rechaza una bebida propuesta. Solo admin. */
    @PostMapping("/{id}/rechazar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rechazar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        bebidaService.rechazar(principal.id(), id);
    }
}
