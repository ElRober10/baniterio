/**
 * Tipos compartidos entre AuthService y los componentes de login/registro.
 * Son solo "formas" (contratos) de los datos que van y vienen del backend;
 * TypeScript los usa para autocompletar y avisar de errores, no generan código.
 *
 * `*Body` = lo que enviamos. `*Dto` = lo que recibimos. `CodigoError` = los
 * valores del campo "codigo" que puede traer una respuesta de error del backend
 * (ver ApiExceptionHandler.java).
 */
export interface RegistroBody {
  telefono: string;
  email: string;
  password: string;
  nombre: string;
  apellidos: string;
  mote: string;
}

export interface LoginBody {
  telefono: string;
  password: string;
}

/** Solicitud de acceso de alguien cuyo teléfono no está autorizado. */
export interface SolicitudIngresoBody {
  telefono: string;
  email: string;
  nombre: string;
  apellidos: string;
  motivo: string;
  relacion: string;
  conocidos: string;
}

export interface UsuarioDto {
  id: number;
  nombre: string;
  apellidos: string;
  mote: string | null;
  esSuperadmin: boolean;
}

export interface LoginDto {
  token: string;
  usuario: UsuarioDto;
}

export type CodigoError =
  | 'TELEFONO_NO_AUTORIZADO'
  | 'YA_REGISTRADO'
  | 'CREDENCIALES_INVALIDAS'
  | 'VALIDACION'
  | 'SOLICITUD_YA_PENDIENTE'
  | 'TELEFONO_YA_AUTORIZADO';
