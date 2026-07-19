package com.lagradost.nicehttp

actual typealias NiceResponseBodyCompat = okhttp3.ResponseBody
internal actual fun NiceResponse.resolveOkHttpResponseBodyCompat(): NiceResponseBodyCompat {
    @Suppress("DEPRECATION")
    return okhttpResponse.body
}
