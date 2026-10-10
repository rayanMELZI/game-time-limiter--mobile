package com.rayan.gametimelimiter.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipInputStream

/**
 * Reads an ActionDash backup (.backup = zip with Room/SQLite databases) and rebuilds
 * daily screen time per app from its raw usage events.
 */
object ActionDashImport {
    /** A single session longer than this means a missing "app closed" event; it's cut off. */
    private const val MAX_SESSION_MS = 6 * 3600_000L

    data class Result(
        val days: Int,
        val from: LocalDate?,
        val to: LocalDate?,
        val hours: Double,
        val apps: Int,
        /** Daily limits set in ActionDash: package -> minutes. */
        val limits: Map<String, Int>,
    )

    suspend fun run(context: Context, uri: Uri, onProgress: (Float) -> Unit): Result = withContext(Dispatchers.IO) {
        val db = File(context.cacheDir, "actiondash_usage.db")
        var limitsXml: String? = null
        var foundEvents = false
        try {
            context.contentResolver.openInputStream(uri)!!.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        when (entry.name) {
                            "usage_events" -> {
                                db.outputStream().use { zip.copyTo(it) }
                                foundEvents = true
                            }
                            "usage_limits" -> limitsXml = zip.readBytes().decodeToString()
                        }
                    }
                }
            }
            if (!foundEvents) throw IllegalArgumentException("This isn't an ActionDash backup (no usage data inside).")
            onProgress(0.15f)

            val ignored = AppHistory.ignoredPackages(context)
            val history = mutableMapOf<String, MutableMap<String, Double>>()
            fun add(pkg: String, start: Long, end: Long) {
                // Home screens (also ones no longer installed, like an old launcher) aren't app usage.
                if (pkg in ignored || "launcher" in pkg || end <= start) return
                var a = start
                val b = minOf(end, start + MAX_SESSION_MS)
                // Split at day boundaries (which follow the "new day starts at" setting).
                while (a < b) {
                    val day = Store.dayInfoAt(a)
                    val e = minOf(b, day.nextReset.toInstant().toEpochMilli())
                    val apps = history.getOrPut(day.key) { mutableMapOf() }
                    apps[pkg] = (apps[pkg] ?: 0.0) + (e - a) / 1000.0
                    a = e
                }
            }

            SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
                val total = sql.rawQuery(
                    "SELECT COUNT(*) FROM UsageEventEntity WHERE type IN ('MOVE_TO_FOREGROUND','MOVE_TO_BACKGROUND','SCREEN_NON_INTERACTIVE')",
                    null,
                ).use { if (it.moveToFirst()) it.getLong(0) else 0L }.coerceAtLeast(1)
                sql.rawQuery(
                    "SELECT applicationId, type, timestamp FROM UsageEventEntity " +
                        "WHERE type IN ('MOVE_TO_FOREGROUND','MOVE_TO_BACKGROUND','SCREEN_NON_INTERACTIVE') ORDER BY timestamp, id",
                    null,
                ).use { c ->
                    var current: String? = null
                    var start = 0L
                    var n = 0L
                    while (c.moveToNext()) {
                        val pkg = c.getString(0)
                        val ts = c.getLong(2)
                        when (c.getString(1)) {
                            "MOVE_TO_FOREGROUND" -> {
                                current?.let { add(it, start, ts) }
                                current = pkg
                                start = ts
                            }
                            "MOVE_TO_BACKGROUND" -> if (pkg == current) {
                                add(pkg, start, ts)
                                current = null
                            }
                            else -> { // screen turned off
                                current?.let { add(it, start, ts) }
                                current = null
                            }
                        }
                        if (++n % 20_000 == 0L) onProgress(0.15f + 0.8f * n / total)
                    }
                }
            }

            AppHistory.merge(history)
            onProgress(1f)

            val days = history.keys.sorted()
            Result(
                days = days.size,
                from = days.firstOrNull()?.let(LocalDate::parse),
                to = days.lastOrNull()?.let(LocalDate::parse),
                hours = history.values.sumOf { it.values.sum() } / 3600,
                apps = history.values.flatMap { it.keys }.toSet().size,
                limits = parseLimits(limitsXml),
            )
        } finally {
            db.delete()
            File(db.path + "-journal").delete()
        }
    }

    /** `<long name="com.app" value="1800000" />` (milliseconds) -> package to minutes. */
    private fun parseLimits(xml: String?): Map<String, Int> =
        Regex("""<long name="([^"]+)" value="(\d+)"""").findAll(xml ?: "")
            .associate { it.groupValues[1] to (it.groupValues[2].toLong() / 60_000).toInt() }
            .filterValues { it in 1..24 * 60 }
}
