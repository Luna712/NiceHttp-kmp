package com.lagradost.nicehttp.kmp

actual open class PlatformInputStream internal constructor(private val data: ByteArray) {
    private var position = 0
    actual fun read(): Int = if (position >= data.size) -1 else data[position++].toInt() and 0xFF
    actual fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= data.size) return -1
        val count = minOf(length, data.size - position)
        data.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }
    actual fun close() = Unit
}

internal actual fun ByteArray.toPlatformInputStream(): PlatformInputStream = PlatformInputStream(this)
