package com.lagradost.nicehttp

actual typealias NiceResponseBodyCompat = okhttp3.ResponseBody
internal actual fun NiceResponse.resolveOkHttpResponseBodyCompat(): NiceResponseBodyCompat? {
    return okhttpResponse?.body
}
