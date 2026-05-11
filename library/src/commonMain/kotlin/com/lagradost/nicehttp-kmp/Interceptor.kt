package com.lagradost.nicehttp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * KMP interceptor backed by Ktor's [HttpSend] plugin.
 * Runs inside Ktor's actual request pipeline, properly interacting
 * with [HttpCache], [HttpTimeout], and other plugins.
 * On JVM/Android, okhttp3.Interceptor can be converted via .toNiceInterceptor().
 */
fun interface Interceptor {
    suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall
}

/**
 * Context passed to each [Interceptor], wrapping Ktor's [HttpSendInterceptor].
 * Hides Ktor internals from the public API while still running inside
 * Ktor's pipeline so [HttpCache], [HttpTimeout] etc. are honoured.
 */
class HttpSendInterceptorContext(
    val request: HttpRequestBuilder,
    val execute: suspend (HttpRequestBuilder) -> HttpClientCall,
) {
    val url: String get() = request.url.buildString()
    val headers: Headers get() = request.headers.build()
    val method: String get() = request.method.value

    /** Proceed with the current request unchanged. */
    suspend fun proceed(): HttpClientCall = execute(request)

    /** Proceed with a modified request. */
    suspend fun proceed(block: HttpRequestBuilder.() -> Unit): HttpClientCall {
        request.block()
        return execute(request)
    }

    /** Proceed with a different request builder entirely. */
    suspend fun proceed(request: HttpRequestBuilder): HttpClientCall =
        execute(request)
}

/**
 * Adds or replaces headers on every request.
 * Existing headers with the same name are removed first.
 */
class HeadersInterceptor(
    private val headers: Map<String, String>,
) : Interceptor {
    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        headers.forEach { (k, v) ->
            // Remove existing headers with the same
            // case-insensitive name first.
            ctx.request.headers.entries()
                .filter { it.key.equals(k, ignoreCase = true) }
                .forEach { ctx.request.headers.remove(it.key) }
            ctx.request.headers.append(k, v)
        }
        return ctx.proceed()
    }
}

/**
 * Retries failed requests up to [maxRetries] times.
 * @param shouldRetry called with each [HttpClientCall]; return true to retry.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val shouldRetry: (HttpClientCall) -> Boolean = { !it.response.status.isSuccess() },
) : Interceptor {
    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        var call = ctx.proceed()
        var retries = 0
        while (shouldRetry(call) && retries < maxRetries) {
            retries++
            call = ctx.proceed()
        }
        return call
    }
}

/**
 * Retries a failed request against a fallback URL.
 * Replaces the first occurrence of [primaryUrl] with [fallbackUrl] in the request URL.
 * @param shouldFallback called with each [HttpClientCall]; return true to use fallback.
 */
/*class FallbackUrlInterceptor(
    private val primaryUrl: String,
    private val fallbackUrl: String,
    private val shouldFallback: (HttpClientCall) -> Boolean = { !it.response.status.isSuccess() },
) : Interceptor {
    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        try {
            val call = ctx.proceed()
            if (!shouldFallback(call)) return call
        } catch (_: Exception) {
        }

        val newUrl = ctx.request.url.buildString().replaceFirst(primaryUrl, fallbackUrl)
        val parsed = Url(newUrl)
        return ctx.proceed {
            url.protocol = parsed.protocol
            url.host = parsed.host
            url.port = parsed.port
            url.pathSegments = parsed.pathSegments
            url.parameters.clear()
            parsed.parameters.forEach { key, values ->
                values.forEach { url.parameters.append(key, it) }
            }
        }
    }
}*/

/**
 * Equivalent of original NiceHttp's CacheInterceptor.
 * Strips server cache headers and forces Ktor's [HttpCache] to serve from cache.
 * Applied automatically to every request in [Requests.custom].
 */
object CacheInterceptor : Interceptor {
    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        // Try cache first
        ctx.request.headers.apply {
            remove("Cache-Control")
            remove("Pragma")
            append("Cache-Control", "only-if-cached, max-stale=${Int.MAX_VALUE}")
        }

        val cachedCall = ctx.proceed()

        // 504 means no cache available - fall back to normal online request
        if (cachedCall.response.status.value == 504) {
            ctx.request.headers.apply {
                remove("Cache-Control")
                remove("Pragma")
            }
            return ctx.proceed()
        }

        return cachedCall
    }
}

/**
 * Logs request and response details.
 * @param log logging function, defaults to [println].
 */
class LoggingInterceptor(
    private val log: (String) -> Unit = ::println,
) : Interceptor {
    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        log("--> ${ctx.method} ${ctx.url}")
        ctx.headers.forEach { key, values ->
            values.forEach { value -> log("$key: $value") }
        }

        val call = ctx.proceed()
        log("<-- ${call.response.status.value} ${call.response.call.request.url}")

        return call
    }
}

/**
 * Installs a list of [Interceptor]s into an [HttpClient] via Ktor's [HttpSend] plugin.
 * Returns a new configured client, does not modify the original.
 * Interceptors run inside Ktor's pipeline so they properly interact with
 * [HttpCache], [HttpTimeout], and other plugins.
 * Interceptors are applied in order: first in list = first to run.
 */
internal fun HttpClient.withInterceptors(
    interceptors: List<Interceptor>,
): HttpClient {
    if (interceptors.isEmpty()) return this
    return config {
        install(HttpSend)
    }.also { client ->
        interceptors.reversed().forEach { interceptor ->
            client.plugin(HttpSend).intercept { request ->
                interceptor.intercept(
                    HttpSendInterceptorContext(request) { req -> execute(req) }
                )
            }
        }
    }
}
