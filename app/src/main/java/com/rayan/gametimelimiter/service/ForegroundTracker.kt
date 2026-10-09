package com.rayan.gametimelimiter.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** Follows which app is on screen by reading the system usage events (needs Usage access). */
class ForegroundTracker(context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    // Look back far enough to know what's on screen when the service (re)starts.
    private var lastQuery = System.currentTimeMillis() - 6 * 60 * 60 * 1000L
    private var current: String? = null
    private val event = UsageEvents.Event()

    @Suppress("DEPRECATION") // MOVE_TO_* have the same values as ACTIVITY_RESUMED/PAUSED and work on API 26+.
    fun current(): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQuery, now) ?: return current
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> current = event.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (event.packageName == current) current = null
            }
        }
        lastQuery = now
        return current
    }
}
