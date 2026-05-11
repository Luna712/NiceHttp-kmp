package com.lagradost.nicehttp

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.statement.*
import io.ktor.http.*

/** Maximum byte count read by [NiceResponse.text] before an [IllegalStateException] is thrown. */
const val MAX_TEXT_BYTES: Long = 5_000_000L // 5 MB

/**
 * Wraps a Ktor [HttpResponse] with a friendlier API that mirrors the original NiceHttp library.
 */
class NiceResponse(
    val response: HttpResponse,
    val parser: ResponseParser? = null,
) {
    /** HTTP status code, e.g. 200 */
    val code: Int get() = response.status.value

    /**
     * Response headers wrapped in [NiceHeaders], which delegates [Headers] and adds
     * [NiceHeaders.toMap] and [NiceHeaders.toMultiMap] for source compatibility
     * with the original NiceHttp library.
     */
    val headers: NiceHeaders get() = NiceHeaders(response.headers)

    /** Final URL after any redirects */
    val url: String get() = response.request.url.toString()

    /** `true` if [code] is in 200–299 */
    val isSuccessful: Boolean get() = response.status.isSuccess()

    /** Content-Length as reported by the server, or null if absent */
    val size: Long? get() = response.contentLength()

    /** All cookies sent by the server for this response */
    val cookies: Map<String, String> get() = response.headers.getSetCookies()

    // ── Compatibility layers for old NiceHttp ─────────────────────────────────────

    /** Reads the body as a string. Cached after first call. */
    val text: String by lazy {
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
    val textLarge: String by lazy { runBlockingCompat { response.bodyAsText() } }

    val document: Document by lazy { Ksoup.parse(text) }
    val documentLarge: Document by lazy { Ksoup.parse(textLarge) }

    /** Response body. Call .bytes() or .string() to read. Call .close() when done (no-op here). */
    val body: ResponseBody by lazy {
        ResponseBody(runBlockingCompat { response.readRawBytes() })
    }

    /** Alias for [NiceResponse] for source compatibility with original NiceHttp */
    val okhttpResponse: NiceResponse get() = this

    /** Returns the value of the header with the given [name], or null if absent. */
    fun header(name: String): String? = headers[name]

    // ── Body helpers ─────────────────────────────────────────────────────────

    /**
     * Reads the response body as a string. Throws [IllegalStateException] if the body exceeds
     * [MAX_TEXT_BYTES] (use [textLarge] to bypass that guard).
     */
    suspend fun text(): String {
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

    /** Response body. Call .bytes() or .string() to read. Call .close() when done (no-op here). */
    suspend fun body(): ResponseBody = ResponseBody(response.readRawBytes())

    /** Reads the response body without the size guard. */
    suspend fun textLarge(): String = response.bodyAsText()

    /** Parses the body as an HTML [Document] using ksoup. */
    suspend fun document(): Document = Ksoup.parse(text())

    /** Same as [document] but without the size guard. */
    suspend fun documentLarge(): Document = Ksoup.parse(textLarge())

    /** Parses the body as [T] using the configured [ResponseParser]. */
    suspend inline fun <reified T : Any> parsed(): T =
        parser!!.parse(text(), T::class)

    /** Same as [parsed] but returns null on failure. */
    suspend inline fun <reified T : Any> parsedSafe(): T? = try {
        parser?.parseSafe(text(), T::class)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /** Like [parsed] but without the size guard. */
    suspend inline fun <reified T : Any> parsedLarge(): T =
        parser!!.parse(textLarge(), T::class)

    /** Like [parsedSafe] but without the size guard. */
    suspend inline fun <reified T : Any> parsedSafeLarge(): T? = try {
        parser?.parseSafe(textLarge(), T::class)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    override fun toString(): String =
        "NiceResponse(code=$code, url=$url)"
}

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
