package com.baniterio.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.baniterio.app.data.ArchivoElegido

/**
 * Tipos que el backend acepta como recibo ([AlmacenRecibos.extensionDe] en el
 * back). Si el content resolver no da uno de estos (pasa con algunos gestores
 * de archivos y PDFs descargados, que devuelven `application/octet-stream`),
 * se adivina por la extensión del nombre en vez de dejar que el back lo
 * rechace con un archivo perfectamente válido.
 */
private val TIPOS_RECIBO_VALIDOS = setOf("application/pdf", "image/jpeg", "image/jpg", "image/png")

private fun tipoPorExtension(nombre: String): String? = when {
    nombre.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
    nombre.endsWith(".png", ignoreCase = true) -> "image/png"
    nombre.endsWith(".jpg", ignoreCase = true) || nombre.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
    else -> null
}

/**
 * Lee el contenido de [uri] (el recibo que el usuario ha elegido: PDF o imagen)
 * a un [ArchivoElegido] con sus bytes, nombre y tipo MIME. Sin recompresión: el
 * PDF sube tal cual y las imágenes de recibo suelen ser razonables. Devuelve
 * `null` si no se puede leer.
 */
fun leerArchivo(context: Context, uri: Uri): ArchivoElegido? = runCatching {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null

    val nombre = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?: "recibo"

    val tipoResolver = resolver.getType(uri)
    val tipo = tipoResolver?.takeIf { it in TIPOS_RECIBO_VALIDOS }
        ?: tipoPorExtension(nombre)
        ?: tipoResolver
        ?: "application/octet-stream"
    ArchivoElegido(bytes, nombre, tipo)
}.getOrNull()
