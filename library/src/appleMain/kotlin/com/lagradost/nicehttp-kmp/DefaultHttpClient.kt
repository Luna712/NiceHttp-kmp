package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import platform.Foundation.*
import platform.darwin.*

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
        configureSession {
            setSessionDelegate(object : NSURLSessionDelegate(
                NSObject(),
                NSURLSessionTaskDelegateProtocol
            ) {
                override fun URLSession(
                    session: NSURLSession,
                    task: NSURLSessionTask,
                    didReceiveChallenge: NSURLAuthenticationChallenge,
                    completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
                ) {
                    // Accept all certificates
                    completionHandler(
                        NSURLSessionAuthChallengeUseCredential,
                        NSURLCredential.credentialForTrust(
                            didReceiveChallenge.protectionSpace.serverTrust!!
                        )
                    )
                }
            })
        }
    }
}
