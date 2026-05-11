package com.lagradost.nicehttp

/**
 * Platform-agnostic file descriptor used in multipart uploads.
 *
 * On JVM/Android you can use the [NiceFile(name, file)] constructor that accepts
 * a [PlatformFile] (which is `java.io.File` on JVM/Android, or a [ByteArray]-based
 * alternative on other targets).
 *
 * For simple key/value form fields just use [NiceFile(name, value)].
 */
class NiceFile(
    /** Form field name */
    val name: String,
    /** File name sent in the Content-Disposition header, or the plain string value */
    val fileName: String,
    /** Raw bytes to upload; null for plain form fields */
    val bytes: ByteArray? = null,
    /** MIME type, e.g. "image/png". Null lets the engine decide. */
    val fileType: String? = null,
) {
    /** Convenience: plain string form field */
    constructor(name: String, value: String) : this(name, value, null, null)
}

/** Convenience extension: convert a Map of simple string fields to [NiceFile] list. */
fun Map<String, String>.toNiceFiles(): List<NiceFile> = map { NiceFile(it.key, it.value) }
