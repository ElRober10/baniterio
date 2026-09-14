package com.baniterio.app.ui.comun

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark

/** Cabecera repetida en (casi) toda pantalla: marca + "Volver" a la derecha. */
@Composable
fun CabeceraPantalla(onVolver: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BaniterioWordmark()
        Text(
            "Volver",
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.brandBright,
            modifier = Modifier.clickable { onVolver() },
        )
    }
}
