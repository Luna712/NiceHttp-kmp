package com.lagradost.nicehttp.kmp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

/**
 * A [ResponseParser] implementation backed by `kotlinx-serialization`.
 *
 * Supports any class annotated with `@Serializable`.
 *
 * Example usage:
 * ```kotlin
 * val requests = Requests(responseParser = SerializationResponseParser())
 *
 * @Serializable data class Repo(val name: String, val stargazers_count: Int)
 *
 * val repo = requests.get("https://api.github.com/repos/Kotlin/kotlinx.coroutines")
 *                    .parsed<Repo>()
 * ```
 *
 * You can supply a custom [Json] instance if you need non-default settings:
 * ```kotlin
 * SerializationResponseParser(Json { ignoreUnknownKeys = true })
 * ```
 */
@ExperimentalSerializationApi
class SerializationResponseParser(
    val json: Json = Json { ignoreUnknownKeys = true }
) : ResponseParser {

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        val serializer = json.serializersModule.getContextual(kClass)
            ?: throw IllegalArgumentException(
                "No serializer found for $kClass. Make sure it is @Serializable."
            )
        return json.decodeFromString(serializer, text)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? = try {
        val serializer = json.serializersModule.getContextual(kClass) ?: return null
        json.decodeFromString(serializer, text)
    } catch (_: Exception) {
        null
    }

    override fun writeValueAsString(obj: Any): String =
        json.encodeToString(JsonElement.serializer(), json.parseToJsonElement(obj.toString()))
}
