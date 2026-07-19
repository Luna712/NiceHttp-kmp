package com.lagradost.nicehttp

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.cancel
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.write

/**
 * KMP replacement for okhttp3.ResponseBody, backed by a [ByteReadChannel].
 *
 * The channel is read lazily on first access and the result is cached, since a
 * [ByteReadChannel] can only be consumed once but callers may call multiple accessors.
 */
class ResponseBody(private val channel: ByteReadChannel) {
    private var cachedBytes: ByteArray? = null

    private suspend fun readAll(): ByteArray =
        cachedBytes ?: channel.readRemaining().readByteArray().also { cachedBytes = it }

    /** Returns the body as a byte array. */
    suspend fun bytes(): ByteArray = readAll()

    /** Returns the body as a UTF-8 string. */
    suspend fun string(): String = readAll().decodeToString()

    /** Returns the content length in bytes. */
    suspend fun contentLength(): Long = readAll().size.toLong()

    /**
     * Returns the body as a platform-specific input stream.
     * On JVM/Android returns [java.io.InputStream].
     * On other platforms returns a [PlatformInputStream] wrapping the raw bytes.
     */
    suspend fun byteStream(): PlatformInputStream = readAll().toPlatformInputStream()

    /** Returns the body as a [Source]. */
    suspend fun source(): Source = Buffer().apply { write(readAll()) }

    /**
     * Cancels the underlying channel, releasing the connection if the body was never
     * (fully) consumed. Safe to call after the body has already been read, it's then
     * a no-op since the channel is already exhausted/closed.
     */
    fun close() {
        if (cachedBytes == null) channel.cancel()
    }
}
