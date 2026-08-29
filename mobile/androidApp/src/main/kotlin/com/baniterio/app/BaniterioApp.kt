package com.baniterio.app

import android.app.Application
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
}
