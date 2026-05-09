package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.plugins.sender.*
import io.ktor.client.request.*

/**
 * Installs a list of [Interceptor]s into an [HttpClient] via [HttpSend].
 * Returns a new configured client — does not modify the original.
 */
internal fun HttpClient.withInterceptors(
    interceptors: List<Interceptor>,
): HttpClient {
    if (interceptors.isEmpty()) return this
    return config {
        install(HttpSend) {
            for (interceptor in interceptors.reversed()) {
                intercept { request ->
                    val ctx = HttpSendInterceptorContext(request) { req -> execute(req) }
                    interceptor.intercept(ctx)
                }
            }
        }
    }
}
