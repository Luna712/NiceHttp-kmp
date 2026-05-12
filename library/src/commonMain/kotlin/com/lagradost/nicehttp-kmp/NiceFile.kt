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

    @Deprecated(
        "Passing a File directly is deprecated because it is inconsistent across platforms. " +
            "Read the file bytes yourself and use NiceFile(name, fileName, bytes, fileType) directly.",
        ReplaceWith("NiceFile(name, fileName, bytes, fileType)"),
        DeprecationLevel.WARNING,
    )
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

    @Deprecated(
        "Passing a File directly is deprecated because it is inconsistent across platforms. " +
            "Read the file bytes yourself and use NiceFile(name, fileName, bytes) directly.",
        ReplaceWith("NiceFile(name, fileName, bytes)"),
        DeprecationLevel.WARNING,
    )
    constructor(name: String, file: PlatformFile) : this(name, file, null)

    @Deprecated(
        "Passing a File directly is deprecated because it is inconsistent across platforms. " +
            "Read the file bytes yourself and use NiceFile(fileName, fileName, bytes) directly.",
        ReplaceWith("NiceFile(fileName, fileName, bytes)"),
        DeprecationLevel.WARNING,
    )
    constructor(file: PlatformFile) : this(file.toFileName(), file, null)
}

/** Convenience extension: convert a Map of simple string fields to [NiceFile] list. */
fun Map<String, String>.toNiceFiles(): List<NiceFile> = map { NiceFile(it.key, it.value) }
