package com.baniterio.app.data

import androidx.compose.runtime.Composable

// iOS: sin selector de contactos todavía (necesita Mac). El teléfono se teclea.
private object SelectorContactoIos : SelectorContacto {
    override val disponible = false
    override fun elegir(onTelefono: (String?) -> Unit) = onTelefono(null)
}

@Composable
actual fun rememberSelectorContacto(): SelectorContacto = SelectorContactoIos
