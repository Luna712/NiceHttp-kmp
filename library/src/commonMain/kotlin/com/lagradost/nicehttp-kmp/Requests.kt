package com.lagradost.nicehttp

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.charsets.*
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
 * @param baseHttpClient    The Ktor [HttpClient] used for all requests.
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
    var baseHttpClient: HttpClient = defaultHttpClient(),
    var defaultHeaders: Map<String, String> = mapOf(HttpHeaders.UserAgent to "NiceHttp"),
    var defaultReferer: String? = null,
    var defaultData: Map<String, String> = emptyMap(),
    var defaultCookies: Map<String, String> = emptyMap(),
    var defaultCacheTime: Duration = Duration.ZERO,
    var defaultTimeout: Duration = Duration.ZERO,
    var responseParser: ResponseParser? = null,
    var interceptors: MutableList<Interceptor> = mutableListOf(),
) {
    /**
     * Back-compat companion. On JVM/Android this inherits the legacy `Call.await()`
     * extension (see [RequestsCompanionCompat]).
     */
    companion object : RequestsCompanionCompat()

    var baseClient: NiceOkHttpClientCompat = defaultNiceOkHttpClientCompat()
    set(value) {
        field = value
        baseHttpClient = value.toHttpClient()
    }

    /**
     * Back-compatible constructor accepting the original NiceHttp parameter types.
     * Use the primary constructor with [Duration] and [Interceptor] directly instead.
     */
    @Deprecated(
        "Use the primary constructor with Duration and Interceptor instead. " +
            "Replace defaultCacheTime/defaultCacheTimeUnit with a Duration (e.g. defaultCacheTime.minutes), " +
            "defaultTimeOut with a Duration (e.g. defaultTimeOut.seconds), " +
            "and OkHttp Interceptor with Interceptor from NiceHttp.",
        level = DeprecationLevel.WARNING,
    )
    constructor(
        baseClient: NiceOkHttpClientCompat,
        defaultHeaders: Map<String, String> = mapOf(HttpHeaders.UserAgent to "NiceHttp"),
        defaultReferer: String? = null,
        defaultData: Map<String, String> = emptyMap(),
        defaultCookies: Map<String, String> = emptyMap(),
        defaultCacheTime: Int = 0,
        defaultCacheTimeUnit: NiceTimeUnit = NiceTimeUnit.MINUTES,
        defaultTimeOut: Long = 0L,
        responseParser: ResponseParser? = null,
    ) : this(
        baseHttpClient = baseClient.toHttpClient(),
        defaultHeaders = defaultHeaders,
        defaultReferer = defaultReferer,
        defaultData = defaultData,
        defaultCookies = defaultCookies,
        defaultCacheTime = defaultCacheTime.toLong().toDuration(defaultCacheTimeUnit.toDurationUnit()),
        defaultTimeout = if (defaultTimeOut <= 0L) Duration.ZERO
            else defaultTimeOut.seconds,
        responseParser = responseParser,
    )

    fun addInterceptor(interceptor: Interceptor) = interceptors.add(interceptor)
    fun removeInterceptor(interceptor: Interceptor) = interceptors.remove(interceptor)

    private val noRedirectClient: HttpClient by lazy {
        baseHttpClient.config { followRedirects = false }
    }

    private val insecureClient: HttpClient by lazy {
        insecureHttpClient()
    }

    private val insecureNoRedirectClient: HttpClient by lazy {
        insecureHttpClient().config { followRedirects = false }
    }

    /**
     * Assembles the full interceptor chain for a single call.
     *
     * Always prepends a [LoggingInterceptor], optionally prepends a cache-control
     * [HeadersInterceptor] when [cacheTime] > 0, and appends the optional per-call
     * [interceptor] at the end.
     *
     * @param cacheTime    How long responses should be cached; [Duration.ZERO] skips the interceptor.
     * @param interceptor  Optional per-call interceptor appended after [interceptors].
     * @return A new [MutableList] ready to be installed via [HttpClient.withInterceptors].
     */
    private fun buildInterceptorChain(
        cacheTime: Duration,
        interceptor: Interceptor?,
    ): MutableList<Interceptor> {
        val chain = interceptors.toMutableList()
        if (cacheTime > Duration.ZERO) {
            chain.add(HeadersInterceptor {
                header(
                    HttpHeaders.CacheControl,
                    CacheControl.MaxAge(cacheTime.inWholeSeconds.toInt())
                )
            })
        }
        if (interceptor != null) chain.add(interceptor)
        chain.add(0, LoggingInterceptor())
        return chain
    }

    /**
     * Selects the correct [HttpClient] variant based on SSL-verification and redirect flags.
     *
     * @param verify         Whether to verify SSL certificates.
     * @param allowRedirects Whether to follow HTTP redirects automatically.
     * @return The appropriate [HttpClient] instance (lazy-initialised on first use).
     */
    private fun selectClient(verify: Boolean, allowRedirects: Boolean): HttpClient =
        when {
            !verify && !allowRedirects -> insecureNoRedirectClient
            !verify -> insecureClient
            !allowRedirects -> noRedirectClient
            else -> baseHttpClient
        }

    /**
     * Configures a Ktor [HttpRequestBuilder] with all resolved call parameters.
     *
     * This is the single place where URL, headers, body, and timeout are written
     * onto the builder, keeping non-streaming and streaming calls in sync without
     * duplicating logic (see [executeRequest]).
     *
     * @param method       HTTP method for this request.
     * @param finalUrl     Already-resolved URL with query params appended.
     * @param finalHeaders Already-merged [Headers] (default + per-call + cookies).
     * @param body         Resolved [RequestBody], or null for body-less methods.
     * @param timeout      Request timeout; [Duration.ZERO] means no timeout.
     */
    private fun HttpRequestBuilder.configureRequest(
        method: HttpMethod,
        finalUrl: String,
        finalHeaders: Headers,
        body: RequestBody?,
        timeout: Duration,
    ) {
        this.method = method
        url(finalUrl)
        // Write every header value individually; Ktor headers are multi-valued
        finalHeaders.forEach { k, values -> values.forEach { v -> header(k, v) } }
        body?.let { setBody(it.content) }
        if (timeout > Duration.ZERO) {
            val ms = timeout.inWholeMilliseconds
            timeout {
                requestTimeoutMillis = ms
                connectTimeoutMillis = ms
                socketTimeoutMillis = ms
            }
        }
    }

    /**
     * Shared implementation behind both [request] and [stream].
     *
     * Resolves the URL/headers/body, builds the interceptor chain, picks the right
     * client variant, and then either:
     *  - executes the request immediately via [HttpClient.request] ([stream] = false), or
     *  - keeps the connection open via [HttpClient.prepareRequest] + [HttpStatement.execute]
     *    for the duration of [block] ([stream] = true).
     *
     * In both cases the resulting [HttpResponse] is wrapped in a [NiceResponse] and handed
     * to [block]; the non-streaming [request] wrapper simply passes through an identity
     * block so it can still return a plain [NiceResponse].
     *
     * Caching is skipped whenever [stream] is true - it makes no sense for a live byte stream.
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
     * @param stream         If true, keeps the connection open for [block] instead of
     *                       buffering the whole body up-front, and disables caching.
     * @param cacheTime      How long to cache the response. Ignored when [stream] is true.
     * @param timeout        Request timeout. [Duration.ZERO] means no timeout.
     * @param interceptor    Per-call [Interceptor], appended after [interceptors].
     * @param verify         If false, SSL certificate verification is disabled.
     *                       Only has effect on platforms that support it (JVM/Android, Darwin, Curl, WinHttp).
     *                       Silently ignored on JS/WASM.
     * @param responseParser Overrides [this.responseParser] for this call.
     * @param block          Suspend lambda that receives the [NiceResponse] and returns [T].
     * @return Whatever [block] returns.
     */
    private suspend fun <T> executeRequest(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        referer: String?,
        params: Map<String, String>,
        cookies: Map<String, String>,
        data: Map<String, String>?,
        files: List<NiceFile>?,
        json: Any?,
        requestBody: RequestBody?,
        allowRedirects: Boolean,
        stream: Boolean,
        cacheTime: Duration,
        timeout: Duration,
        interceptor: Interceptor?,
        verify: Boolean,
        responseParser: ResponseParser?,
        block: suspend (NiceResponse) -> T,
    ): T {
        val finalUrl = addParamsToUrl(url, params)
        val finalHeaders = buildHeaders(
            defaultHeaders + headers,
            referer ?: defaultReferer,
            defaultCookies + cookies,
        )

        val body = buildBody(method, data, files, json, requestBody, responseParser)

        // Streaming requests skip the cache interceptor - caching a live stream is meaningless
        val allInterceptors = buildInterceptorChain(if (stream) Duration.ZERO else cacheTime, interceptor)
        val client = selectClient(verify, allowRedirects).withInterceptors(allInterceptors)

        return if (stream) {
            // prepareRequest + execute keeps the connection open for the duration of the lambda;
            // Ktor releases it automatically once block() returns
            client.prepareRequest {
                configureRequest(method, finalUrl, finalHeaders, body, timeout)
            }.execute { httpResponse ->
                block(NiceResponse(httpResponse, responseParser))
            }
        } else {
            val response = client.request {
                configureRequest(method, finalUrl, finalHeaders, body, timeout)
            }
            block(NiceResponse(response, responseParser))
        }
    }

    /**
     * Make requests and return NiceResponse. All method shortcuts delegate here.
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
    private suspend fun request(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        referer: String?,
        params: Map<String, String>,
        cookies: Map<String, String>,
        data: Map<String, String>?,
        files: List<NiceFile>?,
        json: Any?,
        requestBody: RequestBody?,
        allowRedirects: Boolean,
        cacheTime: Duration = defaultCacheTime,
        timeout: Duration = defaultTimeout,
        interceptor: Interceptor?,
        verify: Boolean,
        responseParser: ResponseParser?,
    ): NiceResponse = executeRequest(
        method, url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, stream = false, cacheTime = cacheTime, timeout = timeout,
        interceptor = interceptor, verify = verify, responseParser = responseParser,
    ) { it }

    @Deprecated(
        "Use one of the named builder methods instead: get(url) { }, post(url) { }, put(url) { }, etc.",
        level = DeprecationLevel.WARNING,
    )
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
        requestBody: NiceRequestBodyCompat? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: NiceTimeUnit = NiceTimeUnit.MINUTES,
        timeout: Long = 0L,
        interceptor: NiceInterceptorCompat? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = request(
        HttpMethod(method.uppercase()), url, headers, referer, params, cookies,
        data, files, json, requestBody?.toRequestBody(), allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
    )

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeout: Duration = Duration.ZERO,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this, block)
        return request(
            HttpMethod.Get, url, builder.headers + headers, referer ?: builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.cacheTime,
            if (timeout != Duration.ZERO) timeout else builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeout: Duration = Duration.ZERO,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this, block)
        return request(
            HttpMethod.Post, url, builder.headers + headers, referer ?: builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects,
            builder.cacheTime, if (timeout != Duration.ZERO) timeout else builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun put(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeout: Duration = Duration.ZERO,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this, block)
        return request(
            HttpMethod.Put, url, builder.headers + headers, referer ?: builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects,
            builder.cacheTime, if (timeout != Duration.ZERO) timeout else builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeout: Duration = Duration.ZERO,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this, block)
        return request(
            HttpMethod.Delete, url, builder.headers + headers, referer ?: builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects,
            builder.cacheTime, if (timeout != Duration.ZERO) timeout else builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeout: Duration = Duration.ZERO,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this, block)
        return request(
            HttpMethod.Head, url, builder.headers + headers, referer ?: builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.cacheTime,
            if (timeout != Duration.ZERO) timeout else builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun patch(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeout: Duration = Duration.ZERO,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this, block)
        return request(
            HttpMethod.Patch, url, builder.headers + headers, referer ?: builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects,
            builder.cacheTime, if (timeout != Duration.ZERO) timeout else builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun options(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeout: Duration = Duration.ZERO,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this, block)
        return request(
            HttpMethod.Options, url, builder.headers + headers, referer ?: builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.cacheTime,
            if (timeout != Duration.ZERO) timeout else builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    /**
     * GET request that streams the response instead of buffering it up-front.
     *
     * Pass `stream = true` to keep the connection open for the duration of [streamBlock],
     * ideal for video/audio streaming, large file downloads, and Server-Sent Events.
     * Use [NiceResponse.response] to access the [ByteReadChannel] for incremental reads.
     * Ktor closes the connection automatically when [streamBlock] returns. Caching is
     * skipped whenever [stream] is true, since caching a live byte stream is meaningless.
     *
     * Example reading a video stream in chunks:
     * ```kotlin
     * app.get("https://cdn.example.com/video.mp4", stream = true, {
     *     header(HttpHeaders.Range, "bytes=0-")
     * }) { response ->
     *     val channel = response.channel
     *     while (!channel.isClosedForRead) { /* read chunks */ }
     * }
     * ```
     *
     * @param url         Target URL.
     * @param stream      If true, keeps the connection open for [streamBlock] instead of
     *                     buffering the whole body up-front, and disables caching.
     * @param headers     Extra headers merged on top of [defaultHeaders] and any set via [block].
     * @param referer     Overrides [defaultReferer] (and any referer set via [block]) for this call.
     * @param timeout     Request timeout. [Duration.ZERO] means fall back to whatever [block] set.
     * @param block       Optional [RequestBuilder] configuration lambda (headers, params, etc.).
     * @param streamBlock Suspend lambda that receives the [NiceResponse] and returns [T].
     * @return Whatever [streamBlock] returns.
     */
    suspend fun <T> get(
        url: String,
        stream: Boolean,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeout: Duration = Duration.ZERO,
        block: RequestBuilder.() -> Unit = {},
        streamBlock: suspend (NiceResponse) -> T,
    ): T {
        val builder = RequestBuilder(this, block)
        return executeRequest(
            HttpMethod.Get, url, builder.headers + headers, referer ?: builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, stream,
            cacheTime = builder.cacheTime,
            timeout = if (timeout != Duration.ZERO) timeout else builder.timeout,
            interceptor = builder.interceptor, verify = builder.verify,
            responseParser = builder.responseParser, block = streamBlock,
        )
    }

    /**
     * POST request that streams the response instead of buffering it up-front.
     *
     * Pass `stream = true` to keep the connection open for the duration of [streamBlock].
     * Useful for Server-Sent Events or chunked JSON responses where the server sends
     * data progressively rather than all at once. Caching is skipped whenever [stream]
     * is true, since caching a live byte stream is meaningless.
     *
     * @param url         Target URL.
     * @param stream      If true, keeps the connection open for [streamBlock] instead of
     *                     buffering the whole body up-front, and disables caching.
     * @param headers     Extra headers merged on top of [defaultHeaders] and any set via [block].
     * @param referer     Overrides [defaultReferer] (and any referer set via [block]) for this call.
     * @param timeout     Request timeout. [Duration.ZERO] means fall back to whatever [block] set.
     * @param block       Optional [RequestBuilder] configuration lambda (body, headers, etc.).
     * @param streamBlock Suspend lambda that receives the [NiceResponse] and returns [T].
     * @return Whatever [streamBlock] returns.
     */
    suspend fun <T> post(
        url: String,
        stream: Boolean,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeout: Duration = Duration.ZERO,
        block: RequestBuilder.() -> Unit = {},
        streamBlock: suspend (NiceResponse) -> T,
    ): T {
        val builder = RequestBuilder(this, block)
        return executeRequest(
            HttpMethod.Post, url, builder.headers + headers, referer ?: builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects, stream,
            cacheTime = builder.cacheTime,
            timeout = if (timeout != Duration.ZERO) timeout else builder.timeout,
            interceptor = builder.interceptor, verify = builder.verify,
            responseParser = builder.responseParser, block = streamBlock,
        )
    }

    @Deprecated(
        "Use get(url) { } with a RequestBuilder block instead. " +
            "Replace cacheTime/cacheUnit with cacheTime = n.minutes (or other Duration), " +
            "timeout with timeout = n.seconds, and OkHttp Interceptor with Interceptor " +
            "from NiceHttp.",
        level = DeprecationLevel.WARNING,
    )
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
    ) = request(
        HttpMethod.Get, url, headers, referer, params, cookies,
        null, null, null, null, allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
    )

    @Deprecated(
        "Use post(url) { } with a RequestBuilder block instead. " +
            "Replace cacheTime/cacheUnit with cacheTime = n.minutes (or other Duration), " +
            "timeout with timeout = n.seconds, OkHttp Interceptor with Interceptor " +
            "from NiceHttp, and OkHttp RequestBody with RequestBody from NiceHttp.",
        level = DeprecationLevel.WARNING,
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
    ) = request(
        HttpMethod.Post, url, headers, referer, params, cookies,
        data, files, json, requestBody?.toRequestBody(), allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
    )

    @Deprecated(
        "Use put(url) { } with a RequestBuilder block instead. " +
            "Replace cacheTime/cacheUnit with cacheTime = n.minutes (or other Duration), " +
            "timeout with timeout = n.seconds, OkHttp Interceptor with Interceptor " +
            "from NiceHttp, and OkHttp RequestBody with RequestBody from NiceHttp.",
        level = DeprecationLevel.WARNING,
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
    ) = request(
        HttpMethod.Put, url, headers, referer, params, cookies,
        data, files, json, requestBody?.toRequestBody(), allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
    )

    @Deprecated(
        "Use delete(url) { } with a RequestBuilder block instead. " +
            "Replace cacheTime/cacheUnit with cacheTime = n.minutes (or other Duration), " +
            "timeout with timeout = n.seconds, OkHttp Interceptor with Interceptor " +
            "from NiceHttp, and OkHttp RequestBody with RequestBody from NiceHttp.",
        level = DeprecationLevel.WARNING,
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
    ) = request(
        HttpMethod.Delete, url, headers, referer, params, cookies,
        data, files, json, requestBody?.toRequestBody(), allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
    )

    @Deprecated(
        "Use head(url) { } with a RequestBuilder block instead. " +
            "Replace cacheTime/cacheUnit with cacheTime = n.minutes (or other Duration), " +
            "timeout with timeout = n.seconds, and OkHttp Interceptor with Interceptor " +
            "from NiceHttp.",
        level = DeprecationLevel.WARNING,
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
    ) = request(
        HttpMethod.Head, url, headers, referer, params, cookies,
        null, null, null, null, allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
    )

    @Deprecated(
        "Use patch(url) { } with a RequestBuilder block instead. " +
            "Replace cacheTime/cacheUnit with cacheTime = n.minutes (or other Duration), " +
            "timeout with timeout = n.seconds, OkHttp Interceptor with Interceptor " +
            "from NiceHttp, and OkHttp RequestBody with RequestBody from NiceHttp.",
        level = DeprecationLevel.WARNING,
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
    ) = request(
        HttpMethod.Patch, url, headers, referer, params, cookies,
        data, files, json, requestBody?.toRequestBody(), allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
    )

    @Deprecated(
        "Use options(url) { } with a RequestBuilder block instead. " +
            "Replace cacheTime/cacheUnit with cacheTime = n.minutes (or other Duration), " +
            "timeout with timeout = n.seconds, OkHttp Interceptor with Interceptor " +
            "from NiceHttp, and OkHttp RequestBody with RequestBody from NiceHttp.",
        level = DeprecationLevel.WARNING,
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
    ) = request(
        HttpMethod.Options, url, headers, referer, params, cookies,
        null, null, null, null, allowRedirects,
        cacheTime.toLong().toDuration(cacheUnit.toDurationUnit()),
        timeout.seconds,
        interceptor?.toInterceptor(), verify, responseParser,
    )
}

private val MUST_HAVE_BODY  = setOf(HttpMethod.Post, HttpMethod.Put)

// Keep in sync with https://github.com/ktorio/ktor/blob/245774a/ktor-http/common/src/io/ktor/http/HttpMethod.kt#L108-L113
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
 * 5. Empty form body for methods in [MUST_HAVE_BODY] when nothing else is provided
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
            val ct = if (json is String) ContentType.Text.Plain else ContentType.Application.Json
            RequestBody.text(jsonString, ct.withCharset(Charsets.UTF_8))
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
                                            ContentDisposition("form-data")
                                                .withParameter(ContentDisposition.Parameters.Name, file.name)
                                                .withParameter(ContentDisposition.Parameters.FileName, file.fileName)
                                                .toString()
                                        )
                                        file.fileType?.let { append(HttpHeaders.ContentType, ContentType.parse(it)) }
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
