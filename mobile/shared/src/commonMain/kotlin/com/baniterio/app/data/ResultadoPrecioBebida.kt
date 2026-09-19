package com.baniterio.app.data

/** Resultado de una operación de Precio compras. Nunca lanza por HTTP esperado. */
sealed class ResultadoPrecioBebida<out T> {
    data class Exito<T>(val dato: T) : ResultadoPrecioBebida<T>()
    data class Error(val codigo: CodigoErrorPrecioBebida, val mensaje: String) : ResultadoPrecioBebida<Nothing>()
}

enum class CodigoErrorPrecioBebida {
    EVENTO_NO_ENCONTRADO,
    BEBIDA_NO_ENCONTRADA,
    TIENDA_DUPLICADA,
    TIENDA_NO_ENCONTRADA,
    TAMANO_DUPLICADO,
    TAMANO_NO_VALIDO,
    SIN_PERMISO,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorPrecioBebida = when (codigo) {
            "EVENTO_NO_ENCONTRADO" -> EVENTO_NO_ENCONTRADO
            "BEBIDA_NO_ENCONTRADA" -> BEBIDA_NO_ENCONTRADA
            "TIENDA_DUPLICADA" -> TIENDA_DUPLICADA
            "TIENDA_NO_ENCONTRADA" -> TIENDA_NO_ENCONTRADA
            "TAMANO_PRECIO_BEBIDA_DUPLICADO" -> TAMANO_DUPLICADO
            "TAMANO_PRECIO_BEBIDA_NO_VALIDO" -> TAMANO_NO_VALIDO
            "SIN_PERMISO" -> SIN_PERMISO
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            EVENTO_NO_ENCONTRADO -> "Ese evento ya no existe."
            BEBIDA_NO_ENCONTRADA -> "Esa marca ya no está en el catálogo."
            TIENDA_DUPLICADA -> "Ya hay una tienda con ese nombre."
            TIENDA_NO_ENCONTRADA -> "Esa tienda ya no existe."
            TAMANO_DUPLICADO -> "Ya hay un tamaño igual en este evento."
            TAMANO_NO_VALIDO -> "Ese tamaño no está entre las pestañas del evento."
            SIN_PERMISO -> "No tienes permiso para esto."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
