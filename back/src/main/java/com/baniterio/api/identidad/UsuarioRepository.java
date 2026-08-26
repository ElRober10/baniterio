package com.baniterio.api.identidad;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByTelefono(String telefono);

    Optional<Usuario> findByEmail(String email);

    boolean existsByTelefono(String telefono);

    boolean existsByEmail(String email);
}
