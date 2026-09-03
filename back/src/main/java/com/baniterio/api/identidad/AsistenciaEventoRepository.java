package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a BBDD para {@link AsistenciaEvento}. */
public interface AsistenciaEventoRepository extends JpaRepository<AsistenciaEvento, Long> {

    Optional<AsistenciaEvento> findByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

    Optional<AsistenciaEvento> findByIdAndEventoId(Long id, Long eventoId);

    List<AsistenciaEvento> findByEventoId(Long eventoId);

    long countByEventoIdAndEstado(Long eventoId, EstadoAsistencia estado);

    boolean existsByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

    /** Ids de usuarios con app que ya han respondido a ese evento (para la audiencia del push). */
    @Query("""
            select a.usuario.id from AsistenciaEvento a
            where a.evento.id = :eventoId and a.usuario.id is not null
            """)
    Set<Long> idsUsuariosConRespuesta(@Param("eventoId") Long eventoId);
}
