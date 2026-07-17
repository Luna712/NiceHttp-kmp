package com.lagradost.nicehttp

internal expect class NiceResponseCompat
internal expect fun NiceResponse.resolveOkHttpResponseCompat(): NiceResponseCompat?
