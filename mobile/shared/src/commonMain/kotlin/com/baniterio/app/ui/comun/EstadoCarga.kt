package com.baniterio.app.ui.comun

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.baniterio.app.theme.BaniterioColors

/** Estado de una pantalla que carga un único dato desde el backend. */
sealed interface EstadoCarga<out T> {
    data object Cargando : EstadoCarga<Nothing>
    data class Error(val mensaje: String) : EstadoCarga<Nothing>
    data class Cargado<T>(val dato: T) : EstadoCarga<T>
}

/**
 * "Cargando…" / mensaje de error con botón Reintentar / el contenido ya
 * cargado. El bloque que se repetía igual en cada pantalla que carga un dato.
 */
@Composable
fun <T> PantallaConEstado(
    estado: EstadoCarga<T>,
    onReintentar: () -> Unit,
    contenido: @Composable (T) -> Unit,
) {
    when (estado) {
        is EstadoCarga.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
        is EstadoCarga.Error -> {
            Text(estado.mensaje, color = BaniterioColors.muted)
            Button(onClick = onReintentar) { Text("Reintentar") }
        }
        is EstadoCarga.Cargado -> contenido(estado.dato)
    }
}
