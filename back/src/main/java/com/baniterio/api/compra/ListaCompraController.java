package com.baniterio.api.compra;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.compra.dto.ListaCompraResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
