package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.serverTrust

actual fun defaultHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
}

actual fun insecureHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }

    @OptIn(ExperimentalForeignApi::class)
    engine {
        handleChallenge { _, _, challenge, completionHandler ->
            val serverTrust = challenge.protectionSpace.serverTrust
            if (serverTrust != null) {
                completionHandler(
                    NSURLSessionAuthChallengeUseCredential.convert(),
                    NSURLCredential.credentialForTrust(serverTrust)
                )
            } else {
                // Fallback to default handling if trust is null
                completionHandler(
                    NSURLSessionAuthChallengePerformDefaultHandling.convert(),
                    null
                )
            }
        }
    }
}
