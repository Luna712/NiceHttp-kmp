package com.lagradost.nicehttp

expect class NiceHeadersCompat
internal expect fun NiceResponse.resolveOkHttpHeadersCompat(): NiceHeadersCompat?
