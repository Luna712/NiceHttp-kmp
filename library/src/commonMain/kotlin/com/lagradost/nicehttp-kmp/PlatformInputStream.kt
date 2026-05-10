package com.lagradost.nicehttp.kmp

expect class PlatformInputStream
internal expect fun ByteArray.toPlatformInputStream(): PlatformInputStream
