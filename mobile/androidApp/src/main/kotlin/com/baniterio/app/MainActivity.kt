package com.baniterio.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    // Debe registrarse como campo (antes de que onCreate termine); el resultado
    // no se usa: si el usuario dice que no, simplemente no llegan notificaciones.
    private val pedirPermiso =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // El grafo vive en el Application (ámbito de proceso): sobrevive a las
        // recreaciones de la Activity, así que la sesión en memoria no se pierde
        // al rotar y no se filtra un HttpClient por rotación.
        val deps = (application as BaniterioApp).deps

        // Al iniciar sesión: registra el token FCM de este dispositivo. Aquí la
        // sesión ya tiene JWT y no se limpia, así que `registrar` usa el de
        // SesionHolder sin más.
        fun sincronizarToken() {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                lifecycleScope.launch { deps.dispositivoRepo.registrar(token, "ANDROID") }
            }
        }
        // Al cerrar sesión: da de baja el token. El callback `alCerrarSesion`
        // corre en el mismo frame que `deps.repo.logout()` (App.kt), que pone a
        // null el JWT en memoria. `addOnSuccessListener` y `launch` se ejecutan
        // DESPUÉS de ese frame, cuando el token ya no está. Por eso se captura el
        // JWT de forma síncrona ahora y se pasa explícitamente a `eliminar`, para
        // que el DELETE salga autenticado con el token válido de antes del logout.
        fun borrarToken() {
            val jwt = deps.repo.tokenSesion
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                lifecycleScope.launch { deps.dispositivoRepo.eliminar(token, jwt) }
            }
        }

        // El permiso de notificaciones solo existe en Android 13+ (TIRAMISU);
        // por debajo se concede al instalar. Se pre-comprueba para no re-lanzar
        // el diálogo en cada recreación de la Activity (p. ej. al rotar).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
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
