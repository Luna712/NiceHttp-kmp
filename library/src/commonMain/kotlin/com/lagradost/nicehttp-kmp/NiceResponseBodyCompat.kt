package com.lagradost.nicehttp

expect abstract class NiceResponseBodyCompat
internal expect fun NiceResponse.resolveOkHttpResponseBodyCompat(): NiceResponseBodyCompat
