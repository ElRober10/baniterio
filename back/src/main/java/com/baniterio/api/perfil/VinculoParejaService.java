package com.baniterio.api.perfil;

import com.baniterio.api.identidad.EstadoVinculo;
import com.baniterio.api.identidad.VinculoParejaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Máquina de estados del vínculo de pareja: declararlo desde el editor, promover
 * {@code SIN_CUENTA → PENDIENTE} cuando la pareja se registra, y las altas/bajas
 * asociadas en {@code telefono_autorizado}.
 *
 * <p><b>Estado en la Task 6:</b> {@code reconciliarAlEntrar} es un no-op y
 * {@code aplicarDesdePerfil} solo cubre el camino "el usuario dice que NO tiene
 * pareja" (borrar el vínculo vivo que él hubiera declarado). El camino
 * {@code tienePareja=true} (crear/actualizar el vínculo, resolver si el teléfono
 * es de un miembro, publicar el push, dar de alta el teléfono) lo implementa la
 * Task 7. Ver {@code // TODO(Task 7)}.
 */
@Service
public class VinculoParejaService {

    private final VinculoParejaRepository vinculos;

    public VinculoParejaService(VinculoParejaRepository vinculos) {
        this.vinculos = vinculos;
    }

    /**
     * Al entrar (primer {@code GET /perfil}), si hay un vínculo {@code SIN_CUENTA}
     * dirigido al teléfono de este usuario, promoverlo a {@code PENDIENTE} y
     * avisar por push a este usuario.
     *
     * <p>TODO(Task 7): implementar. Hoy no hace nada, así que un usuario recién
     * registrado cuyo teléfono ya figuraba en un vínculo {@code SIN_CUENTA} no ve
     * el aviso hasta que la Task 7 llegue.
     */
    public void reconciliarAlEntrar(Long usuarioId) {
        // TODO(Task 7)
    }

    /**
     * Aplica la respuesta del editor sobre la pareja.
     *
     * <p>{@code tienePareja=false}: si el usuario tenía un vínculo vivo declarado
     * por él en estado {@code SIN_CUENTA} o {@code PENDIENTE}, se borra (aún no
     * había compromiso mutuo). Un {@code ACEPTADO} NO se toca por aquí: romper un
     * vínculo aceptado es {@code DELETE /perfil/pareja} (Task 7), no un efecto
     * lateral de guardar el perfil.
     *
     * <p>{@code tienePareja=true}: TODO(Task 7) — crear o actualizar el vínculo
     * con {@code parejaNombre}/{@code parejaTelefono}, decidir {@code SIN_CUENTA}
     * vs {@code PENDIENTE}, publicar el push y dar de alta el teléfono en
     * {@code telefono_autorizado}. Hoy no hace nada.
     */
    @Transactional
    public void aplicarDesdePerfil(Long usuarioId, boolean tienePareja, String parejaNombre, String parejaTelefono) {
        if (tienePareja) {
            // TODO(Task 7)
            return;
        }
        vinculos.findBySolicitanteIdAndEstadoNot(usuarioId, EstadoVinculo.RECHAZADO)
                .filter(v -> v.getEstado() == EstadoVinculo.SIN_CUENTA
                        || v.getEstado() == EstadoVinculo.PENDIENTE)
                .ifPresent(vinculos::delete);
    }
}
