package com.baniterio.api.preciobebida;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a BBDD para {@link TamanoArticuloEvento}. */
public interface TamanoArticuloEventoRepository extends JpaRepository<TamanoArticuloEvento, Long> {

    List<TamanoArticuloEvento> findByEventoId(Long eventoId);

    /** Los tamaños apuntados en cualquier evento de la misma peña que {@code eventoId}, del más antiguo al más nuevo. */
    @Query("select t from TamanoArticuloEvento t "
            + "where t.evento.pena.id = (select e.pena.id from Evento e where e.id = :eventoId) order by t.id")
    List<TamanoArticuloEvento> findDeLaPenaDelEvento(@Param("eventoId") Long eventoId);

    Optional<TamanoArticuloEvento> findByEventoIdAndNombreArticulo(Long eventoId, String nombreArticulo);
}
