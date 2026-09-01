package com.baniterio.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // El grafo vive en el Application (ámbito de proceso): sobrevive a las
        // recreaciones de la Activity, así que la sesión en memoria no se pierde
        // al rotar y no se filtra un HttpClient por rotación.
        val deps = (application as BaniterioApp).deps

        // Ejecuta [bloque] con el token FCM actual, o no hace nada si el push no
        // está configurado (sin google-services.json, FirebaseApp.getInstance()
        // lanzaría IllegalStateException). Así "Entrar"/"Cerrar sesión" no crashea
        // en el estado del repo tras el merge, antes de crear el proyecto Firebase.
        fun conTokenFcm(bloque: (String) -> Unit) {
            if (FirebaseApp.getApps(this).isEmpty()) return
            runCatching { FirebaseMessaging.getInstance().token.addOnSuccessListener(bloque) }
        }

        // Al iniciar sesión: registra el token FCM de este dispositivo. Aquí la
        // sesión ya tiene JWT y no se limpia, así que `registrar` usa el de
        // SesionHolder sin más.
        fun sincronizarToken() {
            conTokenFcm { token ->
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
            conTokenFcm { token ->
                lifecycleScope.launch { deps.dispositivoRepo.eliminar(token, jwt) }
            }
        }

        // El permiso de notificaciones solo existe en Android 13+ (TIRAMISU);
        // por debajo se concede al instalar. Se pre-comprueba para no re-lanzar
        // el diálogo en cada recreación de la Activity (p. ej. al rotar).
        //
        // Se usa ActivityCompat.requestPermissions (con requestCode de 16 bits) y
        // NO registerForActivityResult: esta Activity es FragmentActivity (por la
        // biblioteca de biometría, que arrastra un androidx.fragment antiguo) y su
        // validador de requestCode rechaza el código de 32 bits que genera el API
        // moderno -> "Can only use lower 16 bits for requestCode". El resultado no
        // se usa: si el usuario dice que no, simplemente no llegan notificaciones.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), RC_PERMISO_NOTIF,
            )
        }

        setContent {
            App(
                deps = deps,
                alIniciarSesion = { sincronizarToken() },
                alCerrarSesion = { borrarToken() },
            )
        }
    }

    private companion object {
        // requestCode de 16 bits (el validador de FragmentActivity lo exige).
        const val RC_PERMISO_NOTIF = 1001
    }
}
