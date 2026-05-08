package com.lagradost.nicehttp.kmp

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.client.request.forms.*
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * Full bidirectional bridge between OkHttp and NiceHttp KMP interceptors.
 *
 * [okhttp3.Interceptor.toInterceptor]:
 *   Converts an OkHttp interceptor to a NiceHttp KMP [Interceptor].
 *   Preserves the full OkHttp interceptor contract:
 *   - chain.request()    returns the current OkHttp Request
 *   - chain.proceed()    delegates back through the Ktor stack
 *   - chain.connection() returns null (not available outside OkHttp internals)
 *   - Timeout accessors  return 0 (managed by Ktor client config instead)
 *   Threading: OkHttp interceptors are synchronous. The Ktor suspend chain is
 *   bridged via runBlocking so the OkHttp interceptor sees a normal sync call.
 *
 * [Interceptor.toOkHttpInterceptor]:
 *   Converts a NiceHttp KMP interceptor to an [okhttp3.Interceptor].
 *   Use when passing a KMP interceptor into raw OkHttp client config.
 *   The suspend chain is bridged via runBlocking.
 */

fun okhttp3.Interceptor.toInterceptor(): Interceptor = Interceptor { chain ->
    val okRequest = chain.request.toOkHttpRequest()

    val okChain = object : okhttp3.Interceptor.Chain {
        override fun request(): okhttp3.Request = okRequest

        override fun proceed(request: okhttp3.Request): Response {
            val updatedBuilder = request.toKtorRequestBuilder()
            return runBlocking {
                val niceResponse = chain.proceed(updatedBuilder)
                niceResponse.toOkHttpResponse(request)
            }
        }

        override fun connection(): okhttp3.Connection? = null

        override fun call(): okhttp3.Call =
            throw UnsupportedOperationException("call() not available in KMP bridge")

        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }

    this@toInterceptor.intercept(okChain).toNiceResponse()
}

fun Interceptor.toOkHttpInterceptor(): okhttp3.Interceptor = okhttp3.Interceptor { chain ->
    val ktorBuilder = chain.request().toKtorRequestBuilder()

    val kmpChain = object : Interceptor.Chain {
        override val request: HttpRequestBuilder = ktorBuilder

        override suspend fun proceed(request: HttpRequestBuilder): INiceResponse {
            val updatedOkRequest = request.toOkHttpRequest()
            val okResponse = chain.proceed(updatedOkRequest)
            return okResponse.toNiceResponse()
        }
    }

    val niceResponse = runBlocking { this@toOkHttpInterceptor.intercept(kmpChain) }
    niceResponse.toOkHttpResponse(chain.request())
}

internal fun HttpRequestBuilder.toOkHttpRequest(): okhttp3.Request {
    val url = this.url.buildString()
    val method = this.method.value
    val okHeaders = okhttp3.Headers.Builder().also { h ->
        this.headers.build().forEach { k, values ->
            values.forEach { v -> h.add(k, v) }
        }
    }.build()

    val okBody: okhttp3.RequestBody? = when {
        method == "GET" || method == "HEAD" -> null
        else -> when (val b = this.body) {
            is ByteArrayContent ->
                b.bytes().toRequestBody(b.contentType?.toString()?.toMediaTypeOrNull())
            is TextContent ->
                b.text.toRequestBody(b.contentType.toString().toMediaTypeOrNull())
            is FormDataContent -> {
                val form = okhttp3.FormBody.Builder()
                b.formData.entries().forEach { entry ->
                    val key = entry.key
                    entry.value.forEach { v -> form.addEncoded(key, v) }
                }
                form.build()
            }
            else -> null
        }
    }

    return okhttp3.Request.Builder()
        .url(url)
        .headers(okHeaders)
        .method(method, okBody)
        .build()
}

internal fun okhttp3.Request.toKtorRequestBuilder(): HttpRequestBuilder =
    HttpRequestBuilder().apply {
        method = HttpMethod(this@toKtorRequestBuilder.method)
        url(this@toKtorRequestBuilder.url.toString())
        this@toKtorRequestBuilder.headers.forEach { (k, v) -> header(k, v) }
        val okBody = this@toKtorRequestBuilder.body
        if (okBody != null) {
            val buffer = Buffer()
            okBody.writeTo(buffer)
            val bytes = buffer.readByteArray()
            val ct = okBody.contentType()?.toString()
                ?.let { ContentType.parse(it) }
                ?: ContentType.Application.OctetStream
            setBody(ByteArrayContent(bytes, ct))
        }
    }

internal fun INiceResponse.toOkHttpResponse(request: okhttp3.Request): okhttp3.Response =
    okhttp3.Response.Builder()
        .request(request)
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(
            okhttp3.ResponseBody.create(
                headers["content-type"]?.toMediaTypeOrNull(),
                body.bytes()
            )
        )
        .also { builder ->
            headers.forEach { k, values ->
                values.forEach { v -> builder.addHeader(k, v) }
            }
        }
        .build()

/**
 * Converts an [okhttp3.Response] to a [SyntheticNiceResponse].
 * Uses the real request URL so [INiceResponse.url] is always accurate.
 */
internal fun okhttp3.Response.toNiceResponse(
    parser: ResponseParser? = null,
): INiceResponse {
    val bytes = body?.bytes() ?: ByteArray(0)
    val url = request.url.toString()
    val ktorHeaders = Headers.build {
        headers.forEach { (k, v) -> append(k, v) }
    }
    return SyntheticNiceResponse(
        rawBytes = bytes,
        code = code,
        headers = ktorHeaders,
        url = url,
        parser = parser,
    )
}
