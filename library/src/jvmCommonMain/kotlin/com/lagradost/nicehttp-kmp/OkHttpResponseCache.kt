package com.lagradost.nicehttp

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.Collections
import java.util.UUID

/**
 * This was added as a temporary bridge between ktor and okhttp, in order
 * to make NiceResponse.okhttpResponse backwards-compatible.
 */

internal const val NICEHTTP_OKHTTP_ID_HEADER = "X-NiceHttp-OkHttp-Response-Id"

internal object OkHttpResponseCache {
    private const val maxEntries = 200

    private class CachedEntry(
        val template: Response,
        val bodyBytes: ByteArray?,
        val contentType: MediaType?,
    )

    private val map = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedEntry>(16, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedEntry>?): Boolean =
                size > maxEntries
        }
    )

    fun put(id: String, response: Response, bodyBytes: ByteArray?, contentType: MediaType?) {
        map[id] = CachedEntry(response.newBuilder().body(null).build(), bodyBytes, contentType)
    }

    fun take(id: String): Response? {
        val entry = map.remove(id) ?: return null
        return entry.template.newBuilder()
            .body(entry.bodyBytes?.toResponseBody(entry.contentType))
            .build()
    }
}

private object OkHttpResponseCaptureInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val id = UUID.randomUUID().toString()
        val networkResponse = chain.proceed(chain.request())

        val bytes = networkResponse.body?.bytes()
        val contentType = networkResponse.body?.contentType()

        OkHttpResponseCache.put(id, networkResponse, bytes, contentType)

        return networkResponse.newBuilder()
            .header(NICEHTTP_OKHTTP_ID_HEADER, id)
            .body(bytes?.toResponseBody(contentType))
            .build()
    }
}

internal fun OkHttpClient.Builder.addNiceHttpResponseCapture(): OkHttpClient.Builder =
    addInterceptor(OkHttpResponseCaptureInterceptor)
