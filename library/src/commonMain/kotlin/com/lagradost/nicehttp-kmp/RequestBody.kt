package com.lagradost.nicehttp

import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*

/**
 * KMP replacement for okhttp3.RequestBody.
 * Wraps an [OutgoingContent] so it works on all targets without OkHttp dependency.
 * On JVM/Android, okhttp3.RequestBody can be converted via .toNiceRequestBody().
 */
abstract class RequestBody {
    abstract val content: OutgoingContent

    companion object {
        /** Raw bytes body with the given content type. */
        fun bytes(
            bytes: ByteArray,
            contentType: String = "application/octet-stream",
        ): RequestBody = BytesRequestBody(bytes, contentType)

        fun text(
            text: String,
            contentType: String = RequestBodyTypes.TEXT,
        ): RequestBody = TextRequestBody(text, contentType)

        fun form(params: Map<String, String>): RequestBody = FormRequestBody(params)
        fun of(content: OutgoingContent): RequestBody = OutgoingContentRequestBody(content)
    }
}

private class BytesRequestBody(
    private val bytes: ByteArray,
    private val contentType: String,
) : RequestBody() {
    override val content: OutgoingContent =
        ByteArrayContent(bytes, ContentType.parse(contentType))
}

private class TextRequestBody(
    private val text: String,
    private val contentType: String,
) : RequestBody() {
    override val content: OutgoingContent =
        TextContent(text, ContentType.parse(contentType))
}

private class FormRequestBody(
    private val params: Map<String, String>,
) : RequestBody() {
    override val content: OutgoingContent =
        FormDataContent(Parameters.build {
            params.forEach { (k, v) -> append(k, v) }
        })
}
