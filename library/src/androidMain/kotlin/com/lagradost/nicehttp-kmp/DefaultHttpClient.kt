package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*

actual fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpRequestRetry) { noRetry() }
    // OkHttp engine exposes the raw builder via `engine { preconfigured = ... }`
    // so callers can still attach DNS-over-HTTPS, custom interceptors, etc.
}
