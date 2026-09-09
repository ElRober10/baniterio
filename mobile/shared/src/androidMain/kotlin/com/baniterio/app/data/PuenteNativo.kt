package com.baniterio.app.data

/**
 * Puente Activity ↔ Compose para resultados de Activity que necesitan
 * `startActivityForResult` clásico (16 bits), porque `MainActivity` es
 * `FragmentActivity` y su validador rechaza los códigos de 32 bits del API
 * moderno (`registerForActivityResult`).
 *
 * `MainActivity.onCreate` fija los `lanzar*`; un composable, justo antes de
 * lanzar, deja su callback en `pendiente*`; `MainActivity.onActivityResult` lo
 * invoca una vez y lo pone a `null`. Hay una sola `MainActivity` y como mucho un
 * editor visible, así que el estado mutable de proceso vale.
 */
object PuenteNativo {
    var lanzarFotoGaleria: (() -> Unit)? = null
    var lanzarFotoCamara: (() -> Unit)? = null
    var lanzarContacto: (() -> Unit)? = null
    var lanzarArchivo: (() -> Unit)? = null
    var pendienteFoto: ((FotoElegida?) -> Unit)? = null
    var pendienteContacto: ((String?) -> Unit)? = null
    var pendienteArchivo: ((ArchivoElegido?) -> Unit)? = null
}
