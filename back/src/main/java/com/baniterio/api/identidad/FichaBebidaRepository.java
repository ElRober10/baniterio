package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a BBDD para {@link FichaBebida}. */
public interface FichaBebidaRepository extends JpaRepository<FichaBebida, Long> {

    Optional<FichaBebida> findByAsistenciaId(Long asistenciaId);

    @Query("select f from FichaBebida f where f.asistencia.evento.id = :eventoId")
    List<FichaBebida> findByEventoId(@Param("eventoId") Long eventoId);
}
