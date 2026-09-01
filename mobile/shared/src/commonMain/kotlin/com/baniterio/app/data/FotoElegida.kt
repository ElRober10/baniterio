package com.baniterio.app.data

/**
 * Una foto elegida en el dispositivo, lista para subir a `POST /perfil/foto`.
 * El selector nativo que la produce se define en [SelectorFoto] (solo Android).
 */
data class FotoElegida(val bytes: ByteArray, val nombre: String, val tipoMime: String) {
    // ByteArray no compara por contenido; se generan equals/hashCode para que
    // `remember` y los tests se comporten como se espera.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FotoElegida) return false
        return bytes.contentEquals(other.bytes) && nombre == other.nombre && tipoMime == other.tipoMime
    }

    override fun hashCode(): Int =
        bytes.contentHashCode() * 31 * 31 + nombre.hashCode() * 31 + tipoMime.hashCode()
}
