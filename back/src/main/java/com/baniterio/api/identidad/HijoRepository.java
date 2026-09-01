package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link Hijo}. */
public interface HijoRepository extends JpaRepository<Hijo, Long> {

    List<Hijo> findByCreadorId(Long creadorId);

    List<Hijo> findByVinculoParejaId(Long vinculoParejaId);

    /** {@code hijo.telefono} no es único: devuelve lista, no {@code Optional}. */
    List<Hijo> findAllByTelefono(String telefono);

    Optional<Hijo> findByUsuarioId(Long usuarioId);
}
