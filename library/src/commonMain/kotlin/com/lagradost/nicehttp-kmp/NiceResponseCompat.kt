package kotlin

import com.lagradost.nicehttp.INiceResponse

/** Parses the body as [T] using the configured [ResponseParser]. */
@Deprecated(
    message = "Will be removed once migration is complete. Add import com.lagradost.nicehttp.kmp.parsed instead.",
    level = DeprecationLevel.WARNING
)
suspend inline fun <reified T : Any> INiceResponse.parsed(): T =
    parser!!.parse(text(), T::class)

/** Same as [parsed] but returns null on failure. */
@Deprecated(
    message = "Will be removed once migration is complete. Add import com.lagradost.nicehttp.kmp.parsedSafe instead.",
    level = DeprecationLevel.WARNING
)
suspend inline fun <reified T : Any> INiceResponse.parsedSafe(): T? = try {
    parser?.parseSafe(text(), T::class)
} catch (e: Exception) {
    e.printStackTrace()
    null
}

/** Like [parsed] but without the size guard. */
@Deprecated(
    message = "Will be removed once migration is complete. Add import com.lagradost.nicehttp.kmp.parsedLarge instead.",
    level = DeprecationLevel.WARNING
)
suspend inline fun <reified T : Any> INiceResponse.parsedLarge(): T =
    parser!!.parse(textLarge(), T::class)

/** Like [parsedSafe] but without the size guard. */
@Deprecated(
    message = "Will be removed once migration is complete. Add import com.lagradost.nicehttp.kmp.parsedSafeLarge instead.",
    level = DeprecationLevel.WARNING
)
suspend inline fun <reified T : Any> INiceResponse.parsedSafeLarge(): T? = try {
    parser?.parseSafe(textLarge(), T::class)
} catch (e: Exception) {
    e.printStackTrace()
    null
}
