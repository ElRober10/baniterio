package com.baniterio.app.data

sealed class ResultadoCuenta<out T> {
    data class Exito<T>(val dato: T) : ResultadoCuenta<T>()
    data class Error(val codigo: CodigoErrorCuenta, val mensaje: String) : ResultadoCuenta<Nothing>()
}

enum class CodigoErrorCuenta {
    CUENTA_NO_ENCONTRADA,
    SIN_PERMISO,
    VALIDACION,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorCuenta = when (codigo) {
            "CUENTA_NO_ENCONTRADA" -> CUENTA_NO_ENCONTRADA
            "SIN_PERMISO" -> SIN_PERMISO
            "VALIDACION" -> VALIDACION
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            CUENTA_NO_ENCONTRADA -> "Esa cuenta ya no existe."
            SIN_PERMISO -> "No tienes permiso para esto."
            VALIDACION -> "Revisa los datos del formulario."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
