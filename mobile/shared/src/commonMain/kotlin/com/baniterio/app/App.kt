package com.baniterio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.baniterio.app.nav.Screen
import com.baniterio.app.theme.BaniterioTheme
import com.baniterio.app.ui.historia.HistoriaScreen
import com.baniterio.app.ui.login.LoginScreen
import com.baniterio.app.ui.panel.PanelScreen
import com.baniterio.app.ui.registro.RegistroScreen

@Composable
fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Login) }

    BaniterioTheme {
        when (screen) {
            is Screen.Login -> LoginScreen(
                onLoginSuccess = { screen = Screen.Panel },
                onIrARegistro = { screen = Screen.Registro },
            )
            is Screen.Registro -> RegistroScreen(
                onRegistroCompletado = { screen = Screen.Panel },
                onVolverALogin = { screen = Screen.Login },
            )
            is Screen.Panel -> PanelScreen(
                onAbrirHistoria = { screen = Screen.Historia },
            )
            is Screen.Historia -> HistoriaScreen(
                onVolver = { screen = Screen.Panel },
            )
        }
    }
}
