package com.lagradost.nicehttp

import okhttp3.ResponseBody.Companion.toResponseBody

actual typealias NiceResponseBodyCompat = okhttp3.ResponseBody
internal actual fun NiceResponse.resolveOkHttpResponseBodyCompat(): NiceResponseBodyCompat {
    @Suppress("DEPRECATION")
    return okhttpResponse?.body ?: "".toResponseBody()
}
