package com.baniterio.app.data

sealed class ResultadoEvento<out T> {
    data class Exito<T>(val dato: T) : ResultadoEvento<T>()
    data class Error(val codigo: CodigoErrorEvento, val mensaje: String) : ResultadoEvento<Nothing>()
}

/** Qué pasó al pedir un borrado: se borró de verdad o quedó una solicitud pendiente. */
enum class BorradoEvento { BORRADO, SOLICITUD_CREADA }

enum class CodigoErrorEvento {
    EVENTO_NO_ENCONTRADO,
    SIN_PERMISO_EVENTO,
    SIN_CREDITO_EVENTO,
    SOLICITUD_EVENTO_YA_PENDIENTE,
    CREDITO_SIN_CONSUMIR,
    SOLICITUD_EVENTO_NO_APLICA,
    SOLICITUD_EVENTO_YA_RESUELTA,
    CUENTA_YA_EXISTE,
    CUENTA_NO_ENCONTRADA,
    SIN_PERMISO,
    VALIDACION,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorEvento = when (codigo) {
            "EVENTO_NO_ENCONTRADO" -> EVENTO_NO_ENCONTRADO
            "SIN_PERMISO_EVENTO" -> SIN_PERMISO_EVENTO
            "SIN_CREDITO_EVENTO" -> SIN_CREDITO_EVENTO
            "SOLICITUD_EVENTO_YA_PENDIENTE" -> SOLICITUD_EVENTO_YA_PENDIENTE
            "CREDITO_SIN_CONSUMIR" -> CREDITO_SIN_CONSUMIR
            "SOLICITUD_EVENTO_NO_APLICA" -> SOLICITUD_EVENTO_NO_APLICA
            "SOLICITUD_EVENTO_YA_RESUELTA" -> SOLICITUD_EVENTO_YA_RESUELTA
            "CUENTA_YA_EXISTE" -> CUENTA_YA_EXISTE
            "CUENTA_NO_ENCONTRADA" -> CUENTA_NO_ENCONTRADA
            "SIN_PERMISO" -> SIN_PERMISO
            "VALIDACION" -> VALIDACION
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            EVENTO_NO_ENCONTRADO -> "Ese evento ya no existe."
            SIN_PERMISO_EVENTO, SIN_PERMISO -> "No tienes permiso para esto."
            SIN_CREDITO_EVENTO -> "No tienes ningún evento autorizado sin crear."
            SOLICITUD_EVENTO_YA_PENDIENTE -> "Ya hay una solicitud pendiente."
            CREDITO_SIN_CONSUMIR -> "Ya tienes un evento autorizado sin crear."
            SOLICITUD_EVENTO_NO_APLICA -> "Como administrador puedes crear eventos directamente."
            SOLICITUD_EVENTO_YA_RESUELTA -> "Esa solicitud ya la resolvió alguien."
            CUENTA_YA_EXISTE -> "Ya existe una cuenta con ese nombre. Elígela de la lista."
            CUENTA_NO_ENCONTRADA -> "Esa cuenta ya no existe."
            VALIDACION -> "Revisa los datos del formulario."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
