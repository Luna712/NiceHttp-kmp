package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*

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
            completionHandler(
                platform.Foundation.NSURLSessionAuthChallengeDisposition.NSURLSessionAuthChallengeUseCredential,
                platform.Foundation.NSURLCredential.credentialForTrust(challenge.protectionSpace.serverTrust)
            )
        }
    }
}
