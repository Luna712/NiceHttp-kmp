package com.lagradost.nicehttp

import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.charsets.*

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
            contentType: ContentType = ContentType.Application.OctetStream,
        ): RequestBody = BytesRequestBody(bytes, contentType)

        /** Plain text or JSON body. */
        fun text(
            text: String,
            contentType: ContentType = ContentType.Text.Plain.withCharset(Charsets.UTF_8),
        ): RequestBody = TextRequestBody(text, contentType)

        /** URL-encoded form body. */
        fun form(params: Map<String, String>): RequestBody = FormRequestBody(params)

        /** Wraps a pre-built [OutgoingContent] directly. Use for multipart or custom body types. */
        fun of(content: OutgoingContent): RequestBody = OutgoingContentRequestBody(content)
    }
}

private class BytesRequestBody(
    private val bytes: ByteArray,
    private val contentType: ContentType,
) : RequestBody() {
    override val content: OutgoingContent =
        ByteArrayContent(bytes, contentType)
}

private class TextRequestBody(
    private val text: String,
    private val contentType: ContentType,
) : RequestBody() {
    override val content: OutgoingContent =
        TextContent(text, contentType)
}

private class FormRequestBody(
    private val params: Map<String, String>,
) : RequestBody() {
    override val content: OutgoingContent =
        FormDataContent(Parameters.build {
            params.forEach { (k, v) -> append(k, v) }
        })
}

private class OutgoingContentRequestBody(
    override val content: OutgoingContent,
) : RequestBody()
