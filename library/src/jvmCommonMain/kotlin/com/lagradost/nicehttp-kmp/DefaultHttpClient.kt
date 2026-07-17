package com.lagradost.nicehttp

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import okhttp3.OkHttpClient

actual fun defaultHttpClient(): HttpClient = defaultHttpClient(OkHttpClient.Builder())

fun defaultHttpClient(preconfigured: OkHttpClient): HttpClient =
    defaultHttpClient(preconfigured.newBuilder())

private fun defaultHttpClient(builder: OkHttpClient.Builder): HttpClient = okHttpClient(
    builder
        // OkHttp engine exposes the raw builder via `engine { preconfigured = ... }`
        // so callers can still attach DNS-over-HTTPS, custom interceptors, etc.
        // addNiceHttpResponseCapture() is what makes NiceResponse.okHttpResponse work.
        // It will eventually be removed.
        .addNetworkInterceptor(CacheNetworkInterceptor)
        .addNiceHttpResponseCapture()
)

actual fun insecureHttpClient(): HttpClient =
    defaultHttpClient(OkHttpClient.Builder().ignoreAllSSLErrors())

private fun okHttpClient(builder: OkHttpClient.Builder): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
    engine { preconfigured = builder.build() }
}
