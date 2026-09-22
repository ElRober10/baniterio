package com.baniterio.app.data

/**
 * Descarga archivos servidos por `/api/v1/media/...` (recibos, fotos) como bytes en memoria.
 * El visor propio de la app (recibos en PDF/foto, sin salir a un navegador) los necesita así:
 * no basta con darle la URL, `PdfRenderer`/`PDFKit` y "guardar en Descargas" quieren los bytes.
 */
interface MediaRepository {
    suspend fun descargar(url: String): ResultadoMedia<ByteArray>
}
