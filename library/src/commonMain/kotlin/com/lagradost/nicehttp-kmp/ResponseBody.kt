package com.lagradost.nicehttp

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.write

/**
 * KMP replacement for okhttp3.ResponseBody.
 * Keeps .bytes and .close() call sites working unchanged.
 *
 * TODO: Migrate to accept a [io.ktor.utils.io.ByteReadChannel] instead of a [ByteArray] and make
 *  [bytes], [string], [contentLength], [byteStream], and [source] suspend functions.
 *  The channel can then be read lazily on demand, avoiding the eager copy done here.
 */
class ResponseBody(private val data: ByteArray) {
    /** Returns the body as a byte array. */
    fun bytes(): ByteArray = data

    /** Returns the body as a UTF-8 string. */
    fun string(): String = data.decodeToString()

    /** Returns the content length in bytes. */
    fun contentLength(): Long = data.size.toLong()

    /**
     * Returns the body as a platform-specific input stream.
     * On JVM/Android returns [java.io.InputStream].
     * On other platforms returns a [PlatformInputStream] wrapping the raw bytes.
     */
    fun byteStream(): PlatformInputStream = data.toPlatformInputStream()

    /** Returns the body as a [Source]. */
    fun source(): Source = Buffer().apply { write(data) }

    /** No-op - included for okhttp3.ResponseBody source compatibility. */
    @Deprecated(
        "No-op OkHttp compatibility shim, safe to remove.",
        ReplaceWith(""),
        DeprecationLevel.WARNING,
    )
    fun close() = Unit
}
