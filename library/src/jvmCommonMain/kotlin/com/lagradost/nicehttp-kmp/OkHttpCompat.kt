package com.lagradost.nicehttp

import io.ktor.http.Headers
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody as OkRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

@Deprecated(
    "OkHttp back-compat shim. Use the Ktor-based request builder instead.",
    level = DeprecationLevel.WARNING,
)
fun requestCreator(
    method: String,
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    params: Map<String, String> = emptyMap(),
    cookies: Map<String, String> = emptyMap(),
    data: Map<String, String>? = null,
    files: List<NiceFile>? = null,
    json: Any? = null,
    requestBody: OkRequestBody? = null,
    cacheTime: Int? = null,
    cacheUnit: TimeUnit? = null,
    responseParser: ResponseParser? = null,
): Request {
    val finalUrl = addParamsToUrl(url, params)
    val ktorHeaders = buildHeaders(headers, referer, cookies)
    val okHeaders = okhttp3.Headers.Builder().also { builder ->
        ktorHeaders.forEach { k, values -> values.forEach { v -> builder.add(k, v) } }
    }.build()

    // Build OkHttp body from data/json/files if no requestBody provided
    val body: OkRequestBody? = requestBody ?: run {
        val upper = method.uppercase()
        if (upper == "GET" || upper == "HEAD") return@run null
        when {
            !data.isNullOrEmpty() -> {
                val form = okhttp3.FormBody.Builder()
                data.forEach { (k, v) -> form.addEncoded(k, v) }
                form.build()
            }
            json != null -> {
                val jsonStr = when (json) {
                    is String -> json
                    is JsonAsString -> json.string
                    else -> json.toString()
                }
                jsonStr.toRequestBody("application/json;charset=utf-8".toMediaTypeOrNull())
            }
            !files.isNullOrEmpty() -> {
                val builder = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                files.forEach { file ->
                    if (file.bytes != null) {
                        builder.addFormDataPart(
                            file.name, file.fileName,
                            file.bytes.toRequestBody(file.fileType?.toMediaTypeOrNull())
                        )
                    } else {
                        builder.addFormDataPart(file.name, file.fileName)
                    }
                }
                builder.build()
            }
            upper == "POST" || upper == "PUT" -> okhttp3.FormBody.Builder().build()
            else -> null
        }
    }

    return Request.Builder()
        .url(finalUrl)
        .headers(okHeaders)
        .method(method, body)
        .apply {
            if (cacheTime != null && cacheUnit != null) {
                cacheControl(CacheControl.Builder().maxAge(cacheTime, cacheUnit).build())
            }
        }
        .build()
}

// Provides async-able Calls
internal class ContinuationCallback(
    private val call: Call,
    private val continuation: CancellableContinuation<Response>,
) : Callback, CompletionHandler {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onResponse(call: Call, response: Response) {
        continuation.resume(response) { _, _, _ -> }
    }

    override fun onFailure(call: Call, e: IOException) {
        // Cannot throw exception on SocketException since that can lead to un-catchable crashes
        // when you exit an activity as a request
        println("Exception in NiceHttp: ${e.javaClass.name} ${e.message}")
        if (call.isCanceled()) {
            // Must be able to throw errors, for example timeouts
            if (e is InterruptedIOException)
                continuation.cancel(e)
            else
                e.printStackTrace()
        } else {
            continuation.resumeWithException(e)
        }
    }

    override fun invoke(cause: Throwable?) {
        try {
            call.cancel()
        } catch (_: Throwable) {
        }
    }
}

@Deprecated(
    "OkHttp back-compat shim. Use the Ktor-based request builder instead.",
    level = DeprecationLevel.ERROR,
)
class RequestsCompat {
    companion object {
        suspend inline fun Call.await(): Response {
            return suspendCancellableCoroutine { continuation ->
                val callback = ContinuationCallback(this, continuation)
                enqueue(callback)
                continuation.invokeOnCancellation(callback)
            }
        }
    }
}
