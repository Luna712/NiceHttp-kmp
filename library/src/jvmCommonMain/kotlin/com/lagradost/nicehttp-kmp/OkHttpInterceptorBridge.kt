package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Response
import okio.Buffer

/**
 * Full bidirectional bridge between OkHttp and NiceHttp KMP interceptors.
 *
 * [okhttp3.Interceptor.toNiceInterceptor]:
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
fun okhttp3.Interceptor.toNiceInterceptor(): Interceptor = Interceptor { ctx ->
    val okRequest = ctx.request.toOkHttpRequest()

    val okChain = object : okhttp3.Interceptor.Chain {
        override fun request() = okRequest

        override fun proceed(request: okhttp3.Request): Response {
            val updatedBuilder = request.toKtorRequestBuilder()
            return runBlocking {
                val call = ctx.execute(updatedBuilder)
                call.toOkHttpResponse(request)
            }
        }

        override fun connection() = null

        override fun call(): okhttp3.Call = object : okhttp3.Call {
            private var executed = false
            private var canceled = false

            override fun request() = okRequest

            override fun execute(): okhttp3.Response = runBlocking {
                executed = true
                ctx.execute(okRequest.toKtorRequestBuilder()).toOkHttpResponse(okRequest)
            }

            override fun enqueue(responseCallback: okhttp3.Callback) {
                try {
                    val response = execute()
                    responseCallback.onResponse(this, response)
                } catch (e: java.io.IOException) {
                    responseCallback.onFailure(this, e)
                }
            }

            override fun cancel() { canceled = true }
            override fun isExecuted() = executed
            override fun isCanceled() = canceled
            override fun timeout() = okio.Timeout.NONE
            override fun clone(): okhttp3.Call = this

            override fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = null
            override fun <T> tag(type: Class<out T>): T? = null
            override fun <T : Any> tag(type: kotlin.reflect.KClass<T>, computeIfAbsent: () -> T): T =
                computeIfAbsent()
            override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T =
                computeIfAbsent()
        }

        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }

    val okResponse = this@toNiceInterceptor.intercept(okChain)
    okResponse.toKtorCall(ctx.request)
}

fun Interceptor.toOkHttpInterceptor(): okhttp3.Interceptor = okhttp3.Interceptor { chain ->
    val ktorBuilder = chain.request().toKtorRequestBuilder()
    val call = runBlocking {
        val ctx = HttpSendInterceptorContext(ktorBuilder) { req ->
            // Can't execute via Ktor here since we're in OkHttp's sync chain
            // so just build a synthetic call from the OkHttp proceed result
            chain.proceed(req.toOkHttpRequest()).toKtorCall(req)
        }
        this@toOkHttpInterceptor.intercept(ctx)
    }
    runBlocking { call.toOkHttpResponse(chain.request()) }
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

internal suspend fun okhttp3.Response.toKtorCall(
    request: HttpRequestBuilder,
): HttpClientCall {
    val bytes = body.bytes()
    val code = code
    val url = this.request.url.toString()
    val ktorHeaders = Headers.build {
        headers.forEach { (k, v) -> append(k, v) }
    }
    val mockEngine = MockEngine {
        respond(
            content = bytes,
            status = HttpStatusCode.fromValue(code),
            headers = ktorHeaders,
        )
    }
    val client = HttpClient(mockEngine)
    return client.get(url).call
}

internal suspend fun HttpClientCall.toOkHttpResponse(request: okhttp3.Request): Response {
    val bytes = response.readRawBytes()
    return Response.Builder()
        .request(request)
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(response.status.value)
        .message("")
        .body(bytes.toResponseBody(response.headers["content-type"]?.toMediaTypeOrNull()))
        .also { builder ->
            response.headers.forEach { k, values ->
                values.forEach { v -> builder.addHeader(k, v) }
            }
        }
        .build()
}
