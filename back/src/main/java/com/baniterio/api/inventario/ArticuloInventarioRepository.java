package com.baniterio.api.inventario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link ArticuloInventario}. */
public interface ArticuloInventarioRepository extends JpaRepository<ArticuloInventario, Long> {

    /**
     * Todos los artículos de la peña, ordenados por categoría (orden del enum),
     * luego por {@code orden} dentro de la categoría, luego por nombre.
     */
    List<ArticuloInventario> findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(Long penaId);
}
