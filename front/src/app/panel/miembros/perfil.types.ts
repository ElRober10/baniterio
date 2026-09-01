/**
 * Tipos del editor de perfil y de la sección Miembros. Reflejan uno a uno los
 * DTOs del backend (`com.baniterio.api.perfil.dto`, `TarjetaMiembroResponse`).
 * `*Response`/`*Dto`-like = lo que recibimos. `*Request` = lo que enviamos.
 */

export interface ParejaEnPerfil {
  vinculoId: number;
  nombre: string;
  telefono: string;
  estado: 'SIN_CUENTA' | 'PENDIENTE' | 'ACEPTADO' | 'RECHAZADO';
}

export interface HijoEnPerfil {
  id: number;
  nombre: string;
  mayorDeEdad: boolean;
  telefono: string | null;
  visible: boolean;
  registrado: boolean;
}

export interface VinculoPendiente {
  vinculoId: number;
  solicitanteNombre: string;
}

/** `GET /api/v1/perfil`. Siempre 200: si aún no hay perfil, llega con `completado: false`. */
export interface PerfilResponse {
  usuarioId: number;
  nombre: string;
  apellidos: string;
  mote: string | null;
  sobreMi: string | null;
  imagenTipo: 'FOTO' | 'AVATAR' | null;
  imagenRef: string | null;
  imagenUrl: string | null;
  completado: boolean;
  pareja: ParejaEnPerfil | null;
  hijos: HijoEnPerfil[];
  vinculoPendiente: VinculoPendiente | null;
}

export interface HijoRequest {
  id: number | null;
  nombre: string;
  mayorDeEdad: boolean;
  telefono: string | null;
  visible: boolean;
}

/** Cuerpo de `PUT /api/v1/perfil`: todo el estado del editor de una vez. */
export interface GuardarPerfilRequest {
  nombre: string;
  apellidos: string;
  mote: string | null;
  sobreMi: string | null;
  imagenTipo: 'FOTO' | 'AVATAR';
  imagenRef: string;
  tienePareja: boolean;
  parejaNombre: string | null;
  parejaTelefono: string | null;
  hijos: HijoRequest[];
}

export interface AvatarResumen {
  id: string;
  genero: 'CHICO' | 'CHICA';
}

/** Una tarjeta de `GET /api/v1/miembros`. Nunca trae teléfono ni email. */
export interface TarjetaMiembroResponse {
  id: number;
  nombre: string;
  apellidos: string;
  mote: string | null;
  sobreMi: string | null;
  imagenUrl: string | null;
  parejaNombre: string | null;
  hijos: string[];
}

/** Códigos de error propios del editor de perfil (ver ApiExceptionHandler.java). */
export type CodigoErrorPerfil =
  | 'AVATAR_INEXISTENTE'
  | 'IMAGEN_REF_INVALIDA'
  | 'IMAGEN_NO_SOPORTADA'
  | 'IMAGEN_DEMASIADO_GRANDE'
  | 'VINCULO_NO_ENCONTRADO'
  | 'TELEFONO_YA_EMPAREJADO'
  | 'YA_TIENE_PAREJA'
  | 'TELEFONO_PAREJA_INVALIDO'
  | 'NOMBRE_PAREJA_REQUERIDO'
  | 'TELEFONO_HIJO_INVALIDO'
  | 'VALIDACION';
