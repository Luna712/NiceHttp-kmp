package com.lagradost.nicehttp

import io.ktor.client.HttpClient
import okhttp3.OkHttpClient

actual typealias NiceOkHttpClientCompat = OkHttpClient
internal actual fun NiceOkHttpClientCompat.toHttpClient(): HttpClient = defaultHttpClient(this)
internal actual fun defaultNiceOkHttpClientCompat(): NiceOkHttpClientCompat = OkHttpClient()
