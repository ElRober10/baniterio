package com.baniterio.api.identidad;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve la peña piloto (única peña mientras el alcance esté limitado a una
 * sola peña). Punto único donde vive el slug "baniterio"; el resto de
 * servicios piden aquí el id o la entidad en vez de repetir
 * {@code penas.findBySlug(...)}.
 */
@Service
public class PenaPilotoService {

    private static final String SLUG_PENA = "baniterio";

    private final PenaRepository penas;

    public PenaPilotoService(PenaRepository penas) {
        this.penas = penas;
    }

    /** La peña piloto. Si falta la siembra (V6), es un fallo de arranque legítimo (500). */
    @Transactional(readOnly = true)
    public Pena entidad() {
        return penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"));
    }

    @Transactional(readOnly = true)
    public Long id() {
        return entidad().getId();
    }
}
