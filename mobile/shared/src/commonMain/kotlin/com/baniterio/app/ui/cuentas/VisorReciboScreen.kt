package com.baniterio.app.ui.cuentas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.baniterio.app.data.API_BASE_URL
import com.baniterio.app.data.MediaRepository
import com.baniterio.app.data.ResultadoMedia
import com.baniterio.app.data.rememberDescargadorArchivo
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.EstadoCarga
import com.baniterio.app.ui.comun.PantallaConEstado
import com.baniterio.app.ui.comun.VisorPdf

/**
 * Visor propio del recibo de un movimiento (PDF o foto), sin salir a un navegador: se baja
 * una única vez a bytes (los reutilizan tanto el visor como el botón "Descargar") y, según
 * la extensión del archivo, se rasteriza como PDF o se pinta como imagen.
 */
@Composable
fun VisorReciboScreen(mediaRepo: MediaRepository, archivo: String, onVolver: () -> Unit) {
    val url = "$API_BASE_URL/media/recibos/$archivo"
    val esPdf = archivo.endsWith(".pdf", ignoreCase = true)

    var estado by remember(archivo) { mutableStateOf<EstadoCarga<ByteArray>>(EstadoCarga.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var mensajeDescarga by remember { mutableStateOf<String?>(null) }
    val descargador = rememberDescargadorArchivo()

    LaunchedEffect(archivo, intento) {
        estado = EstadoCarga.Cargando
        estado = when (val r = mediaRepo.descargar(url)) {
            is ResultadoMedia.Exito -> EstadoCarga.Cargado(r.dato)
            is ResultadoMedia.Error -> EstadoCarga.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CabeceraPantalla(onVolver)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (esPdf) "Recibo (PDF)" else "Recibo (foto)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val bytesListos = (estado as? EstadoCarga.Cargado)?.dato
            if (descargador.disponible && bytesListos != null) {
                TextButton(onClick = {
                    descargador.guardar(bytesListos, archivo, mimeTypeDeArchivo(archivo)) { ok ->
                        mensajeDescarga = if (ok) "Guardado en Descargas." else "No se pudo guardar el archivo."
                    }
                }) { Text("⬇ Descargar") }
            }
        }

        mensajeDescarga?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            PantallaConEstado(estado, onReintentar = { intento++ }) { bytes ->
                if (esPdf) {
                    VisorPdf(bytes, url, Modifier.fillMaxSize())
                } else {
                    AsyncImage(
                        model = bytes,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun mimeTypeDeArchivo(archivo: String): String = when {
    archivo.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
    archivo.endsWith(".png", ignoreCase = true) -> "image/png"
    else -> "image/jpeg"
}
