/**
 * Tipos del contrato con los endpoints /api/v1/admin/* (panel de administración).
 * Solo "formas" de datos: TypeScript los usa para autocompletar y avisar de
 * errores, no generan código. `*Resumen` = lo que recibimos en los listados.
 */

/** Fila del listado de solicitudes de ingreso pendientes de resolver. */
export interface SolicitudResumen {
  id: number;
  nombre: string;
  apellidos: string;
  telefono: string;
  email: string;
  motivo: string;
  relacion: string;
  conocidos: string;
  traeContrasena: boolean;
  estado: string;
  createdAt: string;
}

/** Fila del listado de miembros de la peña (para gestionar rol / activo / áreas). */
export interface MiembroResumen {
  id: number;
  nombre: string;
  apellidos: string;
  mote: string | null;
  telefono: string;
  rol: Rol;
  activo: boolean;
  esSuperadmin: boolean;
  areas: string[];
}

/**
 * Resultado de aprobar una solicitud: el backend crea la cuenta directamente si
 * la persona ya traía contraseña, o solo autoriza el teléfono si no.
 */
export type AprobarResultado = 'CUENTA_CREADA' | 'TELEFONO_AUTORIZADO';

/** Rol de un miembro dentro de la peña. */
export type Rol = 'ADMIN' | 'MIEMBRO';

/**
 * Áreas del panel de administración que el front conoce. Es el tipo que exige
 * `areaGuard(...)`: así `areaGuard('ADMIN_SOLICITUDS')` (con una errata) es un
 * error de compilación, no un guard que nunca deja pasar.
 */
export type Area = 'ADMIN_SOLICITUDES' | 'ADMIN_PERMISOS' | 'INVENTARIO';

/**
 * Áreas conocidas del panel de administración y su etiqueta legible. La clave es
 * lo que viaja en `usuario.areas` y en el guard; el valor es lo que se pinta.
 */
export const AREAS: Record<Area, string> = {
  ADMIN_SOLICITUDES: 'Solicitudes',
  ADMIN_PERMISOS: 'Permisos',
  INVENTARIO: 'Inventario',
};

/**
 * Cuántas cosas sin atender tiene el usuario en cada área del panel. La clave es
 * la misma que viaja en `usuario.areas`; falta = 0. Lo devuelve
 * `GET /api/v1/admin/pendientes`.
 */
export type PendientesPorArea = Partial<Record<Area, number>>;

/**
 * Fila del bloque "Solicitudes de evento" (solo la ven admin/superadmin). Espejo
 * de `SolicitudEventoResumen` del backend (`com.baniterio.api.evento.dto`).
 */
export interface SolicitudEventoResumen {
  id: number;
  tipo: 'CREAR' | 'BORRAR';
  estado: string;
  solicitante: { id: number; nombre: string; apellidos: string };
  evento: { id: number; nombre: string; fecha: string } | null;
  mensaje: string | null;
  createdAt: string;
}
