package com.baniterio.app.data

sealed class ResultadoAdmin<out T> {
    data class Exito<T>(val dato: T) : ResultadoAdmin<T>()
    data class Error(val codigo: CodigoErrorAdmin, val mensaje: String) : ResultadoAdmin<Nothing>()
}

enum class CodigoErrorAdmin {
    SIN_PERMISO,
    SOLICITUD_YA_RESUELTA,
    ULTIMO_ADMIN,
    AUTO_MODIFICACION,
    SOLO_EL_SUPERADMIN,
    MIEMBRO_NO_ENCONTRADO,
    CONFLICTO,
    VALIDACION,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorAdmin = when (codigo) {
            "SIN_PERMISO" -> SIN_PERMISO
            "SOLICITUD_YA_RESUELTA" -> SOLICITUD_YA_RESUELTA
            "ULTIMO_ADMIN" -> ULTIMO_ADMIN
            "NO_TE_PUEDES_DEGRADAR", "NO_TE_PUEDES_DESACTIVAR" -> AUTO_MODIFICACION
            "SOLO_EL_SUPERADMIN" -> SOLO_EL_SUPERADMIN
            "MIEMBRO_NO_ENCONTRADO" -> MIEMBRO_NO_ENCONTRADO
            "YA_REGISTRADO" -> CONFLICTO
            "VALIDACION" -> VALIDACION
            else -> DESCONOCIDO
        }
    }

    /** Mensaje por defecto para mostrar al usuario. */
    val mensaje: String
        get() = when (this) {
            SIN_PERMISO -> "No tienes permiso para esto."
            SOLICITUD_YA_RESUELTA -> "Esa solicitud ya la había resuelto alguien."
            ULTIMO_ADMIN -> "No puedes dejar la peña sin ningún administrador."
            AUTO_MODIFICACION -> "No puedes cambiarte a ti mismo el rol ni desactivarte."
            SOLO_EL_SUPERADMIN -> "A esa persona solo puede tocarla ella misma."
            MIEMBRO_NO_ENCONTRADO -> "Ese miembro ya no está."
            CONFLICTO -> "Ya existe una cuenta con ese teléfono o email."
            VALIDACION -> "Datos no válidos."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
