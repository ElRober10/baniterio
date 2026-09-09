package com.baniterio.api.inventario;

import java.util.Arrays;
import java.util.List;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.inventario.dto.ActualizarArticuloRequest;
import com.baniterio.api.inventario.dto.ArticuloDto;
import com.baniterio.api.inventario.dto.CategoriaInventarioDto;
import com.baniterio.api.inventario.dto.InventarioResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sección Inventario: montar la hoja de listado y actualizar un artículo. La
 * peña es la piloto ({@code slug = "baniterio"}), igual que en
 * {@code CuentaService}. Ver un artículo lo puede cualquier usuario logueado;
 * editarlo exige el área {@link AreaProtegida#INVENTARIO}.
 */
@Service
public class InventarioService {

    private static final String SLUG_PENA = "baniterio";

    private final ArticuloInventarioRepository articulos;
    private final PenaRepository penas;
    private final ServicioPermisos permisos;

    public InventarioService(ArticuloInventarioRepository articulos, PenaRepository penas,
                             ServicioPermisos permisos) {
        this.articulos = articulos;
        this.penas = penas;
        this.permisos = permisos;
    }

    private Long penaId() {
        return penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
    }

    @Transactional(readOnly = true)
    public InventarioResponse ver(Long usuarioId) {
        boolean puedoEditar = permisos.puede(usuarioId, AreaProtegida.INVENTARIO);
        List<ArticuloInventario> filas =
                articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId());

        List<CategoriaInventarioDto> categorias = Arrays.stream(CategoriaInventario.values())
                .map(cat -> new CategoriaInventarioDto(
                        cat.name(),
                        cat.etiqueta(),
                        cat.tamanos(),
                        filas.stream()
                                .filter(a -> a.getCategoria() == cat)
                                .map(ArticuloDto::de)
                                .toList()))
                .toList();

        return new InventarioResponse(puedoEditar, categorias);
    }

    @Transactional
    public ArticuloDto actualizar(Long usuarioId, Long articuloId, ActualizarArticuloRequest req) {
        if (!permisos.puede(usuarioId, AreaProtegida.INVENTARIO)) {
            throw new SinPermisoInventarioException();
        }
        ArticuloInventario art = articulos.findById(articuloId)
                .filter(a -> a.getPena().getId().equals(penaId()))
                .orElseThrow(ArticuloInventarioNoEncontradoException::new);

        if (!art.getCategoria().permiteTamano(req.tamano())) {
            throw new TamanoInventarioNoValidoException();
        }

        art.setNombre(req.nombre().trim());
        art.setTamano(req.tamano());
        art.setCantidad(req.cantidad());
        return ArticuloDto.de(articulos.save(art));
    }
}
