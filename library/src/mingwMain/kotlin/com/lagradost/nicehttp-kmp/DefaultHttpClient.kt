package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.winhttp.*

actual fun defaultHttpClient(): HttpClient = HttpClient(WinHttp)
