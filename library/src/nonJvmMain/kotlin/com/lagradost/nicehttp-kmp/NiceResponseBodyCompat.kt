package com.lagradost.nicehttp

actual typealias NiceResponseBodyCompat = ResponseBody
internal actual fun NiceResponse.resolveOkHttpResponseBodyCompat(): NiceResponseBodyCompat? = runBlockingCompat { body() }
