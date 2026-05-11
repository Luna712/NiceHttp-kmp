package com.lagradost.nicehttp

actual typealias NiceTimeUnit = kotlin.time.DurationUnit
internal actual fun NiceTimeUnit.toDurationUnit(): kotlin.time.DurationUnit = this
