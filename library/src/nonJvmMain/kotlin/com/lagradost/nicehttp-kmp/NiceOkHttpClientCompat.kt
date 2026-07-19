package com.lagradost.nicehttp

import io.ktor.client.HttpClient

actual class NiceOkHttpClientCompat internal constructor(internal val client: HttpClient)
internal actual fun NiceOkHttpClientCompat.toHttpClient(): HttpClient = client
internal actual fun defaultNiceOkHttpClientCompat(): NiceOkHttpClientCompat = NiceOkHttpClientCompat(defaultHttpClient())
