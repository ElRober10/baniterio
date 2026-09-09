package com.baniterio.api.inventario;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.inventario.dto.ActualizarArticuloRequest;
import com.baniterio.api.inventario.dto.ArticuloDto;
import com.baniterio.api.inventario.dto.InventarioResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sección Inventario: consultar lo almacenado (cualquier peñista) y ajustar un
 * artículo (área {@code INVENTARIO}, se comprueba en el servicio). De momento
 * solo se editan artículos que ya existen; el alta/baja llega después.
 */
@Tag(name = "Inventario", description = "Material y bebida almacenados por la peña, por categorías.")
@RestController
@RequestMapping("/api/v1/inventario")
public class InventarioController {

    private final InventarioService service;

    public InventarioController(InventarioService service) {
        this.service = service;
    }

    /** El inventario completo agrupado por categoría. {@code puedoEditar} dice si quien pregunta puede tocar. */
    @GetMapping
    public InventarioResponse ver(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return service.ver(principal.id());
    }

    /** Cambia nombre, tamaño y cantidad de un artículo. Área {@code INVENTARIO}. */
    @PutMapping("/{id}")
    public ArticuloDto actualizar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody ActualizarArticuloRequest req) {
        return service.actualizar(principal.id(), id, req);
    }
}
