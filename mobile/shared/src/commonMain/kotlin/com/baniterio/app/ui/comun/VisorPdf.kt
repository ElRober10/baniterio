package com.baniterio.app.ui.comun

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renderiza un PDF (todas sus páginas, una debajo de otra) a partir de sus bytes, sin salir
 * de la app. Implementación nativa por plataforma: en Android con `PdfRenderer` (rasteriza
 * cada página como bitmap); en iOS, de momento, cae a abrir la URL en el navegador del
 * sistema (ver el comentario del `actual` en iosMain).
 */
@Composable
expect fun VisorPdf(bytes: ByteArray, url: String, modifier: Modifier = Modifier.fillMaxSize())
