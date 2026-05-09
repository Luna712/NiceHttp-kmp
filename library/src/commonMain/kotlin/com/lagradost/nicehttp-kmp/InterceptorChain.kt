package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*

/**
 * Installs a list of [Interceptor]s into an [HttpClient] via Ktor's [HttpSend] plugin.
 * Returns a new configured client — does not modify the original.
 * Interceptors run inside Ktor's pipeline so they properly interact with
 * [HttpCache], [HttpTimeout], and other plugins.
 * Interceptors are applied in order: first in list = first to run.
 */
internal fun HttpClient.withInterceptors(
    interceptors: List<Interceptor>,
): HttpClient {
    if (interceptors.isEmpty()) return this
    return config {
        install(HttpSend) {
            for (interceptor in interceptors.reversed()) {
                intercept { request ->
                    interceptor.intercept(
                        HttpSendInterceptorContext(request) { execute(it) }
                    )
                }
            }
        }
    }
}
