package com.lagradost.nicehttp.kmp

/**
 * KMP replacement for okhttp3.ResponseBody.
 * Keeps .bytes and .close() call sites working unchanged.
 */
class ResponseBody(private val data: ByteArray) {
    /** Returns the body as a byte array. */
    fun bytes(): ByteArray = data

    /** Returns the body as a UTF-8 string. */
    fun string(): String = data.decodeToString()

    /** Returns the content length in bytes. Equivalent to okhttp3.ResponseBody.contentLength(). */
    fun contentLength(): Long = data.size.toLong()

    /** No-op - included for okhttp3.ResponseBody source compatibility. */
    fun close() = Unit
}
