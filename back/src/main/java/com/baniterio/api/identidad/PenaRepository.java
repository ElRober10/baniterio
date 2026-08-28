package com.baniterio.api.identidad;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PenaRepository extends JpaRepository<Pena, Long> {

    Optional<Pena> findBySlug(String slug);
}
