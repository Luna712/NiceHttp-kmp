package com.lagradost.nicehttp

expect class PlatformFile
internal expect fun PlatformFile.toBytes(): ByteArray
internal expect fun PlatformFile.toFileName(): String

