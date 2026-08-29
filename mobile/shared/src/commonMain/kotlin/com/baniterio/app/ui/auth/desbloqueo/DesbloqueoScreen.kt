package com.baniterio.app.ui.auth.desbloqueo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.biometric.rememberBiometricAuthenticator
import com.baniterio.app.data.AlmacenCredenciales
import com.baniterio.app.data.AuthRepository
import com.baniterio.app.data.ResultadoAuth
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import kotlinx.coroutines.launch

private sealed interface EstadoDesbloqueo {
    data object Inicial : EstadoDesbloqueo
    data object Autenticando : EstadoDesbloqueo
    data class Error(val mensaje: String) : EstadoDesbloqueo
}

@Composable
fun DesbloqueoScreen(
    repo: AuthRepository,
    almacen: AlmacenCredenciales,
    onDesbloqueado: () -> Unit,
    onUsarOtraCuenta: () -> Unit,
) {
    val biometria = rememberBiometricAuthenticator()
    val scope = rememberCoroutineScope()

    // Arranca en Autenticando (no Inicial) para que el botón esté deshabilitado ya en el
    // primer frame: si no, había una ventana entre la primera composición y el
    // LaunchedEffect en la que un toque rápido lanzaba una segunda biometría/login.
    var estado by remember { mutableStateOf<EstadoDesbloqueo>(EstadoDesbloqueo.Autenticando) }

    val onDesbloqueadoActual by rememberUpdatedState(onDesbloqueado)
    val onUsarOtraCuentaActual by rememberUpdatedState(onUsarOtraCuenta)

    val autenticando = estado is EstadoDesbloqueo.Autenticando

    // Cuerpo compartido por el botón y el LaunchedEffect de auto-lanzamiento.
    // Un fallo deja el estado en Error y termina: NO reintenta en bucle; el usuario
    // vuelve a pulsar el botón para reintentar.
    val intentarDesbloqueo: suspend () -> Unit = intentar@{
        estado = EstadoDesbloqueo.Autenticando
        if (!biometria.autenticar()) {
            estado = EstadoDesbloqueo.Error("No se pudo verificar tu identidad. Inténtalo de nuevo.")
            return@intentar
        }
        val c = almacen.leer()
        if (c == null) {
            onUsarOtraCuentaActual()
            return@intentar
        }
        when (val r = repo.login(c.telefono, c.password)) {
            is ResultadoAuth.Exito -> onDesbloqueadoActual()
            is ResultadoAuth.Error -> estado = EstadoDesbloqueo.Error(r.mensaje)
        }
    }

    // Auto-lanza la biometría en cuanto aparece la pantalla. Se ejecuta una sola vez;
    // si falla no se repite (queda en Error hasta que el usuario pulse el botón).
    LaunchedEffect(Unit) {
        intentarDesbloqueo()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BaniterioWordmark()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Desbloquea para entrar",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (!autenticando) {
                    scope.launch { intentarDesbloqueo() }
                }
            },
            enabled = !autenticando,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BaniterioColors.brand,
                contentColor = BaniterioColors.gold,
            ),
        ) {
            Text(
                if (autenticando) "Verificando…" else "Entrar con huella / Face ID",
                fontWeight = FontWeight.Bold,
            )
        }

        val estadoActual = estado
        if (estadoActual is EstadoDesbloqueo.Error) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = estadoActual.mensaje,
                color = BaniterioColors.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Usar otra cuenta",
            color = BaniterioColors.brandBright,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    almacen.borrar()
                    onUsarOtraCuentaActual()
                },
        )
    }
}
