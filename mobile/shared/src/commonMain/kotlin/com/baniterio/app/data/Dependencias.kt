package com.baniterio.app.data

/**
 * Grafo de dependencias de la app. Se construye una vez en cada entry point
 * (Android / iOS) llamando a [crearDependencias], pasando el [AlmacenCredenciales]
 * de la plataforma (el de Android necesita un [android.content.Context]).
 */
class Dependencias(
    val repo: AuthRepository,
    val almacen: AlmacenCredenciales,
)

/**
 * Fábrica del grafo. Vive en el módulo compartido para que el tipo [io.ktor.client.HttpClient]
 * (dependencia `implementation`, no expuesta transitivamente) no tenga que resolverse desde
 * los módulos de plataforma.
 */
fun crearDependencias(almacen: AlmacenCredenciales): Dependencias =
    Dependencias(
        repo = AuthRepositoryImpl(crearHttpClient()),
        almacen = almacen,
    )
