package com.lagradost.nicehttp

/**
 * Platform-agnostic file descriptor used in multipart uploads.
 * Replaces the original NiceHttp NiceFile which used java.io.File directly.
 *
 * On JVM/Android [PlatformFile] is [java.io.File] so all original constructors
 * work unchanged. On other platforms construct with raw bytes instead.
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
    /** Plain string form field */
    constructor(name: String, value: String) : this(name, value, null, null)

    /**
     * Back-compat with original NiceFile(name, file, fileType).
     * On JVM/Android [PlatformFile] is [java.io.File].
     */
    constructor(
        name: String,
        file: PlatformFile,
        fileType: String? = null,
    ) : this(
        name = name,
        fileName = file.toFileName(),
        bytes = file.toBytes(),
        fileType = fileType,
    )

    /**
     * Back-compat with original NiceFile(name, file).
     * On JVM/Android [PlatformFile] is [java.io.File].
     */
    constructor(name: String, file: PlatformFile) : this(name, file, null)

    /**
     * Back-compat with original NiceFile(file).
     * Uses the file name as the form field name.
     * On JVM/Android [PlatformFile] is [java.io.File].
     */
    constructor(file: PlatformFile) : this(file.toFileName(), file, null)
}

/** Convenience extension: convert a Map of simple string fields to [NiceFile] list. */
fun Map<String, String>.toNiceFiles(): List<NiceFile> = map { NiceFile(it.key, it.value) }
