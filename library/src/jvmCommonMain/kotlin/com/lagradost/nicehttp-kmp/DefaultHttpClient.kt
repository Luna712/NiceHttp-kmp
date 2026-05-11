package com.lagradost.nicehttp

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*

actual fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
    // OkHttp engine exposes the raw builder via `engine { preconfigured = ... }`
    // so callers can still attach DNS-over-HTTPS, custom interceptors, etc.
}

actual fun insecureHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
    engine {
        config {
            ignoreAllSSLErrors()
        }
    }
}
