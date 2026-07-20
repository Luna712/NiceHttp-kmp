package com.lagradost.nicehttp

import okhttp3.Call
import okhttp3.Response

actual open class RequestsCompanionCompat actual constructor() {
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "OkHttp back-compat shim. Use the Ktor-based request builder instead.",
        level = DeprecationLevel.WARNING,
    )
    suspend fun Call.await(): Response = with(RequestsCompat) { await() }
}
