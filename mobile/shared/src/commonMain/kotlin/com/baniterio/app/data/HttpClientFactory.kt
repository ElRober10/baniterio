package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun crearHttpClient(): HttpClient

/** Config común de Ktor: JSON tolerante, timeouts, y expectSuccess para que
 *  un 4xx/5xx lance ResponseException (lo aprovecha AuthRepositoryImpl). */
fun HttpClientConfig<*>.configComun() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
    }
    expectSuccess = true
}
