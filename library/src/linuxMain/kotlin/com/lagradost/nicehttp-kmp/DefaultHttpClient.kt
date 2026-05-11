package com.lagradost.nicehttp

import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*

// This file covers Linux targets (linuxX64, linuxArm64).
// Apple targets (iOS, macOS, tvOS, watchOS) are handled in appleMain via Darwin.
// Windows (mingwX64) is handled in mingwMain via WinHttp.
actual fun defaultHttpClient(): HttpClient = HttpClient(Curl) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
}

actual fun insecureHttpClient(): HttpClient = HttpClient(Curl) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
    engine {
        sslVerify = false
    }
}
