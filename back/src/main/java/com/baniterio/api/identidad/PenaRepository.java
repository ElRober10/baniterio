package com.baniterio.api.identidad;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PenaRepository extends JpaRepository<Pena, UUID> {

    Optional<Pena> findBySlug(String slug);
}
