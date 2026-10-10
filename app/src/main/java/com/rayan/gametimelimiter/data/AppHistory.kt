package com.rayan.gametimelimiter.data

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * Screen time of every app per day (not only limited ones), including history imported from ActionDash.
 * Kept in its own file because it grows over the years and changes less often than data.json.
 */
object AppHistory {
    private const val SAVE_EVERY_MS = 5 * 60_000L
    private val serializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), Double.serializer()))
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var file: File
    /** day ("YYYY-MM-DD") -> package -> seconds */
    private val days = sortedMapOf<String, MutableMap<String, Double>>()
    private var ignored: Set<String> = emptySet()
    private var dirty = false
    private var lastSave = 0L
    @Volatile
    var version = 0
        private set

    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "app_history.json")
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()?.forEach { (day, apps) ->
            days[day] = apps.toMutableMap()
        }
        ignored = ignoredPackages(context)
    }

    /** Home screens and system UI aren't app usage. */
    fun ignoredPackages(context: Context): Set<String> {
        val home = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
        return home.toSet() + setOf("android", "com.android.systemui", context.packageName)
    }

    @Synchronized
    fun add(day: String, pkg: String, seconds: Double) {
        if (seconds <= 0 || pkg in ignored) return
        val apps = days.getOrPut(day) { mutableMapOf() }
        apps[pkg] = (apps[pkg] ?: 0.0) + seconds
        dirty = true
        version++
    }

    /** Adds imported days; for a day and app already present the larger value wins, so importing twice is harmless. */
    @Synchronized
    fun merge(imported: Map<String, Map<String, Double>>) {
        for ((day, apps) in imported) {
            val target = days.getOrPut(day) { mutableMapOf() }
            for ((pkg, sec) in apps) target[pkg] = maxOf(target[pkg] ?: 0.0, sec)
        }
        dirty = true
        version++
        save()
    }

    @Synchronized
    fun saveIfDue() {
        if (dirty && SystemClock.elapsedRealtime() - lastSave >= SAVE_EVERY_MS) save()
    }

    @Synchronized
    fun save() {
        if (!::file.isInitialized) return
        runCatching {
            val tmp = File(file.parentFile, "app_history.json.tmp")
            tmp.writeText(json.encodeToString(serializer, days))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            dirty = false
            lastSave = SystemClock.elapsedRealtime()
        }
    }

    @Synchronized
    fun day(date: LocalDate): Map<String, Double> = days[date.toString()]?.toMap() ?: emptyMap()

    @Synchronized
    fun firstDay(): LocalDate? = days.keys.firstOrNull()?.let(LocalDate::parse)

    /** Seconds per app over [from]..[to] (inclusive), most used first. */
    @Synchronized
    fun topApps(from: LocalDate, to: LocalDate): List<Pair<String, Double>> {
        val totals = mutableMapOf<String, Double>()
        days.subMap(from.toString(), to.plusDays(1).toString()).values.forEach { apps ->
            apps.forEach { (pkg, sec) -> totals[pkg] = (totals[pkg] ?: 0.0) + sec }
        }
        return totals.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
