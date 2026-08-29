package com.baniterio.app.data

import io.ktor.client.HttpClient

actual fun crearHttpClient(): HttpClient =
    HttpClient(io.ktor.client.engine.darwin.Darwin) { configComun() }
