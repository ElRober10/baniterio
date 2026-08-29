package com.baniterio.app

import androidx.compose.ui.window.ComposeUIViewController
import com.baniterio.app.data.AlmacenCredenciales
import com.baniterio.app.data.Dependencias
import com.baniterio.app.data.crearDependencias

// Ámbito de proceso, igual que el Application de Android: el grafo (y con él la
// sesión en memoria y el HttpClient) no se recrea si el UIViewController se
// vuelve a crear.
private val deps: Dependencias by lazy {
    crearDependencias(AlmacenCredenciales())
}

fun MainViewController() = ComposeUIViewController {
    App(deps)
}
