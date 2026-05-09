package com.lagradost.nicehttp.kmp

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.Headers

/**
 * KMP replacement for okhttp3.Interceptor.
 * On JVM/Android, okhttp3.Interceptor can be converted via .toNiceInterceptor().
 * On JS/WASM/Native, implement this interface directly.
 */
fun interface Interceptor {
    suspend fun intercept(chain: Chain): INiceResponse

    interface Chain {
        val request: HttpRequestBuilder
        val url: String get() = request.url.buildString()
        val headers: Headers get() = request.headers.build()
        val method: String get() = request.method.value
        suspend fun proceed(request: HttpRequestBuilder): INiceResponse
        suspend fun proceed(): INiceResponse = proceed(request)
    }
}

/**
 * Interceptor that does nothing and just proceeds with the request.
 * Useful as a base/no-op.
 */
object PassThroughInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): INiceResponse =
        chain.proceed()
}

/**
 * Interceptor that adds headers to every request.
 */
class HeadersInterceptor(
    private val headers: Map<String, String>,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): INiceResponse {
        val req = chain.request
        headers.forEach { (k, v) ->
            req.headers.remove(k)  // remove existing value first
            req.headers.append(k, v)
        }
        return chain.proceed(req)
    }
}

/**
 * Interceptor that retries failed requests.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val shouldRetry: (INiceResponse) -> Boolean = { !it.isSuccessful },
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): INiceResponse {
        var response = chain.proceed()
        var retries = 0
        while (shouldRetry(response) && retries < maxRetries) {
            retries++
            response = chain.proceed()
        }
        return response
    }
}

/**
 * Interceptor that retries a failed request against a fallback URL.
 * Replaces the first occurrence of [primaryUrl] with [fallbackUrl] in the request URL.
 */
/*class FallbackUrlInterceptor(
    private val primaryUrl: String,
    private val fallbackUrl: String,
    private val shouldFallback: (INiceResponse) -> Boolean = { !it.isSuccessful },
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): INiceResponse {
        try {
            val response = chain.proceed()
            if (!shouldFallback(response)) return response
        } catch (_: Exception) {
        }

        val newUrl = chain.request.url.buildString().replaceFirst(primaryUrl, fallbackUrl)
        val parsed = io.ktor.http.Url(newUrl)

        val fallbackRequest = HttpRequestBuilder().apply {
            takeFrom(chain.request)
            url.protocol = parsed.protocol
            url.host = parsed.host
            url.port = parsed.port
            url.segments = parsed.segments
            url.parameters.clear()
            parsed.parameters.forEach { key, values ->
                values.forEach { url.parameters.append(key, it) }
            }
        }

        return chain.proceed(fallbackRequest)
    }
}*/

object CacheInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): INiceResponse {
        val req = chain.request
        req.headers.remove("Cache-Control") // Remove site cache
        req.headers.remove("Pragma") // Remove site cache
        req.headers.append("Cache-Control", "only-if-cached, max-stale=${Int.MAX_VALUE}")
        return chain.proceed(req)
    }
}

/**
 * Interceptor that logs requests and responses.
 */
class LoggingInterceptor(
    private val log: (String) -> Unit = ::println
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): INiceResponse {
        log("--> ${chain.method} ${chain.url}")
        chain.headers.forEach { k, values ->
            values.forEach { v -> log("$k: $v") }
        }
        val response = chain.proceed()
        log("<-- ${response.code} ${response.url}")
        return response
    }
}
