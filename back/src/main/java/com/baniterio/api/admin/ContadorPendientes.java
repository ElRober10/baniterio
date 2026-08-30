package com.baniterio.api.admin;

import com.baniterio.api.identidad.AreaProtegida;

/**
 * Contrato de "cuántas cosas hay sin atender" de un área del panel. Cada área
 * que genere trabajo de aprobar/verificar (solicitudes de ingreso hoy;
 * confirmación de pagos, etc. en el futuro) aporta una implementación
 * {@code @Component}; {@link AdminService#pendientesPorArea} las agrega y las
 * filtra a las áreas del usuario que pregunta.
 *
 * <p>Alcance de una sola peña: quien llama resuelve el {@code penaId} una vez y
 * lo pasa a {@link #contar(Long)}, que devuelve el total de esa peña. Así ningún
 * contador tiene que repetir la resolución de la peña por su cuenta.
 */
public interface ContadorPendientes {

    /** Área del panel a la que pertenece este contador. */
    AreaProtegida area();

    /**
     * Nº de cosas sin atender en esa área para la peña dada. Nunca negativo.
     *
     * @param penaId id de la peña; lo resuelve quien agrega los contadores.
     */
    long contar(Long penaId);
}
