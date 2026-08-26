package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MembresiaRepository extends JpaRepository<Membresia, UUID> {

    List<Membresia> findByUsuarioId(UUID usuarioId);

    List<Membresia> findByPenaId(UUID penaId);

    Optional<Membresia> findByUsuarioIdAndPenaId(UUID usuarioId, UUID penaId);
}
