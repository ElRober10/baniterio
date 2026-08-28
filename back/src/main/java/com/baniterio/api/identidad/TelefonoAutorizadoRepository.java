package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TelefonoAutorizadoRepository extends JpaRepository<TelefonoAutorizado, Long> {

    Optional<TelefonoAutorizado> findByTelefonoAndUsadoFalse(String telefono);

    List<TelefonoAutorizado> findByPenaId(Long penaId);

    boolean existsByTelefonoAndUsadoFalse(String telefono);
}
