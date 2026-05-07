package com.lagradost.nicehttp.kmp

import io.ktor.client.*
import io.ktor.client.engine.curl.*

// This file covers Linux targets (linuxX64, linuxArm64).
// Apple targets (iOS, macOS, tvOS, watchOS) are handled in appleMain via Darwin.
// Windows (mingwX64) is handled in mingwMain via WinHttp.
actual fun defaultHttpClient(): HttpClient = HttpClient(Curl)
