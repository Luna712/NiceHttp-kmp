package com.lagradost.nicehttp

expect class NiceResponseBodyCompat
internal expect fun NiceResponse.resolveOkHttpResponseBodyCompat(): NiceResponseBodyCompat?
