package com.baniterio.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class SelectorFotoAndroid : SelectorFoto {
    override val disponible = true
    override val puedeHacerFoto = true

    override fun elegir(origen: OrigenFoto, onFoto: (FotoElegida?) -> Unit) {
        PuenteNativo.pendienteFoto = onFoto
        when (origen) {
            OrigenFoto.GALERIA -> PuenteNativo.lanzarFotoGaleria?.invoke()
            OrigenFoto.CAMARA -> PuenteNativo.lanzarFotoCamara?.invoke()
        }
    }
}

@Composable
actual fun rememberSelectorFoto(): SelectorFoto = remember { SelectorFotoAndroid() }
