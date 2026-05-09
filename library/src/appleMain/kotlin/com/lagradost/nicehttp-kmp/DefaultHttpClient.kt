package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import platform.Foundation.NSURLCredential
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
    engine {
        handleChallenge { session, task, challenge, completionHandler ->
            val serverTrust = challenge.protectionSpace.serverTrust
            if (serverTrust != null) {
                completionHandler(
                    NSURLSessionAuthChallengeUseCredential,
                    NSURLCredential.credentialForTrust(serverTrust)
                )
            } else {
                // Fallback to default handling if trust is null
                completionHandler(platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling, null)
            }
        }
    }
}
