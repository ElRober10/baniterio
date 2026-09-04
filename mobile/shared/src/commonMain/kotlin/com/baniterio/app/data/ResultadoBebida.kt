package com.baniterio.app.data

/** Resultado de una operación del catálogo de bebidas (pieza 3b). Nunca lanza por HTTP esperado. */
sealed class ResultadoBebida<out T> {
    data class Exito<T>(val dato: T) : ResultadoBebida<T>()
    data class Error(val codigo: CodigoErrorBebida, val mensaje: String) : ResultadoBebida<Nothing>()
}

enum class CodigoErrorBebida {
    BEBIDA_NO_ENCONTRADA,
    SIN_PERMISO,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorBebida = when (codigo) {
            "BEBIDA_NO_ENCONTRADA" -> BEBIDA_NO_ENCONTRADA
            "SIN_PERMISO" -> SIN_PERMISO
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            BEBIDA_NO_ENCONTRADA -> "Esa bebida ya no existe."
            SIN_PERMISO -> "No tienes permiso para esto."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
