package com.baniterio.api.inventario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link ArticuloInventario}. */
public interface ArticuloInventarioRepository extends JpaRepository<ArticuloInventario, Long> {

    /** Todos los artículos de la peña, ordenados por categoría (orden del enum) y luego alfabéticamente. */
    List<ArticuloInventario> findByPenaIdOrderByCategoriaAscNombreAsc(Long penaId);

    /** La fila de un producto concreto (misma categoría, nombre —sin distinguir mayúsculas— y tamaño). */
    Optional<ArticuloInventario> findByPenaIdAndCategoriaAndNombreIgnoreCaseAndTamano(
            Long penaId, CategoriaInventario categoria, String nombre, String tamano);
}
