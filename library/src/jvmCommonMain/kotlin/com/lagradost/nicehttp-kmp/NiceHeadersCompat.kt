package com.lagradost.nicehttp

actual typealias NiceHeadersCompat = okhttp3.Headers
internal actual fun NiceResponse.resolveOkHttpHeadersCompat(): NiceHeadersCompat? {
    return okhttpResponse?.headers
}
