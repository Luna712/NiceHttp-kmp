package com.lagradost.nicehttp.kmp

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.statement.*
import io.ktor.http.*

/** Maximum byte count read by [NiceResponse.text] before an [IllegalStateException] is thrown. */
const val MAX_TEXT_BYTES: Long = 5_000_000L // 5 MB

/**
 * Common interface for all NiceHttp responses.
 * Implemented by [NiceResponse] (Ktor-backed) and [SyntheticNiceResponse] (bytes-backed).
 */
interface INiceResponse {
    /** HTTP status code, e.g. 200 */
    val code: Int

    /** Raw Ktor [Headers] */
    val headers: Headers

    /** Final URL after any redirects */
    val url: String

    /** `true` if [code] is in 200–299 */
    val isSuccessful: Boolean

    /** Content-Length as reported by the server, or null if absent */
    val size: Long?

    /** All cookies sent by the server for this response */
    val cookies: Map<String, String>

    /** JSON parser used by [parsed] */
    val parser: ResponseParser?

    /** Reads the body as a string. Cached after first call. */
    val text: String

    /** Same as [text] but without the size guard. Cached after first call. */
    val textLarge: String

    /** As parsed by Ksoup.parse(text) */
    val document: Document

    /** Same as [document] but without the size guard */
    val documentLarge: Document

    /** Raw response body bytes. Cached after first call. */
    val bodyBytes: ByteArray

    /** Response body. Call .bytes() or .string() to read. Call .close() when done (no-op). */
    val body: ResponseBody

    /** Alias for [response] for source compatibility with original NiceHttp */
    val okhttpResponse: HttpResponse

    /**
     * Reads the response body as a string. Throws [IllegalStateException] if the body exceeds
     * [MAX_TEXT_BYTES] (use [textLarge] to bypass that guard).
     */
    suspend fun text(): String

    /** Reads the response body without the size guard. */
    suspend fun textLarge(): String

    /** Parses the body as an HTML [Document] using ksoup. */
    suspend fun document(): Document

    /** Same as [document] but without the size guard. */
    suspend fun documentLarge(): Document
}

/**
 * Wraps a Ktor [HttpResponse] with a friendlier API that mirrors the original NiceHttp library.
 */
class NiceResponse(
    val response: HttpResponse,
    override val parser: ResponseParser? = null,
) : INiceResponse {
    /** HTTP status code, e.g. 200 */
    override val code: Int get() = response.status.value

    /** Raw Ktor [Headers] */
    override val headers: Headers get() = response.headers

    /** Final URL after any redirects */
    override val url: String get() = response.request.url.toString()

    /** `true` if [code] is in 200–299 */
    override val isSuccessful: Boolean get() = response.status.isSuccess()

    /** Content-Length as reported by the server, or null if absent */
    override val size: Long? get() = response.contentLength()

    /** All cookies sent by the server for this response */
    override val cookies: Map<String, String> get() = response.headers.getSetCookies()

    // ── Compatibility layers for old NiceHttp ─────────────────────────────────────

    /** Reads the body as a string. Cached after first call. */
    override val text: String by lazy {
        runBlockingCompat {
            val raw = response.bodyAsText()
            if (raw.length.toLong() > MAX_TEXT_BYTES) {
                throw IllegalStateException(
                    "Called .text on a response exceeding $MAX_TEXT_BYTES bytes. Use .textLarge instead."
                )
            }
            raw
        }
    }

    /** Same as [text] but without the size guard. Cached after first call. */
    override val textLarge: String by lazy { runBlockingCompat { response.bodyAsText() } }

    override val document: Document by lazy { Ksoup.parse(text) }
    override val documentLarge: Document by lazy { Ksoup.parse(textLarge) }

    /** Raw response body bytes. Cached after first call. */
    override val bodyBytes: ByteArray by lazy { runBlockingCompat { response.readRawBytes() } }

    /** Response body. Call .bytes() or .string() to read. Call .close() when done (no-op here). */
    override val body: ResponseBody by lazy {
        ResponseBody(runBlockingCompat { response.readRawBytes() })
    }

    /** Alias for [response] for source compatibility with original NiceHttp */
    override val okhttpResponse: HttpResponse get() = response

    // ── Body helpers ─────────────────────────────────────────────────────────

    /**
     * Reads the response body as a string. Throws [IllegalStateException] if the body exceeds
     * [MAX_TEXT_BYTES] (use [textLarge] to bypass that guard).
     */
    override suspend fun text(): String {
        val len = size
        if (len != null && len > MAX_TEXT_BYTES) {
            throw IllegalStateException(
                "Called .text() on a response with Content-Length $len > $MAX_TEXT_BYTES bytes. " +
                    "Use .textLarge() if you intentionally want to read a large body."
            )
        }
        val raw = response.bodyAsText()
        if (raw.length.toLong() > MAX_TEXT_BYTES) {
            throw IllegalStateException(
                "Called .text() and the body exceeded $MAX_TEXT_BYTES bytes. Use .textLarge()."
            )
        }
        return raw
    }

    /** Reads the response body without the size guard. */
    override suspend fun textLarge(): String = response.bodyAsText()

    /** Parses the body as an HTML [Document] using ksoup. */
    override suspend fun document(): Document = Ksoup.parse(text())

    /** Same as [document] but without the size guard. */
    override suspend fun documentLarge(): Document = Ksoup.parse(textLarge())

    override fun toString(): String =
        "NiceResponse(code=$code, url=$url)"
}

/**
 * Synthetic [INiceResponse] backed by raw bytes rather than a live Ktor [HttpResponse].
 * Used internally by interceptor bridges. Not part of the public API.
 */
internal class SyntheticNiceResponse(
    private val rawBytes: ByteArray,
    override val code: Int,
    override val headers: Headers,
    override val url: String,
    override val parser: ResponseParser? = null,
) : INiceResponse {
    override val isSuccessful: Boolean get() = code in 200..299
    override val size: Long get() = rawBytes.size.toLong()
    override val cookies: Map<String, String> get() = headers.getSetCookies()

    override val text: String by lazy {
        val raw = rawBytes.decodeToString()
        if (raw.length.toLong() > MAX_TEXT_BYTES) throw IllegalStateException(
            "Called .text on a response exceeding $MAX_TEXT_BYTES bytes. Use .textLarge instead."
        )
        raw
    }

    override val textLarge: String by lazy { rawBytes.decodeToString() }
    override val document: Document by lazy { Ksoup.parse(text) }
    override val documentLarge: Document by lazy { Ksoup.parse(textLarge) }
    override val bodyBytes: ByteArray by lazy { rawBytes }
    override val body: ResponseBody by lazy { ResponseBody(rawBytes) }

    override val okhttpResponse: HttpResponse
        get() = throw UnsupportedOperationException(
            "okhttpResponse is not available on a SyntheticNiceResponse"
        )

    override suspend fun text(): String = text
    override suspend fun textLarge(): String = textLarge
    override suspend fun document(): Document = document()
    override suspend fun documentLarge(): Document = documentLarge()

    override fun toString(): String =
        "SyntheticNiceResponse(code=$code, url=$url)"
}

// ── Cookie helpers ────────────────────────────────────────────────────────────

/** Extracts `Set-Cookie` headers into a simple name→value map. */
fun Headers.getSetCookies(): Map<String, String> =
    getAll("set-cookie")
        ?.mapNotNull { header ->
            val pair = header.substringBefore(";").split("=", limit = 2)
            val k = pair.getOrNull(0)?.trim() ?: return@mapNotNull null
            val v = pair.getOrNull(1)?.trim() ?: return@mapNotNull null
            if (k.isBlank() || v.isBlank()) null else k to v
        }
        ?.toMap()
        ?: emptyMap()

/** Extracts `Cookie` request headers into a simple name→value map. */
fun Headers.getRequestCookies(): Map<String, String> =
    get("Cookie")
        ?.split(";")
        ?.mapNotNull { token ->
            val pair = token.split("=", limit = 2)
            val k = pair.getOrNull(0)?.trim() ?: return@mapNotNull null
            val v = pair.getOrNull(1)?.trim() ?: return@mapNotNull null
            if (k.isBlank() || v.isBlank()) null else k to v
        }
        ?.toMap()
        ?: emptyMap()

/** Converts Ktor [Headers] to a plain Map, keeping the last value for duplicate keys. */
fun Headers.toMap(): Map<String, String> =
    entries().associate { (key, values) -> key to values.last() }

/** Converts Ktor [Headers] to a Map with all values per key. */
fun Headers.toMultiMap(): Map<String, List<String>> =
    entries().associate { (key, values) -> key to values }

// ── Parsed helpers as extensions (inline reified can't live in interfaces) ────

/** Parses the body as [T] using the configured [ResponseParser]. */
suspend inline fun <reified T : Any> INiceResponse.parsed(): T =
    parser!!.parse(text(), T::class)

/** Same as [parsed] but returns null on failure. */
suspend inline fun <reified T : Any> INiceResponse.parsedSafe(): T? = try {
    parser?.parseSafe(text(), T::class)
} catch (e: Exception) {
    e.printStackTrace()
    null
}

/** Like [parsed] but without the size guard. */
suspend inline fun <reified T : Any> INiceResponse.parsedLarge(): T =
    parser!!.parse(textLarge(), T::class)

/** Like [parsedSafe] but without the size guard. */
suspend inline fun <reified T : Any> INiceResponse.parsedSafeLarge(): T? = try {
    parser?.parseSafe(textLarge(), T::class)
} catch (e: Exception) {
    e.printStackTrace()
    null
}
