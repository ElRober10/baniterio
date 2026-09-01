package com.baniterio.app.data

/**
 * Normaliza un móvil español al formato de 9 dígitos que espera el backend
 * (`^[67]\d{8}$`). Quita espacios, guiones y paréntesis, y un prefijo `+34` o
 * `0034`. Devuelve `null` si tras limpiar no es un móvil válido: el campo
 * conserva lo tecleado y la pantalla muestra el error. El backend valida igual
 * (fuente de verdad) y devuelve 400 por campo.
 */
fun normalizarTelefonoEs(entrada: String): String? {
    val limpio = entrada.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }
    val sinPrefijo = limpio.removePrefix("+34").removePrefix("0034")
    return if (Regex("^[67]\\d{8}$").matches(sinPrefijo)) sinPrefijo else null
}
