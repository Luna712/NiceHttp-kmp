package com.lagradost.nicehttp

internal actual typealias NiceResponseCompat = okhttp3.Response
internal actual fun NiceResponse.resolveOkHttpResponseCompat(): NiceResponseCompat? {
    val id = response.headers[NICEHTTP_OKHTTP_ID_HEADER] ?: return null
    return OkHttpResponseCache.take(id)
}
