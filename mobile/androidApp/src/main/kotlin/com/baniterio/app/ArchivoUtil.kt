package com.baniterio.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.baniterio.app.data.ArchivoElegido

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

    val tipo = resolver.getType(uri) ?: "application/octet-stream"
    ArchivoElegido(bytes, nombre, tipo)
}.getOrNull()
