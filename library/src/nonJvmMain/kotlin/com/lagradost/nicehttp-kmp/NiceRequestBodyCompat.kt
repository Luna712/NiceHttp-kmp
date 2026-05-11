package com.lagradost.nicehttp

actual class NiceRequestBodyCompat(val body: RequestBody)
internal actual fun NiceRequestBodyCompat.toRequestBody(): RequestBody = body
