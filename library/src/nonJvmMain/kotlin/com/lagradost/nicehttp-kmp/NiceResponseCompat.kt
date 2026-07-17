package com.lagradost.nicehttp

internal actual typealias NiceResponseCompat = NiceResponse
internal actual fun NiceResponse.resolveOkHttpResponseCompat(): NiceResponseCompat? = this
