package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MembresiaRepository extends JpaRepository<Membresia, Long> {

    List<Membresia> findByUsuarioId(Long usuarioId);

    List<Membresia> findByPenaId(Long penaId);

    Optional<Membresia> findByUsuarioIdAndPenaId(Long usuarioId, Long penaId);
}
