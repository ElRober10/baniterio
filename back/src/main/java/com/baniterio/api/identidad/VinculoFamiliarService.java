package com.baniterio.api.identidad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Quién puede responder una convocatoria en nombre de quién: uno mismo, su
 * pareja con vínculo {@link EstadoVinculo#ACEPTADO} y los hijos con cuenta
 * propia ({@link Hijo#getUsuario()}) de los que es padre/madre — directamente
 * (es el {@code creador}) o como el otro miembro de la pareja que los dio de
 * alta. Un hijo sin cuenta propia no participa: no tiene convocatorias propias
 * que contestar (se le añade a mano al evento, como a cualquier invitado).
 */
@Service
public class VinculoFamiliarService {

    /** Alguien por quien se puede responder: su id y su nombre, para etiquetar "respondiendo por X". */
    public record Persona(Long id, String nombre) {
    }

    private final UsuarioRepository usuarios;
    private final VinculoParejaRepository vinculos;
    private final HijoRepository hijos;

    public VinculoFamiliarService(UsuarioRepository usuarios, VinculoParejaRepository vinculos,
                                   HijoRepository hijos) {
        this.usuarios = usuarios;
        this.vinculos = vinculos;
        this.hijos = hijos;
    }

    /** Uno mismo, más quienes pueda responder en su nombre. Siempre incluye al menos a uno mismo. */
    public List<Persona> personasQuePuedoResponder(Long actuanteId) {
        Usuario yo = usuarios.findById(actuanteId).orElseThrow();
        Map<Long, Persona> porId = new LinkedHashMap<>();
        porId.put(yo.getId(), new Persona(yo.getId(), yo.getNombre()));

        Optional<VinculoPareja> vinculo = vinculos
                .findBySolicitanteIdAndEstado(actuanteId, EstadoVinculo.ACEPTADO)
                .or(() -> vinculos.findByParejaUsuarioIdAndEstado(actuanteId, EstadoVinculo.ACEPTADO));
        vinculo.map(VinculoPareja::getParejaUsuario)
                .ifPresent(u -> porId.put(u.getId(), new Persona(u.getId(), u.getNombre())));

        hijos.findByCreadorId(actuanteId).forEach(h -> agregarSiTieneCuenta(porId, h));
        vinculo.map(VinculoPareja::getId)
                .map(hijos::findByVinculoParejaId)
                .ifPresent(lista -> lista.forEach(h -> agregarSiTieneCuenta(porId, h)));

        return new ArrayList<>(porId.values());
    }

    private static void agregarSiTieneCuenta(Map<Long, Persona> porId, Hijo h) {
        Usuario u = h.getUsuario();
        if (u != null) {
            porId.put(u.getId(), new Persona(u.getId(), u.getNombre()));
        }
    }

    /**
     * A quién puede incluir {@code actuanteId} en un pago además de a sí mismo: su
     * pareja con vínculo {@link EstadoVinculo#ACEPTADO} y los hijos <b>mayores de
     * edad</b> con cuenta propia ({@link Hijo#getUsuario()}), suyos o de la
     * pareja. No incluye a uno mismo (a diferencia de
     * {@link #personasQuePuedoResponder}).
     */
    public List<Persona> personasParaPago(Long actuanteId) {
        Map<Long, Persona> porId = new LinkedHashMap<>();

        Optional<VinculoPareja> vinculo = vinculos
                .findBySolicitanteIdAndEstado(actuanteId, EstadoVinculo.ACEPTADO)
                .or(() -> vinculos.findByParejaUsuarioIdAndEstado(actuanteId, EstadoVinculo.ACEPTADO));
        parejaDe(vinculo, actuanteId)
                .ifPresent(u -> porId.put(u.getId(), new Persona(u.getId(), u.getNombre())));

        hijos.findByCreadorId(actuanteId).forEach(h -> agregarSiMayorConCuenta(porId, h));
        vinculo.map(VinculoPareja::getId)
                .map(hijos::findByVinculoParejaId)
                .ifPresent(lista -> lista.forEach(h -> agregarSiMayorConCuenta(porId, h)));

        return new ArrayList<>(porId.values());
    }

    /**
     * El otro miembro de la pareja visto desde {@code actuanteId}: normalmente
     * {@code parejaUsuario}, pero si {@code actuanteId} es quien fue invitado, el
     * "otro" es el {@code solicitante}.
     */
    private static Optional<Usuario> parejaDe(Optional<VinculoPareja> vinculo, Long actuanteId) {
        return vinculo.map(v -> v.getSolicitante().getId().equals(actuanteId)
                ? v.getParejaUsuario()
                : v.getSolicitante());
    }

    private static void agregarSiMayorConCuenta(Map<Long, Persona> porId, Hijo h) {
        Usuario u = h.getUsuario();
        if (u != null && h.isMayorDeEdad()) {
            porId.put(u.getId(), new Persona(u.getId(), u.getNombre()));
        }
    }
}
