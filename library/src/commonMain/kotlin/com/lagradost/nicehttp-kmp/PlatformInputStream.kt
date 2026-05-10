package com.lagradost.nicehttp.kmp

expect open class PlatformInputStream {
    fun read(): Int
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
    fun close()
}

internal expect fun ByteArray.toPlatformInputStream(): PlatformInputStream
