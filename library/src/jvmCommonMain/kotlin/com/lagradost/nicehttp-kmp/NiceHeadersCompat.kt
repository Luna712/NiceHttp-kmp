package com.lagradost.nicehttp

import okhttp3.Headers.Companion.headersOf

actual typealias NiceHeadersCompat = okhttp3.Headers
internal actual fun NiceResponse.resolveOkHttpHeadersCompat(): NiceHeadersCompat {
    @Suppress("DEPRECATION")
    return okhttpResponse?.headers ?: headersOf()
}
