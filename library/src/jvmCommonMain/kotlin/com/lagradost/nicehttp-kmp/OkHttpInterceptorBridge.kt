package com.lagradost.nicehttp.kmp

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.client.request.forms.*
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

        override fun proceed(request: okhttp3.Request): okhttp3.Response {
            val updatedBuilder = request.toKtorRequestBuilder()
            return runBlocking {
                val call = ctx.execute(updatedBuilder)
                call.toOkHttpResponse(request)
            }
        }

        override fun connection() = null
        override fun call(): okhttp3.Call =
            throw UnsupportedOperationException("call() not available in KMP bridge")
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    }

    val okResponse = this@toInterceptor.intercept(okChain)
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
    call.toOkHttpResponse(chain.request())
}

internal suspend fun okhttp3.Response.toKtorCall(
    request: HttpRequestBuilder,
    parser: ResponseParser? = null,
): HttpClientCall {
    // HttpClientCall can't be constructed directly from outside Ktor internals.
    // Use a MockEngine to produce a real HttpClientCall from our bytes.
    val bytes = body?.bytes() ?: ByteArray(0)
    val code = code
    val url = this.request.url.toString()
    val ktorHeaders = Headers.build {
        headers.forEach { (k, v) -> append(k, v) }
    }
    val mockEngine = io.ktor.client.engine.mock.MockEngine {
        io.ktor.client.engine.mock.respond(
            content = bytes,
            status = HttpStatusCode.fromValue(code),
            headers = ktorHeaders,
        )
    }
    val client = HttpClient(mockEngine)
    return client.call(url)
}

internal suspend fun HttpClientCall.toOkHttpResponse(request: okhttp3.Request): okhttp3.Response {
    val bytes = response.readRawBytes()
    return okhttp3.Response.Builder()
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
