package com.lagradost.nicehttp

actual typealias NiceResponseBodyCompat = io.ktor.client.statement.HttpResponse
internal actual fun NiceResponse.resolveOkHttpResponseBodyCompat(): NiceResponseBodyCompat = response
