package com.baniterio.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.baniterio.app.data.AlmacenCredenciales
import com.baniterio.app.data.Dependencias
import com.baniterio.app.data.crearDependencias

/**
 * Ámbito de proceso para el grafo de dependencias.
 *
 * Antes se construía con `remember {}` dentro de `setContent`, así que se
 * destruía y recreaba en cada recreación de la Activity (p. ej. al rotar, ya
 * que el manifest no declara `configChanges`). Eso ponía a null el token y el
 * `usuarioActual` en memoria mientras `screenKey` (rememberSaveable) restauraba
 * "Panel", y además filtraba un HttpClient vivo por rotación.
 */
class BaniterioApp : Application() {
    val deps: Dependencias by lazy {
        crearDependencias(AlmacenCredenciales(this))
    }

    override fun onCreate() {
        super.onCreate()
        // El canal "avisos" agrupa las notificaciones push de la peña. Existe
        // desde API 26 (Build.VERSION_CODES.O); en 24/25 no hay canales y las
        // notificaciones se pintan igual sin él.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                "avisos",
                "Avisos de la peña",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Solicitudes y cosas por atender" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }
}
