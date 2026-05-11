package com.lagradost.nicehttp

expect abstract class NiceRequestBodyCompat
internal expect fun NiceRequestBodyCompat.toRequestBody(): RequestBody
