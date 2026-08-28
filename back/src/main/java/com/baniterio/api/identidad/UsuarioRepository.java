package com.baniterio.api.identidad;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByTelefono(String telefono);

    Optional<Usuario> findByEmail(String email);

    boolean existsByTelefono(String telefono);

    boolean existsByEmail(String email);
}
