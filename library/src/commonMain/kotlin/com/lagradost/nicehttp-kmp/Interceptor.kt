package com.lagradost.nicehttp.kmp

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sender.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * KMP interceptor backed by Ktor's [HttpSend] plugin.
 * Runs inside Ktor's actual request pipeline, properly interacting
 * with [HttpCache], [HttpTimeout], and other plugins.
 */
fun interface Interceptor {
    suspend fun intercept(sender: HttpSendInterceptorContext): HttpClientCall
}

/** Context passed to each interceptor, mirrors OkHttp's Chain concept. */
class HttpSendInterceptorContext(
    val request: HttpRequestBuilder,
    val execute: suspend (HttpRequestBuilder) -> HttpClientCall,
) {
    /** Proceed with the current request unchanged. */
    suspend fun proceed(): HttpClientCall = execute(request)

    /** Proceed with a modified request. */
    suspend fun proceed(block: HttpRequestBuilder.() -> Unit): HttpClientCall {
        request.block()
        return execute(request)
    }
}

/** Adds/replaces headers on every request. */
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

/** Retries failed requests up to [maxRetries] times. */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val shouldRetry: (HttpClientCall) -> Boolean = { !it.response.status.isSuccess() },
) : Interceptor {
    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        var call = ctx.proceed()
        var retries = 0
        while (shouldRetry(call) && retries < maxRetries) {
            retries++
            call = ctx.execute(ctx.request)
        }
        return call
    }
}

/** Logs requests and responses. */
class LoggingInterceptor(
    private val log: (String) -> Unit = ::println,
) : Interceptor {
    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        log("--> ${ctx.request.method.value} ${ctx.request.url.buildString()}")
        val call = ctx.proceed()
        log("<-- ${call.response.status.value} ${call.response.request.url}")
        return call
    }
}

/** Retries against a fallback URL if the primary fails. */
/* class FallbackUrlInterceptor(
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
        val parsed = io.ktor.http.Url(newUrl)
        ctx.request.url.apply {
            protocol = parsed.protocol
            host = parsed.host
            port = parsed.port
            pathSegments = parsed.pathSegments
            parameters.clear()
            parsed.parameters.forEach { key, values ->
                values.forEach { parameters.append(key, it) }
            }
        }
        return ctx.execute(ctx.request)
    }
} */

/**
 * Equivalent of original NiceHttp's CacheInterceptor.
 * Forces cache usage by setting Cache-Control headers on the request.
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
