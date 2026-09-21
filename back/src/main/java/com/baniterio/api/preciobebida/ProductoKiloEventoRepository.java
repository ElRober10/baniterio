package com.baniterio.api.preciobebida;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a BBDD para {@link ProductoKiloEvento}. */
public interface ProductoKiloEventoRepository extends JpaRepository<ProductoKiloEvento, Long> {

    List<ProductoKiloEvento> findByEventoId(Long eventoId);

    Optional<ProductoKiloEvento> findByEventoIdAndNombreArticulo(Long eventoId, String nombreArticulo);

    /** Lo apuntado en cualquier evento de la misma peña que {@code eventoId}, del más antiguo al más nuevo. */
    @Query("select p from ProductoKiloEvento p "
            + "where p.evento.pena.id = (select e.pena.id from Evento e where e.id = :eventoId) order by p.id")
    List<ProductoKiloEvento> findDeLaPenaDelEvento(@Param("eventoId") Long eventoId);
}
