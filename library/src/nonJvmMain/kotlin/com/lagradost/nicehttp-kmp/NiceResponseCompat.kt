package com.lagradost.nicehttp

actual typealias NiceResponseCompat = NiceResponse
internal actual fun NiceResponse.resolveOkHttpResponseCompat(): NiceResponseCompat? = this
