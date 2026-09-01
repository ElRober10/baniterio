package com.baniterio.api.identidad;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Acceso a BBDD para {@link Dispositivo} (token push ↔ usuario). */
public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    Optional<Dispositivo> findByToken(String token);

    boolean existsByToken(String token);

    List<Dispositivo> findByUsuarioIdIn(Collection<Long> usuarioIds);

    // Los borrados derivados necesitan su propia transacción cuando el que llama
    // no la trae (p. ej. el IT). Si ya hay una abierta, participan en ella.
    @Transactional
    void deleteByToken(String token);

    @Transactional
    void deleteByTokenIn(Collection<String> tokens);
}
