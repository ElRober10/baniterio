package com.baniterio.api.admin;

import com.baniterio.api.identidad.AreaProtegida;

/**
 * Contrato de "cuántas cosas hay sin atender" de un área del panel. Cada área
 * que genere trabajo de aprobar/verificar (solicitudes de ingreso hoy;
 * confirmación de pagos, etc. en el futuro) aporta una implementación
 * {@code @Component}; {@link AdminService#pendientesPorArea} las agrega y las
 * filtra a las áreas del usuario que pregunta.
 *
 * <p>Alcance de una sola peña: {@link #contar()} devuelve el total de la peña.
 */
public interface ContadorPendientes {

    /** Área del panel a la que pertenece este contador. */
    AreaProtegida area();

    /** Nº de cosas sin atender en esa área, en toda la peña. Nunca negativo. */
    long contar();
}
