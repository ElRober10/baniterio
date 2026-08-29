package com.baniterio.app.data

sealed class ResultadoAuth<out T> {
    data class Exito<T>(val dato: T) : ResultadoAuth<T>()
    data class Error(val codigo: CodigoErrorAuth, val mensaje: String) : ResultadoAuth<Nothing>()
}

enum class CodigoErrorAuth {
    TELEFONO_NO_AUTORIZADO,
    YA_REGISTRADO,
    CREDENCIALES_INVALIDAS,
    VALIDACION,
    SOLICITUD_YA_PENDIENTE,
    TELEFONO_YA_AUTORIZADO,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorAuth = when (codigo) {
            "TELEFONO_NO_AUTORIZADO" -> TELEFONO_NO_AUTORIZADO
            "YA_REGISTRADO" -> YA_REGISTRADO
            "CREDENCIALES_INVALIDAS" -> CREDENCIALES_INVALIDAS
            "VALIDACION" -> VALIDACION
            "SOLICITUD_YA_PENDIENTE" -> SOLICITUD_YA_PENDIENTE
            "TELEFONO_YA_AUTORIZADO" -> TELEFONO_YA_AUTORIZADO
            else -> DESCONOCIDO
        }
    }

    /** Mensaje por defecto para mostrar al usuario. Las pantallas pueden
     *  sobreescribir alguno (p. ej. registro trata TELEFONO_NO_AUTORIZADO aparte). */
    val mensaje: String
        get() = when (this) {
            TELEFONO_NO_AUTORIZADO -> "Tu teléfono no está autorizado en la peña."
            YA_REGISTRADO -> "Ya existe una cuenta con ese teléfono o ese email."
            CREDENCIALES_INVALIDAS -> "Teléfono o contraseña incorrectos."
            VALIDACION -> "Revisa los datos del formulario."
            SOLICITUD_YA_PENDIENTE -> "Ya hay una solicitud pendiente para ese teléfono."
            TELEFONO_YA_AUTORIZADO -> "Ese teléfono ya está autorizado. Regístrate directamente."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo más tarde."
        }
}
