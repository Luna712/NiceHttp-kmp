package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Multiplatform HTTP client modelled after the original NiceHttp `Requests` class.
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
 * @param defaultCookies    Cookies merged into every request.
 * @param defaultTimeout    Global call timeout; [Duration.ZERO] means no timeout.
 * @param responseParser    JSON parser used by [NiceResponse.parsed].
 */
open class Requests(
    var baseClient: HttpClient = defaultHttpClient(),
    var defaultHeaders: Map<String, String> = mapOf("user-agent" to "NiceHttp"),
    var defaultReferer: String? = null,
    var defaultData: Map<String, String> = emptyMap(),
    var defaultCookies: Map<String, String> = emptyMap(),
    var defaultTimeout: Duration = Duration.ZERO,
    var responseParser: ResponseParser? = null,
) {
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
     * @param requestBody    Fully pre-built [OutgoingContent] (highest priority body).
     * @param allowRedirects Whether to follow HTTP redirects.
     * @param timeout        Overrides [defaultTimeout] for this call.
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
        requestBody: OutgoingContent? = null,
        allowRedirects: Boolean = true,
        timeout: Duration = defaultTimeout,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse {
        val finalUrl = addParamsToUrl(url, params)
        val finalHeaders = buildHeaders(
            defaultHeaders + headers,
            referer ?: defaultReferer,
            defaultCookies + cookies,
        )
        val body = buildBody(method, data, files, json, requestBody, responseParser)

        val clientToUse = if (!allowRedirects) {
            baseClient.config { followRedirects = false }
        } else baseClient

        val response = clientToUse.request(finalUrl) {
            this.method = HttpMethod(method.uppercase())
            finalHeaders.forEach { k, values -> values.forEach { v -> header(k, v) } }
            body?.let { setBody(it) }

            if (timeout > Duration.ZERO) {
                timeout {
                    requestTimeoutMillis = timeout.inWholeMilliseconds
                    connectTimeoutMillis = timeout.inWholeMilliseconds
                    socketTimeoutMillis = timeout.inWholeMilliseconds
                }
            }
        }

        return NiceResponse(response, responseParser)
    }

    // ── Verb shortcuts ────────────────────────────────────────────────────────

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        timeout: Duration = defaultTimeout,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom("GET", url, headers, referer, params, cookies,
        null, null, null, null, allowRedirects, timeout, responseParser)

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: OutgoingContent? = null,
        allowRedirects: Boolean = true,
        timeout: Duration = defaultTimeout,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom("POST", url, headers, referer, params, cookies,
        data, files, json, requestBody, allowRedirects, timeout, responseParser)

    suspend fun put(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: OutgoingContent? = null,
        allowRedirects: Boolean = true,
        timeout: Duration = defaultTimeout,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom("PUT", url, headers, referer, params, cookies,
        data, files, json, requestBody, allowRedirects, timeout, responseParser)

    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: OutgoingContent? = null,
        allowRedirects: Boolean = true,
        timeout: Duration = defaultTimeout,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom("DELETE", url, headers, referer, params, cookies,
        data, files, json, requestBody, allowRedirects, timeout, responseParser)

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        timeout: Duration = defaultTimeout,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom("HEAD", url, headers, referer, params, cookies,
        null, null, null, null, allowRedirects, timeout, responseParser)

    suspend fun patch(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: OutgoingContent? = null,
        allowRedirects: Boolean = true,
        timeout: Duration = defaultTimeout,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom("PATCH", url, headers, referer, params, cookies,
        data, files, json, requestBody, allowRedirects, timeout, responseParser)

    suspend fun options(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: OutgoingContent? = null,
        allowRedirects: Boolean = true,
        timeout: Duration = defaultTimeout,
        responseParser: ResponseParser? = this.responseParser,
    ) = custom("OPTIONS", url, headers, referer, params, cookies,
        data, files, json, requestBody, allowRedirects, timeout, responseParser)
}

// ── Body builder ──────────────────────────────────────────────────────────────

private val NO_BODY_METHODS = setOf("GET", "HEAD")
private val MUST_HAVE_BODY  = setOf("POST", "PUT")

/**
 * Constructs the [OutgoingContent] for the request body, following the same priority rules
 * as the original NiceHttp:
 *
 * 1. [requestBody] (pre-built)
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
    requestBody: OutgoingContent?,
    responseParser: ResponseParser?,
): OutgoingContent? {
    val upper = method.uppercase()
    if (upper in NO_BODY_METHODS) return null
    if (requestBody != null) return requestBody

    return when {
        !data.isNullOrEmpty() -> {
            FormDataContent(Parameters.build {
                data.forEach { (k, v) -> append(k, v) }
            })
        }

        json != null -> {
            val jsonString = when {
                json is JsonAsString -> json.string
                json is String -> json
                responseParser != null -> responseParser.writeValueAsString(json)
                else -> json.toString()
            }
            val contentType = if (json is String) RequestBodyTypes.TEXT else RequestBodyTypes.JSON
            TextContent(jsonString, ContentType.parse(contentType))
        }

        !files.isNullOrEmpty() -> {
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
        }

        upper in MUST_HAVE_BODY -> FormDataContent(Parameters.Empty)

        else -> null
    }
}
