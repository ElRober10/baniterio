package com.baniterio.api.identidad;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio = acceso a BBDD para {@link Usuario}. Al extender
 * {@code JpaRepository<Usuario, Long>} (entidad, tipo de la PK), Spring Data
 * genera la implementación: ya tienes {@code save}, {@code findById},
 * {@code findAll}, {@code delete}...
 *
 * <p>Los métodos de abajo también los implementa Spring solo, deduciéndolos del
 * nombre: {@code findByTelefono} → {@code SELECT ... WHERE telefono = ?}.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByTelefono(String telefono);

    Optional<Usuario> findByEmail(String email);

    boolean existsByTelefono(String telefono);

    boolean existsByEmail(String email);
}
