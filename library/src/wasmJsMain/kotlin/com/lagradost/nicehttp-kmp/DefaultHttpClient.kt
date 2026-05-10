package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*

actual fun defaultHttpClient(): HttpClient = HttpClient(Js) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
}

actual fun insecureHttpClient(): HttpClient = HttpClient(Js) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
    // WASM/JS runs in a browser/Node.js environment where SSL verification
    // is handled by the runtime and cannot be bypassed programmatically.
    // verify = false is silently ignored on this platform.
}

typealias Requests = BaseRequests
