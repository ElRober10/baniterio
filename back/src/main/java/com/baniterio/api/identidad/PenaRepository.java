package com.baniterio.api.identidad;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link Pena}. {@code findBySlug} la busca por su
 *  identificador de texto (p. ej. "baniterio"). */
public interface PenaRepository extends JpaRepository<Pena, Long> {

    Optional<Pena> findBySlug(String slug);
}
