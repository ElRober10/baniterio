package com.baniterio.api.compra;

import java.util.List;
import java.util.Optional;

import com.baniterio.api.inventario.CategoriaInventario;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link ReglaCompra} (plantilla global). */
public interface ReglaCompraRepository extends JpaRepository<ReglaCompra, Long> {

    List<ReglaCompra> findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(Long penaId);

    Optional<ReglaCompra> findByPenaIdAndCategoriaAndNombreAndTipoFormula(
            Long penaId, CategoriaInventario categoria, String nombre, TipoFormulaCompra tipoFormula);
}
