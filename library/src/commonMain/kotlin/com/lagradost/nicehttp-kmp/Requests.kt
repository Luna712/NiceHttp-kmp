package com.lagradost.nicehttp

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
 * @param defaultCacheTime  Default cache duration. [Duration.ZERO] means no caching.
 * @param defaultTimeout    Default request timeout. [Duration.ZERO] means no timeout.
 * @param responseParser    JSON parser used by [NiceResponse.parsed].
 * @param interceptors      List of [Interceptor]s applied to every request in order.
 */
open class Requests(
    var baseClient: HttpClient = defaultHttpClient(),
    var defaultHeaders: Map<String, String> = mapOf("User-Agent" to "NiceHttp"),
    var defaultReferer: String? = null,
    var defaultData: Map<String, String> = emptyMap(),
    var defaultCookies: Map<String, String> = emptyMap(),
    var defaultCacheTime: Duration = Duration.ZERO,
    var defaultTimeout: Duration = Duration.ZERO,
    var responseParser: ResponseParser? = null,
    var interceptors: MutableList<Interceptor> = mutableListOf(),
) {
    /**
     * Back-compatible constructor accepting the original NiceHttp parameter types.
     * TODO: Deprecate this
     */
    constructor(
        baseClient: HttpClient = defaultHttpClient(),
        defaultHeaders: Map<String, String> = mapOf("User-Agent" to "NiceHttp"),
        defaultReferer: String? = null,
        defaultData: Map<String, String> = emptyMap(),
        defaultCookies: Map<String, String> = emptyMap(),
        defaultCacheTime: Int = 0,
        defaultCacheTimeUnit: NiceTimeUnit = NiceTimeUnit.MINUTES,
        defaultTimeOut: Long = 0L,
        responseParser: ResponseParser? = null,
        interceptors: MutableList<NiceInterceptorCompat> = mutableListOf(),
    ) : this(
        baseClient = baseClient,
        defaultHeaders = defaultHeaders,
        defaultReferer = defaultReferer,
        defaultData = defaultData,
        defaultCookies = defaultCookies,
        defaultCacheTime = defaultCacheTime.toLong().toDuration(defaultCacheTimeUnit.toDurationUnit()),
        defaultTimeout = if (defaultTimeOut <= 0L) Duration.ZERO
            else defaultTimeOut.seconds,
        responseParser = responseParser,
        interceptors = interceptors.map { it.toInterceptor() }.toMutableList(),
    )

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
     * Generic request method. All method shortcuts delegate here.
     *
     * @param method         HTTP method from [HttpMethod].
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
     * @param cacheTime      How long to cache the response. [Duration.ZERO] means no caching.
     * @param timeout        Request timeout. [Duration.ZERO] means no timeout.
     *                       Overrides [defaultTimeout] for this call.
     * @param interceptor    Per-call [Interceptor], appended after [interceptors].
     * @param verify         If false, SSL certificate verification is disabled.
     *                       Only has effect on platforms that support it (JVM/Android, Darwin, Curl, WinHttp).
     *                       Silently ignored on JS/WASM.
     * @param responseParser Overrides [this.responseParser] for this call.
     */
    internal open suspend fun custom(
        method: HttpMethod,
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
        cacheTime: Duration = defaultCacheTime,
        timeout: Duration = defaultTimeout,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse {
        val finalUrl = addParamsToUrl(url, params)
        val finalHeaders = buildHeaders(
            defaultHeaders + headers,
            referer ?: defaultReferer,
            defaultCookies + cookies,
        )
        val body = buildBody(method, data, files, json, requestBody, responseParser)

        // Build all interceptors for this call
        val allInterceptors = interceptors.toMutableList()
        allInterceptors.add(0, LoggingInterceptor())
        if (cacheTime > Duration.ZERO) {
            allInterceptors.add(0, HeadersInterceptor(
                mapOf("Cache-Control" to "max-age=${cacheTime.inWholeSeconds}")
            ))
        }
        // allInterceptors.add(0, CacheInterceptor)
        if (interceptor != null) allInterceptors.add(interceptor)

        // Pick base client
        val clientToUse = when {
            !verify && !allowRedirects -> insecureNoRedirectClient
            !verify                    -> insecureClient
            !allowRedirects            -> noRedirectClient
            else                       -> baseClient
        }

        // Install interceptors via HttpSend
        val client = clientToUse.withInterceptors(allInterceptors)

        val response = client.request {
            this.method = method
            url(finalUrl)
            finalHeaders.forEach { k, values -> values.forEach { v -> header(k, v) } }
            body?.let { setBody(it.content) }
            if (timeout > Duration.ZERO) {
                val ms = timeout.inWholeMilliseconds
                timeout {
                    requestTimeoutMillis = ms
                    connectTimeoutMillis = ms
                    socketTimeoutMillis  = ms
                }
            }
        }

        return NiceResponse(response, responseParser)
    }

    suspend fun get(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this).apply(block)
        return custom(
            HttpMethod.Get, url, builder.headers, builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.cacheTime, builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun post(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this).apply(block)
        return custom(
            HttpMethod.Post, url, builder.headers, builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects,
            builder.cacheTime, builder.timeout, builder.interceptor, builder.verify,
            builder.responseParser,
        )
    }

    suspend fun put(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this).apply(block)
        return custom(
            HttpMethod.Put, url, builder.headers, builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects,
            builder.cacheTime, builder.timeout, builder.interceptor, builder.verify,
            builder.responseParser,
        )
    }

    suspend fun delete(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this).apply(block)
        return custom(
            HttpMethod.Delete, url, builder.headers, builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects,
            builder.cacheTime, builder.timeout, builder.interceptor, builder.verify,
            builder.responseParser,
        )
    }

    suspend fun head(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this).apply(block)
        return custom(
            HttpMethod.Head, url, builder.headers, builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.cacheTime, builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun patch(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this).apply(block)
        return custom(
            HttpMethod.Patch, url, builder.headers, builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects,
            builder.cacheTime, builder.timeout, builder.interceptor, builder.verify,
            builder.responseParser,
        )
    }

    suspend fun options(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this).apply(block)
        return custom(
            HttpMethod.Options, url, builder.headers, builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.cacheTime, builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: NiceTimeUnit = NiceTimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: NiceInterceptorCompat? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        HttpMethod.Get, url, headers, referer, params, cookies,
        null, null, null, null, allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
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
        requestBody: NiceRequestBodyCompat? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: NiceTimeUnit = NiceTimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: NiceInterceptorCompat? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        HttpMethod.Post, url, headers, referer, params, cookies,
        data, files, json, requestBody?.toRequestBody(), allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
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
        requestBody: NiceRequestBodyCompat? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: NiceTimeUnit = NiceTimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: NiceInterceptorCompat? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        HttpMethod.Put, url, headers, referer, params, cookies,
        data, files, json, requestBody?.toRequestBody(), allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
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
        requestBody: NiceRequestBodyCompat? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: NiceTimeUnit = NiceTimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: NiceInterceptorCompat? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        HttpMethod.Delete, url, headers, referer, params, cookies,
        data, files, json, requestBody?.toRequestBody(), allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
    )

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: NiceTimeUnit = NiceTimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: NiceInterceptorCompat? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        HttpMethod.Head, url, headers, referer, params, cookies,
        null, null, null, null, allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
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
        requestBody: NiceRequestBodyCompat? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: NiceTimeUnit = NiceTimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: NiceInterceptorCompat? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        HttpMethod.Patch, url, headers, referer, params, cookies,
        data, files, json, requestBody?.toRequestBody(), allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
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
        requestBody: NiceRequestBodyCompat? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: NiceTimeUnit = NiceTimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: NiceInterceptorCompat? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom(
        HttpMethod.Options, url, headers, referer, params, cookies,
        data, files, json, requestBody?.toRequestBody(), allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
    )
}

private val MUST_HAVE_BODY  = setOf(HttpMethod.Post, HttpMethod.Put)
private val NO_BODY_METHODS = setOf(
    HttpMethod.Get,
    HttpMethod.Head,
    HttpMethod.Options,
    HttpMethod.Trace,
)

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
    method: HttpMethod,
    data: Map<String, String>?,
    files: List<NiceFile>?,
    json: Any?,
    requestBody: RequestBody?,
    responseParser: ResponseParser?,
): RequestBody? {
    if (method in NO_BODY_METHODS) return null
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
            RequestBody.of(
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

        // These methods must always have a body even if empty
        method in MUST_HAVE_BODY -> RequestBody.form(emptyMap())

        else -> null
    }
}
