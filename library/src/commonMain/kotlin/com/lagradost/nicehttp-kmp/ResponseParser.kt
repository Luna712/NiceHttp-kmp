package com.lagradost.nicehttp

import kotlin.reflect.KClass

/**
 * Implement this to plug in your own JSON parser (e.g. kotlinx-serialization, Moshi, Jackson).
 * A default [SerializationResponseParser] backed by kotlinx-serialization is provided.
 */
interface ResponseParser {
    /** Parse [text] into an instance of [kClass]. May throw on failure. */
    fun <T : Any> parse(text: String, kClass: KClass<T>): T

    /** Same as [parse] but returns null on failure instead of throwing. */
    fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T?

    /** Serialise [obj] to a JSON string (used when passing `json = someObject` to requests). */
    fun writeValueAsString(obj: Any): String
}

/**
 * Wraps a raw JSON string so it is sent as `application/json` even though it is already
 * serialised, e.g. `post(url, json = JsonAsString(myRawJson))`.
 */
data class JsonAsString(val string: String)

/** Common content-type constants used when building request bodies. */
object RequestBodyTypes {
    const val JSON = "application/json;charset=utf-8"
    const val TEXT = "text/plain;charset=utf-8"
}
