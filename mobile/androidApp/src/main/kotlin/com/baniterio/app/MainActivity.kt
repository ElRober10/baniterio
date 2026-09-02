package com.baniterio.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.baniterio.app.data.FotoElegida
import com.baniterio.app.data.PuenteNativo
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

        // Puente para el editor de perfil: elegir foto y elegir contacto. Se usa
        // startActivityForResult clásico (requestCode de 16 bits) por el mismo
        // motivo que el permiso de notificaciones (ver más abajo).
        PuenteNativo.lanzarFoto = {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, RC_FOTO)
        }
        PuenteNativo.lanzarContacto = {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, RC_CONTACTO)
        }

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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RC_FOTO -> {
                val cb = PuenteNativo.pendienteFoto
                PuenteNativo.pendienteFoto = null
                val uri = data?.data
                val foto = if (resultCode == RESULT_OK && uri != null) {
                    runCatching {
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        val mime = contentResolver.getType(uri) ?: "image/jpeg"
                        bytes?.let { FotoElegida(it, "foto", mime) }
                    }.getOrNull()
                } else {
                    null
                }
                cb?.invoke(foto)
            }

            RC_CONTACTO -> {
                val cb = PuenteNativo.pendienteContacto
                PuenteNativo.pendienteContacto = null
                val uri = data?.data
                val numero = if (resultCode == RESULT_OK && uri != null) {
                    runCatching {
                        contentResolver.query(
                            uri,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            null, null, null,
                        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    }.getOrNull()
                } else {
                    null
                }
                cb?.invoke(numero)
            }
        }
    }

    override fun onDestroy() {
        // Se sueltan las lambdas para no retener la Activity si el proceso la recrea.
        PuenteNativo.lanzarFoto = null
        PuenteNativo.lanzarContacto = null
        super.onDestroy()
    }

    private companion object {
        // requestCodes de 16 bits (el validador de FragmentActivity lo exige).
        const val RC_PERMISO_NOTIF = 1001
        const val RC_FOTO = 1002
        const val RC_CONTACTO = 1003
    }
}
