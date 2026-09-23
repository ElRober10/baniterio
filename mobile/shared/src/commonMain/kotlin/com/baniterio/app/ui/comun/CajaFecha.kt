package com.baniterio.app.ui.comun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baniterio.app.theme.BaniterioColors

/**
 * Selector de fecha con calendario. Igual que [CajaSelect] pero al pulsar abre un
 * [DatePickerDialog]. [valor] y [onCambio] hablan ISO `yyyy-MM-dd` (cadena vacía =
 * sin fecha); con [opcional] aparece un botón "Quitar" para vaciarla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CajaFecha(
    etiqueta: String,
    valor: String,
    onCambio: (String) -> Unit,
    opcional: Boolean = false,
) {
    var abierto by remember { mutableStateOf(false) }
    Column {
        Text(etiqueta, color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { abierto = true },
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (valor.isBlank()) "Elegir fecha" else isoADiaMesAnio(valor),
                        color = if (valor.isBlank()) BaniterioColors.muted
                        else MaterialTheme.colorScheme.onBackground,
                    )
                    Text("📅", color = BaniterioColors.muted)
                }
            }
            if (opcional && valor.isNotBlank()) {
                TextButton(onClick = { onCambio("") }) { Text("Quitar") }
            }
        }
    }
    if (abierto) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = isoAMillis(valor))
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onCambio(millisAIso(it)) }
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        ) { DatePicker(state = estado) }
    }
}

private const val MILIS_DIA = 86_400_000L

private fun isoADiaMesAnio(iso: String): String {
    val p = iso.split("-")
    return if (p.size == 3) "${p[2]}-${p[1]}-${p[0]}" else iso
}

/** ISO -> medianoche UTC en millis (lo que espera el DatePicker); null si no encaja. */
internal fun isoAMillis(iso: String): Long? {
    val p = iso.split("-").mapNotNull { it.toIntOrNull() }
    if (p.size != 3) return null
    // Días desde 1970-01-01 (algoritmo de Hinnant, calendario gregoriano).
    val (a, m, d) = p
    val y = if (m <= 2) a - 1 else a
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return (era * 146097L + doe - 719468L) * MILIS_DIA
}

/** Millis UTC del DatePicker -> ISO `yyyy-MM-dd`. */
internal fun millisAIso(millis: Long): String {
    val z = millis.floorDiv(MILIS_DIA) + 719468L
    val era = z.floorDiv(146097L)
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}
