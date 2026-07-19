package com.lagradost.nicehttp

expect class NiceResponseCompat
internal expect fun NiceResponse.resolveOkHttpResponseCompat(): NiceResponseCompat
