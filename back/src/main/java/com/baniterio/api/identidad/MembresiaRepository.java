package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link Membresia} (relación usuario–peña con rol). */
public interface MembresiaRepository extends JpaRepository<Membresia, Long> {

    List<Membresia> findByUsuarioId(Long usuarioId);

    List<Membresia> findByPenaId(Long penaId);

    List<Membresia> findByPenaIdAndRol(Long penaId, RolMembresia rol);

    Optional<Membresia> findByUsuarioIdAndPenaId(Long usuarioId, Long penaId);
}
