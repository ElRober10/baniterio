package com.baniterio.app.data

/** Resultado de descargar un archivo servido por `/api/v1/media/...` (recibos, fotos). */
sealed class ResultadoMedia<out T> {
    data class Exito<T>(val dato: T) : ResultadoMedia<T>()
    data class Error(val mensaje: String) : ResultadoMedia<Nothing>()
}
