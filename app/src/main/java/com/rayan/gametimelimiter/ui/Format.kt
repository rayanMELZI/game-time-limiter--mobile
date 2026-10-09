package com.rayan.gametimelimiter.ui

import kotlin.math.max
import kotlin.math.roundToLong

private fun pad(n: Long) = n.toString().padStart(2, '0')

/** "1h 05m", "12m 30s", "45s" */
fun fmtDuration(sec: Double): String {
    val s = max(0L, sec.roundToLong())
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> "${h}h ${pad(m)}m"
        m > 0 -> "${m}m ${pad(s % 60)}s"
        else -> "${s}s"
    }
}

/** Compact form for limits: "2h", "1h 30m", "45m" */
fun fmtMinutes(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private val HUES = listOf(252f, 172f, 28f, 340f, 205f, 130f, 290f, 48f)

fun hueFor(text: String): Float {
    var h = 0L
    for (c in text) h = (h * 31 + c.code) and 0xFFFFFFFFL
    return HUES[(h % HUES.size).toInt()]
}

/** "4:05" countdown */
fun fmtCountdown(sec: Double): String {
    val s = max(0L, kotlin.math.ceil(sec).toLong())
    return "${s / 60}:${pad(s % 60)}"
}
