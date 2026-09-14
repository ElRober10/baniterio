package com.baniterio.app.data

/** Resultado de una operación de la lista de la compra. Nunca lanza por HTTP esperado. */
sealed class ResultadoListaCompra<out T> {
    data class Exito<T>(val dato: T) : ResultadoListaCompra<T>()
    data class Error(val codigo: CodigoErrorListaCompra, val mensaje: String) : ResultadoListaCompra<Nothing>()
}

enum class CodigoErrorListaCompra {
    EVENTO_NO_ENCONTRADO,
    SIN_PERMISO,
    REGLA_NO_ENCONTRADA,
    REGLA_NO_BORRABLE,
    REGLA_DUPLICADA,
    AJUSTE_NO_APLICA,
    POR_CADA_NO_APLICA,
    FORMULA_NO_CREABLE,
    LINEA_NO_ENCONTRADA,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorListaCompra = when (codigo) {
            "EVENTO_NO_ENCONTRADO" -> EVENTO_NO_ENCONTRADO
            "SIN_PERMISO_INVENTARIO" -> SIN_PERMISO
            "REGLA_COMPRA_NO_ENCONTRADA" -> REGLA_NO_ENCONTRADA
            "REGLA_COMPRA_NO_BORRABLE" -> REGLA_NO_BORRABLE
            "REGLA_COMPRA_DUPLICADA" -> REGLA_DUPLICADA
            "AJUSTE_NO_APLICA" -> AJUSTE_NO_APLICA
            "POR_CADA_NO_APLICA" -> POR_CADA_NO_APLICA
            "FORMULA_NO_CREABLE" -> FORMULA_NO_CREABLE
            "LINEA_COMPRA_NO_ENCONTRADA" -> LINEA_NO_ENCONTRADA
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            EVENTO_NO_ENCONTRADO -> "Ese evento ya no existe."
            SIN_PERMISO -> "No tienes permiso para esto."
            REGLA_NO_ENCONTRADA -> "Esa regla ya no existe."
            REGLA_NO_BORRABLE -> "Esa regla es de la plantilla: desactívala en vez de borrarla."
            REGLA_DUPLICADA -> "Ya hay una regla igual en este evento."
            AJUSTE_NO_APLICA -> "Las reglas por marca no admiten cantidad fija."
            POR_CADA_NO_APLICA -> "«Por cada N» necesita el número N."
            FORMULA_NO_CREABLE -> "Esa fórmula no se puede crear a mano."
            LINEA_NO_ENCONTRADA -> "Esa línea ya no está en la lista."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
