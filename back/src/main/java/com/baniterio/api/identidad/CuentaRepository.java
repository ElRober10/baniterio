package com.baniterio.api.identidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link Cuenta}. */
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    /** Cuentas de la peña por nombre ascendente (el orden de la sección Cuentas). */
    List<Cuenta> findByPenaIdOrderByNombreAsc(Long penaId);
}
