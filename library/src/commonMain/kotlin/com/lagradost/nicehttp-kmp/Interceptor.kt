package com.lagradost.nicehttp.kmp

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*

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
 * KMP interceptor backed by Ktor's [HttpSend] plugin.
 * Runs inside Ktor's actual request pipeline, properly interacting
 * with [HttpCache], [HttpTimeout], and other plugins.
 * On JVM/Android, okhttp3.Interceptor can be converted via .toNiceInterceptor().
 */
fun interface Interceptor {
    suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall
}

/**
 * Equivalent of original NiceHttp's CacheInterceptor.
 * Strips server cache headers and forces Ktor's [HttpCache] to serve from cache.
 * Applied automatically to every request in [Requests.custom].
 */
object CacheInterceptor : Interceptor {
    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        ctx.request.headers.apply {
            remove("Cache-Control")
            remove("Pragma")
            append("Cache-Control", "only-if-cached, max-stale=${Int.MAX_VALUE}")
        }
        return ctx.proceed()
    }
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
            ctx.request.headers.remove(k)
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
 * Logs request and response details.
 * @param log logging function, defaults to [println].
 */
class LoggingInterceptor(
    private val log: (String) -> Unit = ::println,
) : Interceptor {
    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        log("--> ${ctx.method} ${ctx.url}")
        val call = ctx.proceed()
        log("<-- ${call.response.status.value} ${call.response.request.url}")
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
