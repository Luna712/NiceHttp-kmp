package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Security.SecTrustRef

actual fun defaultHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
}

actual fun insecureHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
    engine {
        handleChallenge { _, _, challenge, completionHandler ->
            val trust = challenge.protectionSpace.serverTrust
            completionHandler(
                NSURLSessionAuthChallengeUseCredential,
                NSURLCredential.credentialForTrust(trust)
            )
        }
    }
}
