package com.baniterio.app.data

import androidx.compose.runtime.Composable

/**
 * Selector de foto del dispositivo. Solo disponible en Android (el de iOS
 * —PHPicker— está aparcado con el trabajo de Mac). Cuando [disponible] es
 * `false` el editor no ofrece "Subir una foto", solo avatar.
 */
interface SelectorFoto {
    val disponible: Boolean

    /** Abre el selector; llama a [onFoto] con la elección, o con `null` si se cancela. */
    fun elegir(onFoto: (FotoElegida?) -> Unit)
}

@Composable
expect fun rememberSelectorFoto(): SelectorFoto
