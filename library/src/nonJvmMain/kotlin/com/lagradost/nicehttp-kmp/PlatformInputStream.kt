package com.lagradost.nicehttp

actual abstract class PlatformInputStream {
    actual abstract fun read(): Int
    actual open fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
    actual open fun close() = Unit
}

internal actual fun ByteArray.toPlatformInputStream(): PlatformInputStream =
    object : PlatformInputStream() {
        private var position = 0
        override fun read(): Int =
            if (position >= size) -1 else this@toPlatformInputStream[position++].toInt() and 0xFF
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= size) return -1
            val count = minOf(length, size - position)
            copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }
