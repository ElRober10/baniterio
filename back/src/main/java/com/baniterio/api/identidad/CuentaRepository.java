package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link Cuenta}. */
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    /** Cuentas de la peña. El orden de la sección Cuentas lo pone {@code CuentaService}. */
    List<Cuenta> findByPenaId(Long penaId);

    Optional<Cuenta> findByPenaIdAndNombre(Long penaId, String nombre);

    boolean existsByPenaIdAndNombreIgnoreCase(Long penaId, String nombre);
}
