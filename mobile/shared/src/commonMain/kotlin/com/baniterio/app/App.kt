package com.baniterio.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.baniterio.app.data.Dependencias
import com.baniterio.app.nav.Screen
import com.baniterio.app.nav.SolicitudPrecarga
import com.baniterio.app.theme.BaniterioTheme
import com.baniterio.app.ui.admin.AdminIndexScreen
import com.baniterio.app.ui.admin.AdminPermisosScreen
import com.baniterio.app.ui.admin.AdminSolicitudesScreen
import com.baniterio.app.ui.historia.HistoriaScreen
import com.baniterio.app.ui.auth.desbloqueo.DesbloqueoScreen
import com.baniterio.app.ui.auth.login.LoginScreen
import com.baniterio.app.ui.panel.PanelScreen
import com.baniterio.app.ui.auth.registro.RegistroScreen
import com.baniterio.app.ui.auth.solicitaracceso.SolicitarAccesoScreen

private const val CLAVE_DESBLOQUEO = "Desbloqueo"
private const val CLAVE_LOGIN = "Login"
private const val CLAVE_REGISTRO = "Registro"
private const val CLAVE_SOLICITAR = "SolicitarAcceso"
private const val CLAVE_PANEL = "Panel"
private const val CLAVE_HISTORIA = "Historia"
private const val CLAVE_ADMIN_INDEX = "AdminIndex"
private const val CLAVE_ADMIN_SOLICITUDES = "AdminSolicitudes"
private const val CLAVE_ADMIN_PERMISOS = "AdminPermisos"

private fun Screen.aClave(): String = when (this) {
    Screen.Desbloqueo -> CLAVE_DESBLOQUEO
    Screen.Login -> CLAVE_LOGIN
    Screen.Registro -> CLAVE_REGISTRO
    Screen.SolicitarAcceso -> CLAVE_SOLICITAR
    Screen.Panel -> CLAVE_PANEL
    Screen.Historia -> CLAVE_HISTORIA
    Screen.AdminIndex -> CLAVE_ADMIN_INDEX
    Screen.AdminSolicitudes -> CLAVE_ADMIN_SOLICITUDES
    Screen.AdminPermisos -> CLAVE_ADMIN_PERMISOS
}

private fun claveAScreen(clave: String): Screen = when (clave) {
    CLAVE_DESBLOQUEO -> Screen.Desbloqueo
    CLAVE_REGISTRO -> Screen.Registro
    CLAVE_SOLICITAR -> Screen.SolicitarAcceso
    CLAVE_PANEL -> Screen.Panel
    CLAVE_HISTORIA -> Screen.Historia
    CLAVE_ADMIN_INDEX -> Screen.AdminIndex
    CLAVE_ADMIN_SOLICITUDES -> Screen.AdminSolicitudes
    CLAVE_ADMIN_PERMISOS -> Screen.AdminPermisos
    else -> Screen.Login
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(deps: Dependencias) {
    // Se guarda solo la clave (String), no el Screen en sí, porque un sealed class llano
    // no es directamente Saveable en todas las plataformas. Así sobrevive a un cambio de
    // configuración (p. ej. rotar el dispositivo) sin volver a Login.
    var screenKey by rememberSaveable {
        mutableStateOf(if (deps.almacen.hayCredenciales) CLAVE_DESBLOQUEO else CLAVE_LOGIN)
    }
    val screen: Screen = claveAScreen(screenKey)

    // Guardia de arranque en frío: tras morir el proceso, `screenKey` puede restaurar
    // "Panel"/"Historia" pero la sesión (token + usuarioActual) vive solo en memoria y
    // se ha perdido. Se redirige a Desbloqueo/Login. En una recomposición normal dentro
    // de la sesión `usuarioActual` no es null, así que esto no hace nada.
    LaunchedEffect(Unit) {
        if (deps.repo.usuarioActual == null &&
            (screen == Screen.Panel || screen == Screen.Historia ||
                screen == Screen.AdminIndex || screen == Screen.AdminSolicitudes ||
                screen == Screen.AdminPermisos)
        ) {
            screenKey = if (deps.almacen.hayCredenciales) CLAVE_DESBLOQUEO else CLAVE_LOGIN
        }
    }

    // Datos que Registro precarga en SolicitarAcceso cuando el teléfono no está autorizado.
    // rememberSaveable con Saver propio para que sobreviva a una rotación.
    var datosSolicitud by rememberSaveable(
        stateSaver = Saver<SolicitudPrecarga?, List<String>>(
            save = { valor ->
                valor?.let { p -> listOf(p.nombre, p.apellidos, p.telefono, p.email, p.password) }
            },
            restore = { l -> SolicitudPrecarga(l[0], l[1], l[2], l[3], l[4]) },
        ),
    ) { mutableStateOf<SolicitudPrecarga?>(null) }

    fun ir(destino: Screen) {
        screenKey = destino.aClave()
    }

    BaniterioTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (screen) {
                is Screen.Desbloqueo -> DesbloqueoScreen(
                    repo = deps.repo,
                    almacen = deps.almacen,
                    onDesbloqueado = { ir(Screen.Panel) },
                    onUsarOtraCuenta = { ir(Screen.Login) },
                )
                is Screen.Login -> LoginScreen(
                    repo = deps.repo,
                    almacen = deps.almacen,
                    onLoginSuccess = { ir(Screen.Panel) },
                    onIrARegistro = { ir(Screen.Registro) },
                )
                is Screen.Registro -> {
                    BackHandler { ir(Screen.Login) }
                    RegistroScreen(
                        repo = deps.repo,
                        onRegistroCompletado = { ir(Screen.Login) },
                        onVolverALogin = { ir(Screen.Login) },
                        onSolicitarAcceso = { datos ->
                            datosSolicitud = datos
                            ir(Screen.SolicitarAcceso)
                        },
                    )
                }
                is Screen.SolicitarAcceso -> {
                    BackHandler { ir(Screen.Registro) }
                    SolicitarAccesoScreen(
                        repo = deps.repo,
                        precarga = datosSolicitud,
                        onEnviada = { ir(Screen.Login) },
                        onVolver = { ir(Screen.Registro) },
                    )
                }
                is Screen.Panel -> PanelScreen(
                    onAbrirSeccion = { destino -> ir(destino) },
                    onCerrarSesion = {
                        deps.repo.logout()
                        deps.almacen.borrar()
                        ir(Screen.Login)
                    },
                    tieneAdmin = deps.repo.usuarioActual?.areas?.isNotEmpty() == true,
                )
                is Screen.Historia -> {
                    BackHandler { ir(Screen.Panel) }
                    HistoriaScreen(
                        onVolver = { ir(Screen.Panel) },
                    )
                }
                is Screen.AdminIndex -> {
                    BackHandler { ir(Screen.Panel) }
                    AdminIndexScreen(
                        areas = deps.repo.usuarioActual?.areas ?: emptyList(),
                        onAbrir = { ir(it) },
                        onVolver = { ir(Screen.Panel) },
                    )
                }
                is Screen.AdminSolicitudes -> {
                    BackHandler { ir(Screen.AdminIndex) }
                    AdminSolicitudesScreen(adminRepo = deps.adminRepo, onVolver = { ir(Screen.AdminIndex) })
                }
                is Screen.AdminPermisos -> {
                    BackHandler { ir(Screen.AdminIndex) }
                    AdminPermisosScreen(
                        adminRepo = deps.adminRepo,
                        miId = deps.repo.usuarioActual?.id,
                        onVolver = { ir(Screen.AdminIndex) },
                    )
                }
            }
        }
    }
}
