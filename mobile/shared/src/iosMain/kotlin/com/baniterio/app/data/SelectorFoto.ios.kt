package com.baniterio.app.data

import androidx.compose.runtime.Composable

// iOS: sin PHPicker todavía (necesita Mac para compilar/probar). El editor solo
// ofrece avatar. Ver memoria baniterio_push_ios_pendiente.
private object SelectorFotoIos : SelectorFoto {
    override val disponible = false
    override fun elegir(onFoto: (FotoElegida?) -> Unit) = onFoto(null)
}

@Composable
actual fun rememberSelectorFoto(): SelectorFoto = SelectorFotoIos
