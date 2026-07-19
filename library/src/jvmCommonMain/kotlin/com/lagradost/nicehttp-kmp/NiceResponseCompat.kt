package com.lagradost.nicehttp

actual typealias NiceResponseCompat = okhttp3.Response
internal actual fun NiceResponse.resolveOkHttpResponseCompat(): NiceResponseCompat {
    // Exception should never really be given but included for source compatibility to make okhttpResponse non-nullable.
    val missingResponseError = "Missing okhttp response. Try removing okhttpResponse and using NiceResponse directly."
    val id = response.headers[NICEHTTP_OKHTTP_ID_HEADER] ?: throw IllegalStateException(missingResponseError)
    return OkHttpResponseCache.take(id) ?: throw IllegalStateException(missingResponseError)
}
