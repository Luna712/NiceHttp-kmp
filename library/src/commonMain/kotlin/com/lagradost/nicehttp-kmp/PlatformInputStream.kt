package com.lagradost.nicehttp.kmp

expect abstract class PlatformInputStream {
    abstract fun read(): Int
    abstract fun read(buffer: ByteArray, offset: Int, length: Int): Int
    abstract fun close()
}

internal expect fun ByteArray.toPlatformInputStream(): PlatformInputStream
