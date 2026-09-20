package com.baniterio.api.preciobebida;

import java.util.List;
import java.util.Optional;

import com.baniterio.api.inventario.CategoriaInventario;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link PrecioArticuloEvento}. */
public interface PrecioArticuloEventoRepository extends JpaRepository<PrecioArticuloEvento, Long> {

    List<PrecioArticuloEvento> findByEventoId(Long eventoId);

    List<PrecioArticuloEvento> findByEventoIdAndNombreArticuloIn(Long eventoId, List<String> nombres);

    Optional<PrecioArticuloEvento> findByEventoIdAndCategoriaAndNombreArticuloAndTiendaId(
            Long eventoId, CategoriaInventario categoria, String nombreArticulo, Long tiendaId);
}
