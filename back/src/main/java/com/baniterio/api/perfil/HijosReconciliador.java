package com.baniterio.api.perfil;

import java.util.List;

import com.baniterio.api.perfil.dto.HijoRequest;
import org.springframework.stereotype.Service;

/**
 * Reconcilia la lista de hijos del editor con lo que había en {@code hijo}:
 * altas ({@code id} nulo), ediciones ({@code id} presente), bajas (los que
 * estaban y ya no vienen), y las altas/bajas de sus teléfonos en
 * {@code telefono_autorizado}.
 *
 * <p><b>Estado en la Task 6:</b> stub no-op. La reconciliación real la implementa
 * la Task 8. Ver {@code // TODO(Task 8)}.
 */
@Service
public class HijosReconciliador {

    /**
     * TODO(Task 8): implementar el diff altas/ediciones/bajas y sincronizar
     * {@code telefono_autorizado}. Hoy no hace nada: guardar el perfil no crea ni
     * borra hijos todavía.
     */
    public void aplicar(Long usuarioId, List<HijoRequest> hijos) {
        // TODO(Task 8)
    }
}
