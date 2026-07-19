package com.lagradost.nicehttp

expect class NiceOkHttpClientCompat
internal expect fun NiceOkHttpClientCompat.toHttpClient(): io.ktor.client.HttpClient
internal expect fun defaultNiceOkHttpClientCompat(): NiceOkHttpClientCompat
