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
 * Values are converted to strings via [Any.toString], so typed Ktor
 * header values like [CacheControl] work directly.
 *
 * Construct via the DSL builder:
 * ```kotlin
 * HeadersInterceptor {
 *     header(HttpHeaders.CacheControl, CacheControl.MaxAge(maxAgeSeconds = 300))
 *     header(HttpHeaders.Accept, ContentType.Application.Json)
 *     remove(HttpHeaders.UserAgent)
 * }
 * ```
 */
class HeadersInterceptor(
    private val headers: List<Pair<String, String>>,
    private val removals: List<String>,
) : Interceptor {

    class Builder {
        private val headers = mutableListOf<Pair<String, String>>()
        private val removals = mutableListOf<String>()

        /** Add or replace a header. Mirrors Ktor's own [header] naming. */
        fun header(name: String, value: Any) { headers.add(name to value.toString()) }
        /** Add a header, communicating intent to insert a new value. */
        fun add(name: String, value: Any) { headers.add(name to value.toString()) }
        /** Set a header, communicating intent to assign a value regardless of existing. */
        fun set(name: String, value: Any) { headers.add(name to value.toString()) }
        /** Replace a header, communicating intent to overwrite an existing value. */
        fun replace(name: String, value: Any) { headers.add(name to value.toString()) }
        /** Remove a header entirely from the request. */
        fun remove(name: String) { removals.add(name) }

        internal fun buildHeaders() = headers.toList()
        internal fun buildRemovals() = removals.toList()
    }

    companion object {
        operator fun invoke(block: Builder.() -> Unit): HeadersInterceptor {
            val builder = Builder().apply(block)
            return HeadersInterceptor(builder.buildHeaders(), builder.buildRemovals())
        }
    }

    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        removals.forEach { k ->
            ctx.request.headers.entries()
                .filter { it.key.equals(k, ignoreCase = true) }
                .forEach { ctx.request.headers.remove(it.key) }
        }
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
