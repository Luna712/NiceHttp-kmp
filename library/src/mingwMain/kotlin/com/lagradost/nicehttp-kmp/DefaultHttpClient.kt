package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.winhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*

actual fun defaultHttpClient(): HttpClient = HttpClient(WinHttp) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
}

actual fun insecureHttpClient(): HttpClient = HttpClient(WinHttp) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
    engine {
        sslVerify = false
    }
}
