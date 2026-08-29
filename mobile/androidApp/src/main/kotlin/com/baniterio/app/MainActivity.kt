package com.baniterio.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import com.baniterio.app.data.AlmacenCredenciales
import com.baniterio.app.data.crearDependencias

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val deps = remember {
                crearDependencias(AlmacenCredenciales(applicationContext))
            }
            App(deps)
        }
    }
}
