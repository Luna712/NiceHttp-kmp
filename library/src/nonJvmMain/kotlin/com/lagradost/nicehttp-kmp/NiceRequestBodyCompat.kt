package com.lagradost.nicehttp

actual abstract class NiceRequestBodyCompat
internal actual fun NiceRequestBodyCompat.toRequestBody(): RequestBody =
    this as? RequestBody ?: throw UnsupportedOperationException(
        "NiceRequestBodyCompat must be a RequestBody on non-JVM platforms"
    )
