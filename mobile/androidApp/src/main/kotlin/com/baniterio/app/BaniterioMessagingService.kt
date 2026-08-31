package com.baniterio.app

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recibe los eventos de Firebase Cloud Messaging. `onNewToken` mantiene el
 * backend al día si el token cambia estando con sesión. `onMessageReceived`
 * solo se invoca con la app en primer plano (en segundo plano el sistema pinta
 * la notificación solo, por el bloque `notification` del mensaje).
 */
class BaniterioMessagingService : FirebaseMessagingService() {

    private val deps get() = (application as BaniterioApp).deps
    // SupervisorJob: un fallo de una llamada no cancela las demás. El scope NO se
    // ata al ciclo de vida del servicio a propósito: FirebaseMessagingService
    // llama a stopSelf() nada más volver onNewToken, así que cancelarlo en
    // onDestroy mataría el registro del token a mitad de POST. El trabajo es
    // acotado (dos suspend cortas) y no retiene nada al terminar.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (deps.repo.usuarioActual != null) {
            scope.launch { deps.dispositivoRepo.registrar(token, "ANDROID") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // TODO(menor): icono monocromo dedicado; ic_launcher es un apaño para que
        // las notificaciones de segundo plano no salgan con un cuadrado en blanco.
        val n = message.notification ?: return
        val aviso = NotificationCompat.Builder(this, "avisos")
            .setSmallIcon(com.baniterio.app.R.mipmap.ic_launcher)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(message.messageId?.hashCode() ?: 0, aviso)
    }
}
