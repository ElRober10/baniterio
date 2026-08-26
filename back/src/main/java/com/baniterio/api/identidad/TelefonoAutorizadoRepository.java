package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TelefonoAutorizadoRepository extends JpaRepository<TelefonoAutorizado, UUID> {

    Optional<TelefonoAutorizado> findByTelefonoAndUsadoFalse(String telefono);

    List<TelefonoAutorizado> findByPenaId(UUID penaId);

    boolean existsByTelefonoAndUsadoFalse(String telefono);
}
