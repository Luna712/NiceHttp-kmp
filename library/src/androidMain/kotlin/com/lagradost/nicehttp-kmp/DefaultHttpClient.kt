package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*

actual fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpRequestRetry) { noRetry() }
}
