package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a BBDD para {@link TelefonoAutorizado}. {@code findByTelefonoAndUsadoFalse}
 * es la consulta de la puerta de registro: "¿está este número autorizado y sin
 * usar todavía?".
 */
public interface TelefonoAutorizadoRepository extends JpaRepository<TelefonoAutorizado, Long> {

    Optional<TelefonoAutorizado> findByTelefonoAndUsadoFalse(String telefono);

    /** Cualquier fila de ese teléfono (la columna es UNIQUE), usada o no. */
    Optional<TelefonoAutorizado> findByTelefono(String telefono);

    List<TelefonoAutorizado> findByPenaId(Long penaId);

    boolean existsByTelefonoAndUsadoFalse(String telefono);
}
