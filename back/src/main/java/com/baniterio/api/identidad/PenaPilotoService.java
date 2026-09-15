package com.baniterio.api.identidad;

import java.util.Optional;

import com.baniterio.api.auth.UsuarioPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve "la peña actual". Punto único que usan todos los servicios de
 * negocio (Evento, Cuenta, Inventario, Miembros, Admin, ...) en vez de pedir
 * la peña piloto por su cuenta: así, si el usuario autenticado pertenece a la
 * peña demo, ve solo sus datos ficticios, y si pertenece a la peña real, ve
 * los suyos — sin que ningún otro servicio tenga que saber que existe más de
 * una peña.
 *
 * <p>Regla de resolución:
 * <ul>
 *   <li>Si hay un usuario autenticado (JWT válido, ver {@code JwtAuthenticationFilter}),
 *       se usa la peña de su membresía activa.
 *   <li>Si no hay usuario autenticado (pantallas públicas: registro, login,
 *       solicitud de ingreso), se sigue devolviendo la peña piloto por slug,
 *       como antes de que existiera la peña demo.
 * </ul>
 */
@Service
public class PenaPilotoService {

    private static final String SLUG_PENA = "baniterio";
    private static final String SLUG_DEMO = "demo";

    private final PenaRepository penas;
    private final MembresiaRepository membresias;

    public PenaPilotoService(PenaRepository penas, MembresiaRepository membresias) {
        this.penas = penas;
        this.membresias = membresias;
    }

    /**
     * La peña del usuario autenticado (por su membresía activa), o la peña
     * piloto si no hay usuario autenticado. Si falta la siembra de la peña
     * piloto (V6), es un fallo de arranque legítimo (500).
     */
    @Transactional(readOnly = true)
    public Pena entidad() {
        return penaDelUsuarioAutenticado().orElseGet(this::penaPiloto);
    }

    @Transactional(readOnly = true)
    public Long id() {
        return entidad().getId();
    }

    /**
     * Filtra explícitamente por si el usuario es la cuenta demo o no, en vez
     * de fiarse de "la primera membresía activa que haya": si el usuario
     * demo llegara a tener alguna membresía real además de la de la peña
     * demo (p. ej. datos sueltos de pruebas locales), no debe poder verla.
     */
    private Optional<Pena> penaDelUsuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal)) {
            return Optional.empty();
        }
        return membresias.findByUsuarioId(principal.id()).stream()
                .filter(Membresia::isActiva)
                .map(Membresia::getPena)
                .filter(pena -> principal.esDemo() == SLUG_DEMO.equals(pena.getSlug()))
                .findFirst();
    }

    private Pena penaPiloto() {
        return penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"));
    }
}
