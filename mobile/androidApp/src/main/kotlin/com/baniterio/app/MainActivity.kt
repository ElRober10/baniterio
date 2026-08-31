package com.baniterio.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    // Debe registrarse como campo (antes de onCreate termine); el resultado no
    // se usa: si el usuario dice que no, simplemente no llegan notificaciones.
    private val pedirPermiso =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // El grafo vive en el Application (ámbito de proceso): sobrevive a las
        // recreaciones de la Activity, así que la sesión en memoria no se pierde
        // al rotar y no se filtra un HttpClient por rotación.
        val deps = (application as BaniterioApp).deps
        val scope = CoroutineScope(Dispatchers.Main)

        // Al iniciar sesión: registra el token FCM de este dispositivo.
        fun sincronizarToken() {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                scope.launch { deps.dispositivoRepo.registrar(token, "ANDROID") }
            }
        }
        // Al cerrar sesión: da de baja el token. El callback alCerrarSesion se
        // dispara ANTES de limpiar el JWT (Task 9), así que el DELETE sale con
        // el Bearer válido en el caso normal.
        fun borrarToken() {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                scope.launch { deps.dispositivoRepo.eliminar(token) }
            }
        }

        // El permiso de notificaciones solo existe en Android 13+ (TIRAMISU);
        // por debajo se concede al instalar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            App(
                deps = deps,
                alIniciarSesion = { sincronizarToken() },
                alCerrarSesion = { borrarToken() },
            )
        }
    }
}
