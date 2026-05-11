package com.lagradost.nicehttp

actual enum class NiceTimeUnit {
    NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS
}

internal actual fun NiceTimeUnit.toDurationUnit(): kotlin.time.DurationUnit = when (this) {
    NiceTimeUnit.NANOSECONDS  -> kotlin.time.DurationUnit.NANOSECONDS
    NiceTimeUnit.MICROSECONDS -> kotlin.time.DurationUnit.MICROSECONDS
    NiceTimeUnit.MILLISECONDS -> kotlin.time.DurationUnit.MILLISECONDS
    NiceTimeUnit.SECONDS      -> kotlin.time.DurationUnit.SECONDS
    NiceTimeUnit.MINUTES      -> kotlin.time.DurationUnit.MINUTES
    NiceTimeUnit.HOURS        -> kotlin.time.DurationUnit.HOURS
    NiceTimeUnit.DAYS         -> kotlin.time.DurationUnit.DAYS
}
