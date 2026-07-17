package com.lagradost.nicehttp

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import okhttp3.OkHttpClient

actual fun defaultHttpClient(): HttpClient = defaultHttpClient(OkHttpClient.Builder())

fun defaultHttpClient(configure: OkHttpClient.Builder.() -> Unit): HttpClient =
    defaultHttpClient(OkHttpClient.Builder().apply(configure))

fun defaultHttpClient(preconfigured: OkHttpClient): HttpClient =
    defaultHttpClient(preconfigured.newBuilder())

private fun defaultHttpClient(builder: OkHttpClient.Builder): HttpClient {
    // OkHttp engine exposes the raw builder via `engine { preconfigured = ... }`
    // so callers can still attach DNS-over-HTTPS, custom interceptors, etc.
    // addNiceHttpResponseCapture() is what makes NiceResponse.okHttpResponse work.
    // It will eventually be removed.
    builder.addNetworkInterceptor(CacheNetworkInterceptor())
    builder.addNiceHttpResponseCapture()
    return okHttpClient(builder)
}

actual fun insecureHttpClient(): HttpClient = insecureHttpClient(OkHttpClient.Builder())

fun insecureHttpClient(configure: OkHttpClient.Builder.() -> Unit): HttpClient =
    insecureHttpClient(OkHttpClient.Builder().apply(configure))

fun insecureHttpClient(preconfigured: OkHttpClient): HttpClient =
    insecureHttpClient(preconfigured.newBuilder())

private fun insecureHttpClient(builder: OkHttpClient.Builder): HttpClient {
    builder.ignoreAllSSLErrors()
    return defaultHttpClient(builder)
}

private fun okHttpClient(builder: OkHttpClient.Builder): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout)
    install(HttpCache)
    install(HttpRequestRetry) { noRetry() }
    engine { preconfigured = builder.build() }
}
