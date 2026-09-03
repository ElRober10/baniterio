package com.baniterio.app.data

import androidx.compose.runtime.Composable

/** De dónde sale la foto de perfil que se va a subir. */
enum class OrigenFoto { GALERIA, CAMARA }

/**
 * Selector de foto del dispositivo. Solo disponible en Android (el de iOS
 * —PHPicker / cámara— está aparcado con el trabajo de Mac). Cuando [disponible]
 * es `false` el editor no ofrece "Subir una foto", solo avatar; cuando
 * [puedeHacerFoto] es `false` solo ofrece la galería.
 */
interface SelectorFoto {
    val disponible: Boolean
    val puedeHacerFoto: Boolean

    /**
     * Abre la galería o la cámara según [origen]; llama a [onFoto] con la
     * elección, o con `null` si se cancela.
     */
    fun elegir(origen: OrigenFoto, onFoto: (FotoElegida?) -> Unit)
}

@Composable
expect fun rememberSelectorFoto(): SelectorFoto
