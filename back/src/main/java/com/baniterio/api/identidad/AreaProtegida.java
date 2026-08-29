package com.baniterio.api.identidad;

/**
 * Áreas del panel cuyo acceso se puede conceder a un usuario concreto que no
 * es admin. Un {@code es_superadmin} o un miembro con rol {@code ADMIN} tiene
 * todas de forma implícita; al resto se le conceden una a una
 * ({@link PermisoArea}).
 *
 * <p>Añadir un valor aquí = nueva área protegida: aparece sola en la pantalla
 * de permisos y en {@code GET /api/v1/auth/yo}. De momento solo las dos
 * secciones del panel de administración.
 */
public enum AreaProtegida {
    ADMIN_SOLICITUDES,
    ADMIN_PERMISOS
}
