package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.js.*

actual fun defaultHttpClient(): HttpClient = HttpClient(Js)
