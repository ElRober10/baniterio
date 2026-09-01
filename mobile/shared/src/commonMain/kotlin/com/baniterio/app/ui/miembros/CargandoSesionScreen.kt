package com.baniterio.app.ui.miembros

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.PerfilRepository
import com.baniterio.app.data.ResultadoPerfil
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark

/**
 * Entre login/desbloqueo y el panel. Pide el perfil: si está completo entra al
 * panel; si no, manda al editor en modo obligatorio. El `GET /perfil` además
 * dispara en el backend la reconciliación `SIN_CUENTA → PENDIENTE` si esta
 * persona acaba de registrarse con un teléfono que otra declaró como pareja.
 * Un fallo de red muestra reintento (no se deja pasar a ciegas).
 */
@Composable
fun CargandoSesionScreen(
    perfilRepo: PerfilRepository,
    onPerfilCompleto: () -> Unit,
    onPerfilIncompleto: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }

    LaunchedEffect(intento) {
        error = null
        when (val r = perfilRepo.miPerfil()) {
            is ResultadoPerfil.Exito -> if (r.dato.completado) onPerfilCompleto() else onPerfilIncompleto()
            is ResultadoPerfil.Error -> error = r.mensaje
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BaniterioWordmark()
        Spacer(Modifier.height(16.dp))
        val e = error
        if (e == null) {
            Text("Cargando tu perfil…", color = BaniterioColors.muted)
        } else {
            Text(e, color = BaniterioColors.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Reintentar",
                color = BaniterioColors.brandBright,
                modifier = Modifier.clickable { intento++ },
            )
        }
    }
}
