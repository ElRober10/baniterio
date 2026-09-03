package com.baniterio.app.data

/** Resultado de una operación de asistencia (pieza 3a). Nunca lanza por HTTP esperado. */
sealed class ResultadoAsistencia<out T> {
    data class Exito<T>(val dato: T) : ResultadoAsistencia<T>()
    data class Error(val codigo: CodigoErrorAsistencia, val mensaje: String) : ResultadoAsistencia<Nothing>()
}

enum class CodigoErrorAsistencia {
    EVENTO_YA_PASADO,
    NOTIFICACION_REENVIO_PRONTO,
    SIN_PERMISO_EVENTO,
    ASISTENCIA_NO_MANUAL,
    ASISTENCIA_NO_ENCONTRADA,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorAsistencia = when (codigo) {
            "EVENTO_YA_PASADO" -> EVENTO_YA_PASADO
            "NOTIFICACION_REENVIO_PRONTO" -> NOTIFICACION_REENVIO_PRONTO
            "SIN_PERMISO_EVENTO" -> SIN_PERMISO_EVENTO
            "ASISTENCIA_NO_MANUAL" -> ASISTENCIA_NO_MANUAL
            "ASISTENCIA_NO_ENCONTRADA" -> ASISTENCIA_NO_ENCONTRADA
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            EVENTO_YA_PASADO -> "El evento ya ha pasado."
            NOTIFICACION_REENVIO_PRONTO -> "Aún no se puede reenviar: espera 48 h desde el último envío."
            SIN_PERMISO_EVENTO -> "No tienes permiso para esto."
            ASISTENCIA_NO_MANUAL -> "Esa respuesta no se puede quitar."
            ASISTENCIA_NO_ENCONTRADA -> "Esa persona ya no está en la lista."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
