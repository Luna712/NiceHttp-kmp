package com.lagradost.nicehttp

actual typealias NiceRequestBodyCompat = RequestBody
internal actual fun NiceRequestBodyCompat.toRequestBody(): RequestBody = this
