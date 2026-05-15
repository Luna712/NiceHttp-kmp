package com.lagradost.nicehttp

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

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

    /** `true` if [code] is in 200-299 */
    val isSuccessful: Boolean get() = response.status.isSuccess()

    /** Content-Length as reported by the server, or null if absent */
    val size: Long? get() = response.contentLength()

    /** All cookies sent by the server for this response */
    val cookies: Map<String, String> get() = response.headers.getSetCookies()

    /**
     * Compatibility layers for old NiceHttp (all are deprecated)
     */

    @Deprecated(
        "Use suspend fun text() instead.",
        ReplaceWith("text()"),
        DeprecationLevel.WARNING,
    )
    val text: String by lazy {
        runBlockingCompat {
            val len = size
            if (len != null && len > MAX_TEXT_BYTES) {
                throw IllegalStateException(
                    "Called .text on a response with Content-Length $len > $MAX_TEXT_BYTES bytes. Use .textLarge instead."
                )
            }

            channel().readTextLimited(
                response.charset() ?: Charsets.UTF_8
            )
        }
    }

    @Deprecated(
        "Use suspend fun textLarge() instead.",
        ReplaceWith("textLarge()"),
        DeprecationLevel.WARNING,
    )
    val textLarge: String by lazy { runBlockingCompat { response.bodyAsText() } }

    @Deprecated(
        "Returns a jsoup Document on Android/JVM (for back-compat) but ksoup on other platforms. " +
            "Use suspend fun document() which always returns a ksoup Document. " +
            "Migrate call sites to use the ksoup API.",
        ReplaceWith("document()"),
    )
    @Suppress("DEPRECATION")
    val document: NiceDocument by lazy { parseDocument(text) }

    @Deprecated(
        "Returns a jsoup Document on Android/JVM (for back-compat) but ksoup on other platforms. " +
            "Use suspend fun documentLarge() which always returns a ksoup Document. " +
            "Migrate call sites to use the ksoup API.",
        ReplaceWith("documentLarge()"),
        DeprecationLevel.WARNING,
    )
    @Suppress("DEPRECATION")
    val documentLarge: NiceDocument by lazy { parseDocument(textLarge) }

    @Deprecated(
        "Use suspend fun body() instead.",
        ReplaceWith("body()"),
        DeprecationLevel.WARNING,
    )
    val body: ResponseBody by lazy {
        ResponseBody(runBlockingCompat { response.readRawBytes() })
    }

    /** Alias for [NiceResponse] for source compatibility with original NiceHttp */
    @Deprecated(
        "OkHttp compatibility shim, no longer needed. Use the NiceResponse directly.",
        ReplaceWith("this"),
        DeprecationLevel.WARNING,
    )
    val okhttpResponse: NiceResponse get() = this

    /** Returns the value of the header with the given [name], or null if absent. */
    @Deprecated(
        "Exists for back-compat. Use headers[name] directly.",
        ReplaceWith("headers[name]"),
        DeprecationLevel.WARNING,
    )
    fun header(name: String): String? = headers[name]

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

        return channel().readTextLimited(
            response.charset() ?: Charsets.UTF_8
        )
    }

    /** Response body. Call .bytes() or .string() to read. Call .close() when done (no-op here). */
    suspend fun body(): ResponseBody = ResponseBody(response.readRawBytes())

    /** Raw [ByteReadChannel] for incremental reads */
    suspend fun channel(): ByteReadChannel = response.bodyAsChannel()

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

private suspend fun ByteReadChannel.readTo(dst: StringBuilder, charset: Charset, max: Long): Long {
    var consumed = 0L
    val decoder = charset.newDecoder()
    while (!isClosedForRead) {
        awaitContent()
        val before = dst.length
        // readBuffer property is internal but no public API can
        // get a Source (needed for decoder) without an
        // intermediate allocation.
        @OptIn(InternalAPI::class)
        decoder.decode(readBuffer, dst, (max - consumed).toInt())
        consumed += dst.length - before
        if (consumed >= max) break
    }
    return consumed
}

private suspend fun ByteReadChannel.readTextLimited(charset: Charset): String {
    val builder = StringBuilder()
    val read = readTo(builder, charset, MAX_TEXT_BYTES)
    if (read >= MAX_TEXT_BYTES) {
        cancel(IllegalStateException(
            "Response exceeded $MAX_TEXT_BYTES bytes ($read). Use .textLarge instead."
        ))
    }
    return builder.toString()
}
