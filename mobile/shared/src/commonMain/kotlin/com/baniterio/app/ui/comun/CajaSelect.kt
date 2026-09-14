package com.baniterio.app.ui.comun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.baniterio.app.theme.BaniterioColors

/**
 * Caja con aspecto de "select": etiqueta encima, botón a lo ancho con el valor y
 * un ▾ a la derecha, y un [DropdownMenu] al pulsarlo. El valor va en color
 * apagado si todavía es el texto de placeholder.
 */
@Composable
fun CajaSelect(
    etiqueta: String,
    texto: String,
    esPlaceholder: Boolean = false,
    menu: @Composable ColumnScope.(cerrar: () -> Unit) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    Column {
        Text(etiqueta, color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        texto,
                        color = if (esPlaceholder) BaniterioColors.muted
                        else MaterialTheme.colorScheme.onBackground,
                    )
                    Text("▾", color = BaniterioColors.muted)
                }
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                menu { abierto = false }
            }
        }
    }
}
