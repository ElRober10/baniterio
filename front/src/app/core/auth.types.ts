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

export interface UsuarioDto {
  id: string;
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
  | 'VALIDACION';
