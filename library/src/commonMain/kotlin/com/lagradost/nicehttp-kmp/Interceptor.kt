package com.lagradost.nicehttp.kmp

import io.ktor.client.request.HttpRequestBuilder

/**
 * KMP replacement for okhttp3.Interceptor.
 */
fun interface Interceptor {
    suspend fun intercept(chain: Chain): NiceResponse

    interface Chain {
        val request: HttpRequestBuilder
        suspend fun proceed(request: HttpRequestBuilder): NiceResponse
    }
}
