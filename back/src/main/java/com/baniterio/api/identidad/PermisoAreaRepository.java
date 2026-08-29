package com.baniterio.api.identidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link PermisoArea} (concesiones de área por usuario). */
public interface PermisoAreaRepository extends JpaRepository<PermisoArea, Long> {

    List<PermisoArea> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndArea(Long usuarioId, AreaProtegida area);

    void deleteByUsuarioId(Long usuarioId);
}
