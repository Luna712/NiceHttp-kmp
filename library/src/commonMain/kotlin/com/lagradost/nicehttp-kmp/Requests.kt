package com.lagradost.nicehttp

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
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
        baseClient: HttpClient = defaultHttpClient(),
        defaultHeaders: Map<String, String> = mapOf(HttpHeaders.UserAgent to "NiceHttp"),
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
        // Logging always goes first so it sees the raw outgoing request
        chain.add(0, LoggingInterceptor())
        if (cacheTime > Duration.ZERO) {
            // Cache-control header is injected before the logging interceptor sees the request
            chain.add(0, HeadersInterceptor {
                remove(HttpHeaders.Pragma)
                header(
                    HttpHeaders.CacheControl,
                    CacheControl.MaxAge(cacheTime.inWholeSeconds.toInt())
                )
            })
        }
        if (interceptor != null) chain.add(interceptor)
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
            !verify                    -> insecureClient
            !allowRedirects            -> noRedirectClient
            else                       -> baseClient
        }

    /**
     * Configures a Ktor [HttpRequestBuilder] with all resolved call parameters.
     *
     * This is the single place where URL, headers, body, and timeout are written
     * onto the builder, keeping [request] and [stream] in sync without duplicating logic.
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
                socketTimeoutMillis  = ms
            }
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
    ): NiceResponse {
        val finalUrl = addParamsToUrl(url, params)
        val finalHeaders = buildHeaders(
            defaultHeaders + headers,
            referer ?: defaultReferer,
            defaultCookies + cookies,
        )
        val body = buildBody(method, data, files, json, requestBody, responseParser)

        // Build all interceptors for this call
        val allInterceptors = buildInterceptorChain(cacheTime, interceptor)

        // Pick the right client variant, then install interceptors
        val client = selectClient(verify, allowRedirects).withInterceptors(allInterceptors)

        val response = client.request {
            configureRequest(method, finalUrl, finalHeaders, body, timeout)
        }

        return NiceResponse(response, responseParser)
    }

    /**
     * Streaming variant of [request] - uses Ktor's [HttpStatement.execute] so the
     * connection stays open while [block] runs, letting the caller read the body
     * channel incrementally without buffering the entire payload into memory first.
     *
     * The [HttpResponse] is wrapped in an ordinary [NiceResponse] and passed to [block].
     * The response body channel remains live for the duration of [block]; Ktor closes
     * the connection automatically when [block] returns.
     *
     * Ideal for video/audio streaming, large file downloads, and Server-Sent Events.
     * Caching is intentionally skipped - it makes no sense for a live byte stream.
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
     * @param timeout        Connection/socket timeout. Defaults to [Duration.ZERO] (no timeout)
     *                       because a server may drip bytes indefinitely on a live stream.
     * @param interceptor    Per-call [Interceptor], appended after [interceptors].
     * @param verify         If false, SSL certificate verification is disabled.
     * @param responseParser Overrides [this.responseParser] for this call.
     * @param block          Suspend lambda that receives the live [NiceResponse] and returns [T].
     * @return Whatever [block] returns.
     */
    private suspend fun <T> stream(
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
        // Streams intentionally default to no timeout as a server may drip bytes indefinitely
        timeout: Duration = Duration.ZERO,
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

        // Streaming requests skip the cache interceptor as caching a live stream is meaningless
        val allInterceptors = buildInterceptorChain(cacheTime = Duration.ZERO, interceptor)

        val client = selectClient(verify, allowRedirects).withInterceptors(allInterceptors)

        // prepareRequest + execute keeps the connection open for the duration of the lambda;
        // Ktor releases it automatically once block() returns
        return client.prepareRequest {
            configureRequest(method, finalUrl, finalHeaders, body, timeout)
        }.execute { httpResponse ->
            block(NiceResponse(httpResponse, responseParser))
        }
    }

    /**
     * Builds and returns a prepared [HttpStatement] without executing it.
     * Skips caching (meaningless for raw statements) but applies the full interceptor chain,
     * client selection, and header/cookie merging identically to [request].
     */
    private suspend fun prepareStatement(
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
        timeout: Duration = Duration.ZERO,
        interceptor: Interceptor?,
        verify: Boolean,
        responseParser: ResponseParser?,
    ): HttpStatement {
        val finalUrl = addParamsToUrl(url, params)
        val finalHeaders = buildHeaders(
            defaultHeaders + headers,
            referer ?: defaultReferer,
            defaultCookies + cookies,
        )
        val body = buildBody(method, data, files, json, requestBody, responseParser)
        val allInterceptors = buildInterceptorChain(cacheTime = Duration.ZERO, interceptor)
        val client = selectClient(verify, allowRedirects).withInterceptors(allInterceptors)
        return client.prepareRequest {
            configureRequest(method, finalUrl, finalHeaders, body, timeout)
        }
    }

    @Deprecated(
        "Use one of the named builder methods instead: get(url) { }, post(url) { }, put(url) { }, etc.",
        level = DeprecationLevel.WARNING,
    )
    open suspend fun custom(
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
    ): NiceResponse = request(
        method, url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, timeout, interceptor, verify, responseParser,
    )

    suspend fun get(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this, block)
        return request(
            HttpMethod.Get, url, builder.headers, builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.cacheTime, builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun post(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this, block)
        return request(
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
        val builder = RequestBuilder(this, block)
        return request(
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
        val builder = RequestBuilder(this, block)
        return request(
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
        val builder = RequestBuilder(this, block)
        return request(
            HttpMethod.Head, url, builder.headers, builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.cacheTime, builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    suspend fun patch(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): NiceResponse {
        val builder = RequestBuilder(this, block)
        return request(
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
        val builder = RequestBuilder(this, block)
        return request(
            HttpMethod.Options, url, builder.headers, builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.cacheTime, builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    /**
     * Opens a streaming GET request, ideal for video/audio streaming or large file
     * downloads where buffering the full body into memory is undesirable.
     *
     * The connection stays open for the duration of [streamBlock]; use [NiceResponse.response]
     * to access [ByteReadChannel] for incremental reads. Ktor closes the connection
     * automatically when [streamBlock] returns.
     *
     * Example reading a video stream in chunks:
     * ```kotlin
     * app.streamGet("https://cdn.example.com/video.mp4", {
     *     header(HttpHeaders.Range, "bytes=0-")
     * }) { response ->
     *     val channel = response.channel
     *     while (!channel.isClosedForRead) { /* read chunks */ }
     * }
     * ```
     *
     * @param url         Target URL.
     * @param block       Optional [RequestBuilder] configuration lambda (headers, params, etc.).
     * @param streamBlock Suspend lambda that receives the live [NiceResponse] and returns [T].
     * @return Whatever [streamBlock] returns.
     */
    suspend fun <T> streamGet(
        url: String,
        block: RequestBuilder.() -> Unit = {},
        streamBlock: suspend (NiceResponse) -> T,
    ): T {
        val builder = RequestBuilder(this, block)
        return stream(
            HttpMethod.Get, url, builder.headers, builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser, streamBlock,
        )
    }

    /**
     * Opens a streaming POST request. Useful for Server-Sent Events or chunked JSON
     * responses where the server sends data progressively rather than all at once.
     *
     * @param url         Target URL.
     * @param block       Optional [RequestBuilder] configuration lambda (body, headers, etc.).
     * @param streamBlock Suspend lambda that receives the live [NiceResponse] and returns [T].
     * @return Whatever [streamBlock] returns.
     */
    suspend fun <T> streamPost(
        url: String,
        block: RequestBuilder.() -> Unit = {},
        streamBlock: suspend (NiceResponse) -> T,
    ): T {
        val builder = RequestBuilder(this, block)
        return stream(
            HttpMethod.Post, url, builder.headers, builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects,
            builder.timeout, builder.interceptor, builder.verify, builder.responseParser, streamBlock,
        )
    }

    /**
     * Returns a prepared [HttpStatement] for a GET request without executing it.
     * Useful for Media3's KtorDataSource which manages its own execution and byte-range handling.
     */
    suspend fun prepareGet(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): HttpStatement {
        val builder = RequestBuilder(this, block)
        return prepareStatement(
            HttpMethod.Get, url, builder.headers, builder.referer, builder.params, builder.cookies,
            null, null, null, null, builder.allowRedirects, builder.timeout,
            builder.interceptor, builder.verify, builder.responseParser,
        )
    }

    /**
     * Returns a prepared [HttpStatement] for a POST request without executing it.
     * Useful for Media3's KtorDataSource which manages its own execution and byte-range handling.
     */
    suspend fun preparePost(
        url: String,
        block: RequestBuilder.() -> Unit = {},
    ): HttpStatement {
        val builder = RequestBuilder(this, block)
        return prepareStatement(
            HttpMethod.Post, url, builder.headers, builder.referer, builder.params, builder.cookies,
            builder.data, builder.files, builder.json, builder.requestBody, builder.allowRedirects,
            builder.timeout, builder.interceptor, builder.verify, builder.responseParser,
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
