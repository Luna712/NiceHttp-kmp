package com.lagradost.nicehttp

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.Collections
import java.util.UUID

/**
 * This was added as a temporary bridge between ktor and okhttp, in order
 * to make NiceResponse.okHttpResponse backwards-compatible.
 */

internal const val NICEHTTP_OKHTTP_ID_HEADER = "X-NiceHttp-OkHttp-Response-Id"

internal object OkHttpResponseCache {
    private const val maxEntries = 200

    private val map = Collections.synchronizedMap(
        object : LinkedHashMap<String, Response>(16, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Response>?): Boolean =
                size > maxEntries
        }
    )

    fun put(id: String, response: Response) {
        map[id] = response
    }

    fun take(id: String): Response? = map.remove(id)
}

private object OkHttpResponseCaptureInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val id = UUID.randomUUID().toString()
        val response = chain.proceed(chain.request()).newBuilder()
            .header(NICEHTTP_OKHTTP_ID_HEADER, id)
            .build()
        OkHttpResponseCache.put(id, response)
        return response
    }
}

@Deprecated(
    "This was Only added as a temporary bridge to make NiceResponse.okHttpResponse work.",
    level = DeprecationLevel.ERROR,
)
fun OkHttpClient.Builder.addNiceHttpResponseCapture(): OkHttpClient.Builder =
    addInterceptor(OkHttpResponseCaptureInterceptor)

