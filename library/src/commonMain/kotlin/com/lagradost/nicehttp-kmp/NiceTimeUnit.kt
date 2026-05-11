package com.lagradost.nicehttp

expect class NiceTimeUnit {
    companion object {
        val NANOSECONDS: NiceTimeUnit
        val MICROSECONDS: NiceTimeUnit
        val MILLISECONDS: NiceTimeUnit
        val SECONDS: NiceTimeUnit
        val MINUTES: NiceTimeUnit
        val HOURS: NiceTimeUnit
        val DAYS: NiceTimeUnit
    }
}

internal expect fun NiceTimeUnit.toDurationUnit(): kotlin.time.DurationUnit
