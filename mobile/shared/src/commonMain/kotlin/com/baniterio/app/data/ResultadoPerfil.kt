package com.baniterio.app.data

sealed class ResultadoPerfil<out T> {
    data class Exito<T>(val dato: T) : ResultadoPerfil<T>()
    data class Error(val codigo: CodigoErrorPerfil, val mensaje: String) : ResultadoPerfil<Nothing>()
}

enum class CodigoErrorPerfil {
    AVATAR_INEXISTENTE,
    IMAGEN_REF_INVALIDA,
    IMAGEN_NO_SOPORTADA,
    IMAGEN_DEMASIADO_GRANDE,
    VINCULO_NO_ENCONTRADO,
    TELEFONO_YA_EMPAREJADO,
    YA_TIENE_PAREJA,
    TELEFONO_PAREJA_INVALIDO,
    NOMBRE_PAREJA_REQUERIDO,
    TELEFONO_HIJO_INVALIDO,
    VALIDACION,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorPerfil = when (codigo) {
            "AVATAR_INEXISTENTE" -> AVATAR_INEXISTENTE
            "IMAGEN_REF_INVALIDA" -> IMAGEN_REF_INVALIDA
            "IMAGEN_NO_SOPORTADA" -> IMAGEN_NO_SOPORTADA
            "IMAGEN_DEMASIADO_GRANDE" -> IMAGEN_DEMASIADO_GRANDE
            "VINCULO_NO_ENCONTRADO" -> VINCULO_NO_ENCONTRADO
            "TELEFONO_YA_EMPAREJADO" -> TELEFONO_YA_EMPAREJADO
            "YA_TIENE_PAREJA" -> YA_TIENE_PAREJA
            "TELEFONO_PAREJA_INVALIDO" -> TELEFONO_PAREJA_INVALIDO
            "NOMBRE_PAREJA_REQUERIDO" -> NOMBRE_PAREJA_REQUERIDO
            "TELEFONO_HIJO_INVALIDO" -> TELEFONO_HIJO_INVALIDO
            "VALIDACION" -> VALIDACION
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            AVATAR_INEXISTENTE, IMAGEN_REF_INVALIDA -> "Elige una foto o un avatar válido."
            IMAGEN_NO_SOPORTADA -> "Ese archivo no es una foto válida (usa JPEG o PNG)."
            IMAGEN_DEMASIADO_GRANDE -> "La foto pesa demasiado."
            VINCULO_NO_ENCONTRADO -> "Ese vínculo de pareja ya no está."
            TELEFONO_YA_EMPAREJADO -> "Ese teléfono ya tiene pareja en la peña."
            YA_TIENE_PAREJA -> "Ya tienes un vínculo de pareja activo."
            TELEFONO_PAREJA_INVALIDO -> "Revisa el teléfono de tu pareja: no parece un móvil español."
            NOMBRE_PAREJA_REQUERIDO -> "Escribe el nombre de tu pareja."
            TELEFONO_HIJO_INVALIDO -> "Revisa el teléfono de tu hijo: no parece un móvil español."
            VALIDACION -> "Revisa los datos del formulario."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
