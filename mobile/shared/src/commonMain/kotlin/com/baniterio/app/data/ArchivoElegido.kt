package com.baniterio.app.data

/**
 * Un fichero que el usuario ha elegido del dispositivo para subir (de momento,
 * el recibo de un gasto/ingreso de una cuenta: PDF o imagen). Análogo a
 * [FotoElegida] pero sin recompresión: el PDF tiene que subir tal cual.
 */
data class ArchivoElegido(val bytes: ByteArray, val nombre: String, val tipoMime: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArchivoElegido) return false
        return nombre == other.nombre && tipoMime == other.tipoMime && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + nombre.hashCode()
        result = 31 * result + tipoMime.hashCode()
        return result
    }
}
