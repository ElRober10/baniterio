package com.baniterio.app.ui.comun

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * `PdfRenderer` solo sabe leer de disco (un `ParcelFileDescriptor`), no de bytes en memoria:
 * se vuelcan a un fichero temporal en la caché de la app, se rasteriza cada página a un
 * bitmap y se borra el temporal. A 2x la resolución nativa del PDF (72dpi) para que no se
 * vea borroso.
 */
@Composable
actual fun VisorPdf(bytes: ByteArray, url: String, modifier: Modifier) {
    val context = LocalContext.current
    var paginas by remember(bytes) { mutableStateOf<List<Bitmap>?>(null) }

    LaunchedEffect(bytes) {
        val archivoTemp = File.createTempFile("recibo", ".pdf", context.cacheDir)
        try {
            archivoTemp.writeBytes(bytes)
            ParcelFileDescriptor.open(archivoTemp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val bitmaps = mutableListOf<Bitmap>()
                    val escala = 2
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { pagina ->
                            val bitmap = Bitmap.createBitmap(
                                pagina.width * escala,
                                pagina.height * escala,
                                Bitmap.Config.ARGB_8888,
                            )
                            pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bitmap)
                        }
                    }
                    paginas = bitmaps
                }
            }
        } finally {
            archivoTemp.delete()
        }
    }

    val lista = paginas
    if (lista == null) {
        androidx.compose.foundation.layout.Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lista) { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
