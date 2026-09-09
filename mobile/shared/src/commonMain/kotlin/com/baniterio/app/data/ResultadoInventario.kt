package com.baniterio.app.data

/** Resultado de una operación de la sección Inventario. Nunca lanza por HTTP esperado. */
sealed class ResultadoInventario<out T> {
    data class Exito<T>(val dato: T) : ResultadoInventario<T>()
    data class Error(val codigo: CodigoErrorInventario, val mensaje: String) : ResultadoInventario<Nothing>()
}

enum class CodigoErrorInventario {
    ARTICULO_NO_ENCONTRADO,
    SIN_PERMISO,
    TAMANO_NO_VALIDO,
    NADA_QUE_ENVIAR,
    ARTICULO_EVENTO_NO_ENCONTRADO,
    EVENTO_NO_ENCONTRADO,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorInventario = when (codigo) {
            "ARTICULO_INVENTARIO_NO_ENCONTRADO" -> ARTICULO_NO_ENCONTRADO
            "SIN_PERMISO_INVENTARIO" -> SIN_PERMISO
            "TAMANO_INVENTARIO_NO_VALIDO" -> TAMANO_NO_VALIDO
            "NADA_QUE_ENVIAR" -> NADA_QUE_ENVIAR
            "ARTICULO_EVENTO_NO_ENCONTRADO" -> ARTICULO_EVENTO_NO_ENCONTRADO
            "EVENTO_NO_ENCONTRADO" -> EVENTO_NO_ENCONTRADO
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            ARTICULO_NO_ENCONTRADO -> "Ese artículo ya no existe."
            SIN_PERMISO -> "No tienes permiso para esto."
            TAMANO_NO_VALIDO -> "Ese tamaño no vale para esta categoría."
            NADA_QUE_ENVIAR -> "No queda nada de ese artículo para enviar."
            ARTICULO_EVENTO_NO_ENCONTRADO -> "Ese artículo ya no está en la fiesta."
            EVENTO_NO_ENCONTRADO -> "Ese evento ya no existe."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
