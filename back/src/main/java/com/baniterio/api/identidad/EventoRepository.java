package com.baniterio.api.identidad;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a BBDD para {@link Evento}. */
public interface EventoRepository extends JpaRepository<Evento, Long> {

    /**
     * Listado de la peña con el orden de la sección Eventos:
     * <ol>
     *   <li>primero los que aún no han pasado (grupo 0), luego los pasados (grupo 1).
     *       Un evento es "futuro" mientras {@code coalesce(fechaFin, fecha) >= limite},
     *       donde {@code limite = hoy - 3 días}; es "pasado" cuando han pasado 3 días.</li>
     *   <li>entre los futuros, por {@code fecha} ascendente (primero el más cercano a llegar);</li>
     *   <li>entre los pasados, por {@code fecha} descendente (más reciente antes que más antiguo);</li>
     *   <li>{@code id} descendente como desempate.</li>
     * </ol>
     * El {@link Pageable} debe venir <b>sin</b> {@code Sort}: el orden lo fija esta consulta.
     */
    @Query("""
            select e from Evento e
            join fetch e.cuenta
            where e.pena.id = :penaId
            order by
              case when coalesce(e.fechaFin, e.fecha) >= :limite then 0 else 1 end,
              case when coalesce(e.fechaFin, e.fecha) >= :limite then e.fecha end asc,
              e.fecha desc,
              e.id desc
            """)
    Page<Evento> listar(@Param("penaId") Long penaId, @Param("limite") LocalDate limite, Pageable pageable);

    /**
     * Eventos de la peña que aún no han pasado ({@code coalesce(fechaFin, fecha) >= limite}),
     * con la cuenta cargada. Lo usa {@code CuentaService} para ordenar las cuentas por
     * el evento futuro más próximo de cada una.
     */
    @Query("""
            select e from Evento e
            join fetch e.cuenta
            where e.pena.id = :penaId and coalesce(e.fechaFin, e.fecha) >= :limite
            """)
    List<Evento> futuros(@Param("penaId") Long penaId, @Param("limite") LocalDate limite);
}
