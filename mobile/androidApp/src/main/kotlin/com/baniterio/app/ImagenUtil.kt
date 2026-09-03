package com.baniterio.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Decodifica la imagen de [uri], la reduce a como mucho [maxLado] px en su lado
 * mayor, corrige la orientación EXIF y la recomprime a JPEG con [calidad]. Así la
 * foto que se sube pesa cientos de KB en vez de varios MB: el backend la vuelve a
 * procesar a 512×512, pero el POST multipart tiene un límite de 10 MB y las fotos
 * de cámara lo rozan. Devuelve `null` si no se puede leer.
 */
fun reducirImagen(
    context: Context,
    uri: Uri,
    maxLado: Int = 1280,
    calidad: Int = 85,
): ByteArray? = runCatching {
    val resolver = context.contentResolver

    // 1) Solo los límites, para calcular el factor de submuestreo al decodificar.
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
    if (limites.outWidth <= 0 || limites.outHeight <= 0) return@runCatching null

    var sample = 1
    while (max(limites.outWidth, limites.outHeight) / sample > maxLado * 2) sample *= 2

    // 2) Decodifica ya reducida por submuestreo.
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return@runCatching null

    // 3) Orientación EXIF (la cámara suele guardarla en vez de rotar los píxeles).
    val giro = resolver.openInputStream(uri)?.use { entrada ->
        when (
            ExifInterface(entrada).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f

    // 4) Escala fina al lado máximo + rota.
    val escala = maxLado.toFloat() / max(bitmap.width, bitmap.height)
    val matriz = Matrix().apply {
        if (escala < 1f) postScale(escala, escala)
        if (giro != 0f) postRotate(giro)
    }
    val ajustada =
        if (matriz.isIdentity) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)

    ByteArrayOutputStream().use { salida ->
        ajustada.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
        salida.toByteArray()
    }
}.getOrNull()
