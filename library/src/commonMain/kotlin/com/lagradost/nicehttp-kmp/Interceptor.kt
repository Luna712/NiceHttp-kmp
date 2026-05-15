package com.lagradost.nicehttp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*

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
 * Optional response-side counterpart to [Interceptor].
 * Implement alongside [Interceptor] to mutate response headers before they are finalized,
 * mirroring OkHttp's network interceptor behavior.
 */
fun interface ResponseInterceptor {
    fun applyTo(headers: HeadersBuilder)
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
 * Intercepts outgoing requests and optionally incoming responses to add, overwrite,
 * append, or remove headers. Existing headers with the same name are removed before
 * any overwrite operation. Values are converted to strings via [Any.toString], so typed
 * Ktor values like [ContentType] and [CacheControl] work directly.
 *
 * ```kotlin
 * HeadersInterceptor {
 *     set(HttpHeaders.Authorization, "Bearer $token")
 *     append(HttpHeaders.Accept, ContentType.Application.Json)
 *     removeHeader(HttpHeaders.UserAgent)
 *     headers(
 *         HttpHeaders.XRequestId to requestId,
 *         HttpHeaders.XForwardedFor to clientIp,
 *     )
 *
 *     response {
 *         removeHeader(HttpHeaders.CacheControl) // Remove site cache
 *         removeHeader(HttpHeaders.Pragma) // Remove site cache
 *         addHeader(HttpHeaders.CacheControl, "${CacheControl.ONLY_IF_CACHED}, ${CacheControl.MAX_STALE}=${Int.MAX_VALUE}")
 *     }
 * }
 * ```
 */
class HeadersInterceptor private constructor(
    private val requestActions: List<HeaderAction>,
    private val responseActions: List<HeaderAction>,
) : Interceptor, ResponseInterceptor {

    private constructor(builder: Builder) : this(
        builder.buildRequestActions(),
        builder.buildResponseActions(),
    )

    sealed class HeaderAction {
        abstract val name: String

        sealed class Mutation : HeaderAction() {
            abstract val value: String
            data class Overwrite(override val name: String, override val value: String) : Mutation()
            data class Append(override val name: String, override val value: String) : Mutation()
        }

        data class Remove(override val name: String) : HeaderAction()
        data class ReplaceAll(val headers: List<Pair<String, String>>) : HeaderAction() {
            override val name get() = ""
        }
    }

    abstract class HeadersMutationBuilder {
        internal val actions = mutableListOf<HeaderAction>()

        /** Overwrites any existing header with the same name. Mirrors Ktor's own [header] naming. */
        fun header(name: String, value: Any) { actions.add(HeaderAction.Mutation.Overwrite(name, value.toString())) }
        /** Overwrites any existing header with the same name. */
        fun set(name: String, value: Any) { actions.add(HeaderAction.Mutation.Overwrite(name, value.toString())) }
        /** Overwrites any existing header with the same name. */
        fun replace(name: String, value: Any) { actions.add(HeaderAction.Mutation.Overwrite(name, value.toString())) }
        /** Appends a new value alongside any existing values for the same name. */
        fun add(name: String, value: Any) { actions.add(HeaderAction.Mutation.Append(name, value.toString())) }
        /** Appends a new value alongside any existing values for the same name. */
        fun append(name: String, value: Any) { actions.add(HeaderAction.Mutation.Append(name, value.toString())) }
        /** Appends a new value alongside any existing values for the same name. Mirrors OkHttp's [addHeader] naming. */
        fun addHeader(name: String, value: Any) { actions.add(HeaderAction.Mutation.Append(name, value.toString())) }
        /** Removes all values for the given header name. */
        fun remove(name: String) { actions.add(HeaderAction.Remove(name)) }
        /** Removes all values for the given header name. Mirrors OkHttp's [removeHeader] naming. */
        fun removeHeader(name: String) { actions.add(HeaderAction.Remove(name)) }
        /** Removes all existing headers and replaces them entirely with the given pairs. */
        fun headers(vararg pairs: Pair<String, Any>) {
            actions.add(HeaderAction.ReplaceAll(pairs.map { (name, value) -> name to value.toString() }))
        }
        /** Removes all existing headers and replaces them entirely with the given native Ktor [Headers] instance. */
        fun headers(nativeHeaders: Headers) {
            actions.add(HeaderAction.ReplaceAll(nativeHeaders.flattenEntries()))
        }
        /** Removes all existing headers and replaces them entirely using a native Ktor [HeadersBuilder] block. */
        fun headers(block: HeadersBuilder.() -> Unit) {
            actions.add(HeaderAction.ReplaceAll(Headers.build(block).flattenEntries()))
        }

        internal fun buildActions() = actions.toList()
    }

    /** Stable binary entry point. [invoke] always delegates here for binary compatibility. */
    class Builder : HeadersMutationBuilder() {
        private val responseActions = mutableListOf<HeaderAction>()

        fun response(block: ResponseBuilder.() -> Unit) {
            responseActions.addAll(ResponseBuilder().apply(block).actions)
        }

        internal fun buildRequestActions() = actions.toList()
        internal fun buildResponseActions() = responseActions.toList()
    }

    class ResponseBuilder : HeadersMutationBuilder()

    companion object {
        operator fun invoke(block: Builder.() -> Unit): HeadersInterceptor =
            HeadersInterceptor(Builder().apply(block))
    }

    private fun applyActions(actions: List<HeaderAction>, headers: HeadersBuilder) {
        actions.forEach { action ->
            when (action) {
                is HeaderAction.Remove -> headers.entries()
                    .filter { it.key.equals(action.name, ignoreCase = true) }
                    .forEach { headers.remove(it.key) }
                is HeaderAction.ReplaceAll -> {
                    headers.clear()
                    action.headers.forEach { (name, value) -> headers.append(name, value) }
                }
                is HeaderAction.Mutation.Overwrite -> {
                    // Remove existing headers with the same
                    // case-insensitive name first.
                    headers.entries()
                        .filter { it.key.equals(action.name, ignoreCase = true) }
                        .forEach { headers.remove(it.key) }
                    headers.append(action.name, action.value)
                }
                is HeaderAction.Mutation.Append -> headers.append(action.name, action.value)
            }
        }
    }

    override fun applyTo(headers: HeadersBuilder) {
        applyActions(responseActions, headers)
    }

    override suspend fun intercept(ctx: HttpSendInterceptorContext): HttpClientCall {
        applyActions(requestActions, ctx.request.headers)
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
 * On any exception or non-2xx response from the primary URL, retries against [fallbackUrl].
 * @param primaryUrl base URL of the primary host, e.g. "https://api.example.com"
 * @param fallbackUrl base URL of the fallback host, e.g. "https://api-backup.example.com"
 * @param shouldFallback called with each [HttpClientCall]; return true to try the fallback.
 */
class FallbackUrlInterceptor(
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

        return ctx.proceed {
            val originalUrl = url.buildString()
            url.takeFrom(originalUrl.replaceFirst(primaryUrl, fallbackUrl))
        }
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
            values.forEach { value -> log("--> $key: $value") }
        }

        val call = ctx.proceed()
        log("<-- ${call.response.status.value} ${call.response.call.request.url}")
        call.response.headers.forEach { key, values ->
            values.forEach { value -> log("<-- $key: $value") }
        }

        return call
    }
}

/**
 * Installs a list of [Interceptor]s into an [HttpClient] via Ktor's [HttpSend] plugin.
 * [ResponseInterceptor]s are additionally hooked into [HttpReceivePipeline.Before] so they
 * run after the response arrives but before it is finalized, mirroring OkHttp's network interceptor.
 * Returns a new configured client, does not modify the original.
 * Interceptors run inside Ktor's pipeline so they properly interact with
 * [HttpCache], [HttpTimeout], and other plugins.
 * Interceptors are applied in order: first in list = first to run.
 */
@OptIn(InternalAPI::class)
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

        val responseInterceptors = interceptors.filterIsInstance<ResponseInterceptor>()
        if (responseInterceptors.isNotEmpty()) {
            client.plugin(HttpSend).intercept { request ->
                val call = proceed(request)
                val mutatedHeaders = HeadersBuilder().apply {
                    call.response.headers.forEach { name, values ->
                        values.forEach { append(name, it) }
                    }
                    responseInterceptors.forEach { it.applyTo(this) }
                }.build()

                @OptIn(InternalAPI::class)
                call.setResponse(
                    DefaultHttpResponse(
                        call,
                        HttpResponseData(
                            call.response.status,
                            call.response.requestTime,
                            mutatedHeaders,
                            call.response.version,
                            call.response.rawContent,
                            call.response.coroutineContext,
                        )
                    )
                )
                call
            }
        }
    }
}
