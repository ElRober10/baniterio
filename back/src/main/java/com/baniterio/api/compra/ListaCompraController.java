package com.baniterio.api.compra;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.compra.dto.CambiarBloqueoRequest;
import com.baniterio.api.compra.dto.ListaCompraResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lista de la compra calculada de un evento. La ve cualquier peñista logueado. */
@Tag(name = "Lista de la compra", description = "Lista de la compra calculada por evento.")
@RestController
@RequestMapping("/api/v1/eventos")
public class ListaCompraController {

    private final ListaCompraService service;

    public ListaCompraController(ListaCompraService service) {
        this.service = service;
    }

    @GetMapping("/{eventoId}/lista-compra")
    public ListaCompraResponse verLista(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId) {
        return service.verLista(principal.id(), eventoId);
    }

    /** Marca una línea como comprada: pasa al inventario de la fiesta. Área {@code INVENTARIO}. */
    @PostMapping("/{eventoId}/lista-compra/lineas/{lineaId}/comprado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void comprado(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable Long lineaId) {
        service.marcarComprada(principal.id(), eventoId, lineaId);
    }

    /** Bloquea o desbloquea el auto-cálculo de la lista de la compra. Área {@code INVENTARIO}. */
    @PutMapping("/{eventoId}/lista-compra/bloqueo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bloqueo(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @RequestBody CambiarBloqueoRequest req) {
        service.cambiarBloqueo(principal.id(), eventoId, req.bloqueada());
    }
}
