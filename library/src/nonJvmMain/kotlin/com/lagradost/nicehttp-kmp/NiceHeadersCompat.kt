package com.lagradost.nicehttp

actual typealias NiceHeadersCompat = NiceHeaders
internal actual fun NiceResponse.resolveOkHttpHeadersCompat(): NiceHeadersCompat = responseHeaders

