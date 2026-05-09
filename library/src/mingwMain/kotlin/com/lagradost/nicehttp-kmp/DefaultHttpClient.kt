package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.winhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import platform.winhttp.*

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
        protocolConfig {
            securityFlags = (
                SECURITY_FLAG_IGNORE_CERT_CN_INVALID or
                SECURITY_FLAG_IGNORE_CERT_DATE_INVALID or
                SECURITY_FLAG_IGNORE_UNKNOWN_CA or
                SECURITY_FLAG_IGNORE_REVOCATION
            )
        }
    }
}
