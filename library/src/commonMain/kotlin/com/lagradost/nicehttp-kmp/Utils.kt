package com.lagradost.nicehttp

import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.time.Duration

/**
 * Appends query-string parameters to [url].
 *
 * Handles both URLs that already have a query string and those that do not.
 */
internal fun addParamsToUrl(url: String, params: Map<String, String?>): String {
    if (params.isEmpty()) return url
    val builder = URLBuilder(url)
    params.forEach { (key, value) ->
        if (value != null) builder.parameters.append(key, value)
    }
    return builder.buildString()
}

/**
 * Builds the final [Headers] object for a request.
 *
 * Priority (highest to lowest):
 *  1. Explicit per-request [headers]
 *  2. Cookie header derived from [cookie]
 *  3. Referer header derived from [referer]
 *  4. Default headers (applied by the caller before this point)
 *
 * Header names are normalised to lowercase so lookups are case-insensitive.
 */
fun buildHeaders(
    headers: Map<String, String>,
    referer: String?,
    cookie: Map<String, String>,
): Headers = Headers.build {
    referer?.let { append(HttpHeaders.Referer, it) }
    if (cookie.isNotEmpty()) {
        append(HttpHeaders.Cookie, cookie.entries.joinToString("; ") { "${it.key}=${it.value}" })
    }
    headers.forEach { (k, v) -> append(k, v) }
}

/**
 * Standalone request configuration for [ktorRequestCreator].
 * Provides the same fields as [RequestBuilder] but with its own defaults,
 * so no [Requests] instance is needed.
 */
class StandaloneRequestBuilder {
    var headers: Map<String, String> = mapOf(HttpHeaders.UserAgent to "NiceHttp")
    var referer: String? = null
    var cookies: Map<String, String> = emptyMap()
    var data: Map<String, String>? = null
    var params: Map<String, String> = emptyMap()
    var responseParser: ResponseParser? = null
    var cacheTime: Duration = Duration.ZERO
    var timeout: Duration = Duration.ZERO
    var allowRedirects: Boolean = true
    var interceptor: Interceptor? = null
    var verify: Boolean = true
    var files: List<NiceFile>? = null
    var json: Any? = null
    var requestBody: RequestBody? = null
}

/**
 * Builds a Ktor [HttpRequestBuilder] without requiring a [Requests] instance.
 *
 * Use this when you need to construct a request manually, e.g. to pass to a
 * custom [Interceptor] or inspect the builder before execution.
 *
 * @param method HTTP method from [HttpMethod].
 * @param url    Target URL.
 * @param block  Optional [StandaloneRequestBuilder] configuration.
 */
fun requestCreator(
    method: HttpMethod,
    url: String,
    block: StandaloneRequestBuilder.() -> Unit = {},
): HttpRequestBuilder {
    val builder = StandaloneRequestBuilder().apply(block)
    val finalUrl = addParamsToUrl(url, builder.params)
    val finalHeaders = buildHeaders(builder.headers, builder.referer, builder.cookies)
    val body = buildBody(httpMethod, builder.data, builder.files, builder.json, builder.requestBody, builder.responseParser)

    return HttpRequestBuilder().apply {
        this.method = method
        url(finalUrl)
        finalHeaders.forEach { k, values -> values.forEach { v -> header(k, v) } }
        body?.let { setBody(it.content) }
        if (builder.cacheTime > Duration.ZERO) {
            header(
                HttpHeaders.CacheControl,
                CacheControl.MaxAge(builder.cacheTime.inWholeSeconds.toInt())
            )
        }
        if (builder.timeout > Duration.ZERO) {
            val ms = builder.timeout.inWholeMilliseconds
            timeout {
                requestTimeoutMillis = ms
                connectTimeoutMillis = ms
                socketTimeoutMillis  = ms
            }
        }
    }
}
