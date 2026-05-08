package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

internal class RealChain(
    override val request: HttpRequestBuilder,
    private val client: HttpClient,
    private val responseParser: ResponseParser?,
) : Interceptor.Chain {
    override suspend fun proceed(request: HttpRequestBuilder): INiceResponse {
        val response = client.request(request)
        return NiceResponse(response, responseParser)
    }
}

/**
 * Builds a chain from a list of interceptors and executes it.
 * Mirrors OkHttp's interceptor chain exactly - each interceptor
 * calls chain.proceed() to pass to the next one.
 */
internal suspend fun executeWithInterceptors(
    interceptors: List<Interceptor>,
    request: HttpRequestBuilder,
    client: HttpClient,
    responseParser: ResponseParser?,
): INiceResponse {
    if (interceptors.isEmpty()) {
        return RealChain(request, client, responseParser).proceed(request)
    }

    fun chainAt(index: Int): Interceptor.Chain = object : Interceptor.Chain {
        override val request: HttpRequestBuilder = request
        override suspend fun proceed(request: HttpRequestBuilder): INiceResponse {
            return if (index + 1 < interceptors.size) {
                interceptors[index + 1].intercept(chainAt(index + 1))
            } else {
                RealChain(request, client, responseParser).proceed(request)
            }
        }
    }

    return interceptors[0].intercept(chainAt(0))
}
