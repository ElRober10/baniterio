package com.baniterio.app.data

import androidx.compose.runtime.Composable

/**
 * Selector de contacto del dispositivo para rellenar un teléfono de pareja o de
 * un hijo. Solo Android (`ACTION_PICK` sobre la agenda, sin permiso). En iOS
 * [disponible] es `false` y el campo se rellena a mano.
 */
interface SelectorContacto {
    val disponible: Boolean

    /** Abre la agenda; llama a [onTelefono] con el número elegido (sin normalizar), o `null`. */
    fun elegir(onTelefono: (String?) -> Unit)
}

@Composable
expect fun rememberSelectorContacto(): SelectorContacto
