package com.baniterio.app

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Recibe los eventos de Firebase Cloud Messaging. `onNewToken` mantiene el
 * backend al día si el token cambia estando con sesión. `onMessageReceived`
 * solo se invoca con la app en primer plano (en segundo plano el sistema pinta
 * la notificación solo, por el bloque `notification` del mensaje).
 */
class BaniterioMessagingService : FirebaseMessagingService() {

    private val deps get() = (application as BaniterioApp).deps
    // SupervisorJob: un fallo de una llamada no cancela las demás. Se cancela en
    // onDestroy para no dejar corrutinas vivas tras destruirse el servicio.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (deps.repo.usuarioActual != null) {
            scope.launch { deps.dispositivoRepo.registrar(token, "ANDROID") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // TODO(menor): usar un icono propio en vez de ic_dialog_info (marcador).
        val n = message.notification ?: return
        val aviso = NotificationCompat.Builder(this, "avisos")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(message.messageId?.hashCode() ?: 0, aviso)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
