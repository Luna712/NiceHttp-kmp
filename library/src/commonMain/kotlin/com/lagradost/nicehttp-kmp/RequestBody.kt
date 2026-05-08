package com.lagradost.nicehttp.kmp

import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*

/**
 * KMP replacement for okhttp3.RequestBody.
 * Wraps an [OutgoingContent] so it works on all targets without OkHttp dependency.
 * On JVM/Android, okhttp3.RequestBody can be converted via .toNiceRequestBody().
 */
class RequestBody(
    val content: OutgoingContent,
) {
    companion object {
        /** Raw bytes body with the given content type. */
        fun bytes(
            bytes: ByteArray,
            contentType: String = "application/octet-stream",
        ) = RequestBody(ByteArrayContent(bytes, ContentType.parse(contentType)))

        /** Plain text or JSON body. */
        fun text(
            text: String,
            contentType: String = RequestBodyTypes.TEXT,
        ) = RequestBody(TextContent(text, ContentType.parse(contentType)))

        /** URL-encoded form body. */
        fun form(params: Map<String, String>) = RequestBody(
            FormDataContent(Parameters.build {
                params.forEach { (k, v) -> append(k, v) }
            })
        )
    }
}
