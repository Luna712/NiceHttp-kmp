package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlin.time.DurationUnit

/**
 * Multiplatform HTTP client modelled after the original NiceHttp [Requests] class.
 *
 * Instead of OkHttp it uses a Ktor [HttpClient] whose engine is resolved per-platform:
 *  - JVM / Android  → OkHttp engine  (retains full OkHttp power for DNS-over-HTTPS, etc.)
 *  - JS / WASM/JS   → Js engine
 *  - iOS / macOS    → Darwin engine
 *  - Linux          → Curl engine
 *  - Windows        → WinHttp engine
 *
 * Pass a pre-configured [HttpClient] if you need custom TLS, logging, or auth plugins.
 *
 * @param baseClient        The Ktor [HttpClient] used for all requests.
 * @param defaultHeaders    Headers sent with every request (overridable per-call).
 * @param defaultReferer    Referer header sent when not overridden per-call.
 * @param defaultData       Default form data sent with every request.
 * @param defaultCookies    Cookies merged into every request.
 * @param defaultCacheTime  Default cache time, used with [defaultCacheUnit]. 0 means no caching.
 * @param defaultCacheUnit  Unit for [defaultCacheTime], defaults to minutes.
 * @param defaultTimeOut    Default timeout in seconds. 0 means no timeout.
 * @param responseParser    JSON parser used by [INiceResponse.parsed].
 * @param interceptors      List of [Interceptor]s applied to every request in order.
 */
open class Requests(
    var baseClient: HttpClient = defaultHttpClient(),
    var defaultHeaders: Map<String, String> = mapOf("user-agent" to "NiceHttp"),
    var defaultReferer: String? = null,
    var defaultData: Map<String, String> = emptyMap(),
    var defaultCookies: Map<String, String> = emptyMap(),
    var defaultCacheTime: Int = 0,
    var defaultCacheTimeUnit: DurationUnit = DurationUnit.MINUTES,
    var defaultTimeout: Long = 0L,
    var responseParser: ResponseParser? = null,
    var interceptors: MutableList<Interceptor> = mutableListOf(),
) {
    fun addInterceptor(interceptor: Interceptor) = interceptors.add(interceptor)
    fun removeInterceptor(interceptor: Interceptor) = interceptors.remove(interceptor)

    private val noRedirectClient: HttpClient by lazy {
        baseClient.config { followRedirects = false }
    }

    private val insecureClient: HttpClient by lazy {
        insecureHttpClient()
    }

    private val insecureNoRedirectClient: HttpClient by lazy {
        insecureHttpClient().config { followRedirects = false }
    }

    /**
     * Generic request method – all verb shortcuts delegate here.
     *
     * @param method         HTTP method string ("GET", "POST", …).
     * @param url            Target URL.
     * @param headers        Extra headers merged on top of [defaultHeaders].
     * @param referer        Overrides [defaultReferer] for this call.
     * @param params         Query-string parameters appended to [url].
     * @param cookies        Merged with [defaultCookies].
     * @param data           URL-encoded form body (mutually exclusive with [json]/[requestBody]).
     * @param files          Multipart form parts.
     * @param json           Object serialised to JSON, or a raw [JsonAsString].
     * @param requestBody    Fully pre-built [RequestBody] (highest priority body).
     * @param allowRedirects Whether to follow HTTP redirects.
     * @param cacheTime      How long to cache the response. 0 means no caching.
     *                       Uses [cacheUnit] as the time unit.
     * @param cacheUnit      Unit for [cacheTime], defaults to [defaultCacheUnit].
     * @param timeout        Request timeout in seconds. 0 means no timeout.
     *                       Overrides [defaultTimeOut] for this call.
     * @param interceptor    Per-call [Interceptor], appended after [interceptors].
     * @param verify         If false, SSL certificate verification is disabled.
     *                       Only has effect on platforms that support it (JVM/Android, Darwin, Curl, WinHttp).
     *                       Silently ignored on JS/WASM.
     * @param responseParser Overrides [this.responseParser] for this call.
     */
    open suspend fun custom(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: DurationUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeout,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): INiceResponse {
        val finalUrl = addParamsToUrl(url, params)
        val finalHeaders = buildHeaders(
            defaultHeaders + headers,
            referer ?: defaultReferer,
            defaultCookies + cookies,
        )
        val body = buildBody(method, data, files, json, requestBody, responseParser)

        val requestBuilder = HttpRequestBuilder().apply {
            this.method = HttpMethod(method.uppercase())
            url(finalUrl)
            finalHeaders.forEach { k, values -> values.forEach { v -> header(k, v) } }
            body?.let { setBody(it.content) }
            if (timeout > 0L) {
                val ms = timeout * 1000L
                timeout {
                    requestTimeoutMillis = ms
                    connectTimeoutMillis = ms
                    socketTimeoutMillis  = ms
                }
            }
        }

        // Merge instance-level interceptors with the per-call interceptor
        val allInterceptors = interceptors.toMutableList()
        allInterceptors.add(0, CacheInterceptor)

        if (cacheTime > 0) {
            val seconds = when (cacheUnit) {
                DurationUnit.SECONDS -> cacheTime
                DurationUnit.MINUTES -> cacheTime * 60
                DurationUnit.HOURS   -> cacheTime * 3600
                DurationUnit.DAYS    -> cacheTime * 86400
                else                 -> cacheTime * 60
            }
            allInterceptors.add(0, HeadersInterceptor(mapOf("Cache-Control" to "max-age=$seconds")))
        }

        if (interceptor != null) allInterceptors.add(interceptor)

        val clientToUse = when {
            !verify && !allowRedirects -> insecureNoRedirectClient
            !verify                    -> insecureClient
            !allowRedirects            -> noRedirectClient
            else                       -> baseClient
        }

        return executeWithInterceptors(allInterceptors, requestBuilder, clientToUse, responseParser)
    }

    // ── Verb shortcuts ────────────────────────────────────────────────────────

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: DurationUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeout,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        "GET", url, headers, referer, params, cookies, null, null, null, null,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: DurationUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeout,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        "POST", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )

    suspend fun put(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: DurationUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeout,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        "PUT", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )

    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: DurationUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeout,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        "DELETE", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: DurationUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeout,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        "HEAD", url, headers, referer, params, cookies, null, null, null, null,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )

    suspend fun patch(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: DurationUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeout,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        "PATCH", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )

    suspend fun options(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: DurationUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeout,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        "OPTIONS", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )
}

// ── Body builder ──────────────────────────────────────────────────────────────

private val NO_BODY_METHODS = setOf("GET", "HEAD")
private val MUST_HAVE_BODY  = setOf("POST", "PUT")

/**
 * Constructs the [RequestBody] for the request, following the same priority rules
 * as the original NiceHttp:
 *
 * 1. [requestBody] (pre-built, highest priority)
 * 2. [data] (URL-encoded form)
 * 3. [json] (JSON body)
 * 4. [files] (multipart)
 * 5. Empty form body for POST/PUT when nothing else is provided
 * 6. null for GET/HEAD/etc.
 */
internal fun buildBody(
    method: String,
    data: Map<String, String>?,
    files: List<NiceFile>?,
    json: Any?,
    requestBody: RequestBody?,
    responseParser: ResponseParser?,
): RequestBody? {
    val upper = method.uppercase()
    if (upper in NO_BODY_METHODS) return null
    if (requestBody != null) return requestBody

    return when {
        !data.isNullOrEmpty() -> RequestBody.form(data)

        json != null -> {
            val jsonString = when {
                json is JsonAsString   -> json.string
                json is String         -> json
                responseParser != null -> responseParser.writeValueAsString(json)
                else                   -> json.toString()
            }
            val ct = if (json is String) RequestBodyTypes.TEXT else RequestBodyTypes.JSON
            RequestBody.text(jsonString, ct)
        }

        !files.isNullOrEmpty() -> {
            RequestBody(
                MultiPartFormDataContent(
                    formData {
                        files.forEach { file ->
                            if (file.bytes != null) {
                                append(
                                    key = file.name,
                                    value = file.bytes,
                                    headers = Headers.build {
                                        append(
                                            HttpHeaders.ContentDisposition,
                                            "form-data; name=\"${file.name}\"; filename=\"${file.fileName}\""
                                        )
                                        file.fileType?.let { append(HttpHeaders.ContentType, it) }
                                    }
                                )
                            } else {
                                append(file.name, file.fileName)
                            }
                        }
                    }
                )
            )
        }

        // POST/PUT must always have a body even if empty
        upper in MUST_HAVE_BODY -> RequestBody.form(emptyMap())

        else -> null
    }
}
