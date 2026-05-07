package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.darwin.*

actual fun defaultHttpClient(): HttpClient = HttpClient(Darwin)
