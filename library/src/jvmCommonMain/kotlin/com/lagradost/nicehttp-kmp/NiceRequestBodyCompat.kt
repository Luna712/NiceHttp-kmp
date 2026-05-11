package com.lagradost.nicehttp

actual typealias NiceRequestBodyCompat = okhttp3.RequestBody
internal actual fun NiceRequestBodyCompat.toRequestBody(): RequestBody = toNiceRequestBody()
