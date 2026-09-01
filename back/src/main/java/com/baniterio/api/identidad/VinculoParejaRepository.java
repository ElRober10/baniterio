package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link VinculoPareja}. */
public interface VinculoParejaRepository extends JpaRepository<VinculoPareja, Long> {

    /** El vínculo vivo (estado != el dado) donde soy el solicitante. */
    Optional<VinculoPareja> findBySolicitanteIdAndEstadoNot(Long solicitanteId, EstadoVinculo estado);

    /** El vínculo vivo (estado != el dado) donde soy la pareja registrada. */
    Optional<VinculoPareja> findByParejaUsuarioIdAndEstadoNot(Long parejaUsuarioId, EstadoVinculo estado);

    /**
     * Vínculos con ese teléfono de pareja y estado. Devuelve lista: {@code
     * pareja_telefono} NO es único (dos miembros pueden declarar, cada uno, el
     * mismo teléfono aún sin cuenta), así que un {@code Optional} reventaría con
     * {@code IncorrectResultSizeDataAccessException}.
     */
    List<VinculoPareja> findAllByParejaTelefonoAndEstado(String parejaTelefono, EstadoVinculo estado);

    List<VinculoPareja> findAllByParejaTelefonoAndEstadoNot(String parejaTelefono, EstadoVinculo estado);
}
