package com.baniterio.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // El grafo vive en el Application (ámbito de proceso): sobrevive a las
        // recreaciones de la Activity, así que la sesión en memoria no se pierde
        // al rotar y no se filtra un HttpClient por rotación.
        val deps = (application as BaniterioApp).deps

        setContent {
            App(deps)
        }
    }
}
