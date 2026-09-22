package com.baniterio.app.ui.comun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

// iOS: visor nativo con PDFKit pendiente (necesita Mac para compilar/probar, ver
// memoria baniterio_push_ios_pendiente). De momento se abre en el navegador del
// sistema, como antes de este cambio: sigue funcionando, solo que sin el visor propio.
@Composable
actual fun VisorPdf(bytes: ByteArray, url: String, modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            "El visor del PDF dentro de la app todavía no está en iOS. Se abre en el navegador.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { uriHandler.openUri(url) }) {
            Text("Abrir el PDF")
        }
    }
}
