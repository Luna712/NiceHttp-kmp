package com.lagradost.nicehttp

import io.ktor.http.content.*
import io.ktor.client.request.forms.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

/**
 * Converts an [okhttp3.RequestBody] to a NiceHttp [RequestBody].
 * Reads the OkHttp body into a byte array via Okio then wraps it.
 *
 * TODO: Deprecate
 */
fun okhttp3.RequestBody.toNiceRequestBody(): RequestBody {
    val buffer = Buffer()
    writeTo(buffer)
    val bytes = buffer.readByteArray()
    val ct = contentType()?.toString() ?: "application/octet-stream"
    return RequestBody.bytes(bytes, ct)
}

/**
 * Converts a NiceHttp [RequestBody] to an [okhttp3.RequestBody].
 * Used when passing a KMP request body into raw OkHttp code.
 *
 * TODO: Deprecate
 */
fun RequestBody.toOkHttpRequestBody(): okhttp3.RequestBody {
    return when (val c = content) {
        is ByteArrayContent ->
            c.bytes().toRequestBody(c.contentType?.toString()?.toMediaTypeOrNull())
        is TextContent ->
            c.text.toRequestBody(c.contentType.toString().toMediaTypeOrNull())
        is FormDataContent -> {
            val form = okhttp3.FormBody.Builder()
            c.formData.entries().forEach { entry ->
                val key = entry.key
                entry.value.forEach { v -> form.addEncoded(key, v) }
            }
            form.build()
        }
        else -> {
            println("Warning: Unknown OutgoingContent type ${c::class}, falling back to empty body")
            ByteArray(0).toRequestBody(null)
        }
    }
}
