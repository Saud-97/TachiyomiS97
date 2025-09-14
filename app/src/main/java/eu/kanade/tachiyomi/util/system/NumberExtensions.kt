package eu.kanade.tachiyomi.util.system

import kotlin.math.roundToLong

fun Double.roundToTwoDecimal(): Double = (this * 100.0).roundToLong() / 100.0
