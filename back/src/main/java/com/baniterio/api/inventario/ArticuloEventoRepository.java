package com.baniterio.api.inventario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link ArticuloEvento} (inventario de la fiesta). */
public interface ArticuloEventoRepository extends JpaRepository<ArticuloEvento, Long> {

    /** Ordenado por categoría (orden del enum) y luego alfabéticamente. */
    List<ArticuloEvento> findByEventoIdOrderByCategoriaAscNombreAsc(Long eventoId);

    Optional<ArticuloEvento> findByEventoIdAndArticuloInventarioId(Long eventoId, Long articuloInventarioId);

    Optional<ArticuloEvento> findByEventoIdAndCategoriaAndNombreAndTamano(
            Long eventoId, CategoriaInventario categoria, String nombre, String tamano);
}
