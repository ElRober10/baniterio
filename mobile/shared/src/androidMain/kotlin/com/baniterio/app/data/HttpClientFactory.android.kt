package com.baniterio.app.data

import io.ktor.client.HttpClient

actual fun crearHttpClient(): HttpClient =
    HttpClient(io.ktor.client.engine.okhttp.OkHttp) { configComun() }
