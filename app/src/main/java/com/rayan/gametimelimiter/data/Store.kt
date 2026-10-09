package com.rayan.gametimelimiter.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Single source of truth: rules, settings, usage and the enforcement logic. */
object Store {
    private const val HISTORY_DAYS = 60L
    /** Wall-clock jumps larger than this while the app runs are ignored. */
    private const val CLOCK_TOLERANCE_MS = 120_000L
    private const val SAVE_EVERY_MS = 10_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private lateinit var file: File
    private var data = AppData()
    private var initialized = false
    private var dirty = false
    private var lastSave = 0L

    private var anchorWall = 0L
    private var anchorElapsed = 0L

    /** rule id -> one of its apps is on screen */
    private val running = mutableMapOf<String, Boolean>()
    /** rule id -> (day, warnings already shown that day) */
    private val fired = mutableMapOf<String, Pair<String, MutableSet<Int>>>()

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        file = File(context.filesDir, "data.json")
        data = runCatching { json.decodeFromString<AppData>(file.readText()) }.getOrDefault(AppData())
        anchorWall = System.currentTimeMillis()
        anchorElapsed = SystemClock.elapsedRealtime()
        initialized = true
        publish()
    }

    val settings: Settings @Synchronized get() = data.settings

    // ---------- Time ----------

    /** Trusted current time: ignores clock jumps and never goes earlier than anything seen before. */
    private fun now(): Long {
        val wall = System.currentTimeMillis()
        val mono = anchorWall + (SystemClock.elapsedRealtime() - anchorElapsed)
        val t = if (abs(wall - mono) <= CLOCK_TOLERANCE_MS) {
            anchorWall = wall
            anchorElapsed = SystemClock.elapsedRealtime()
            wall
        } else mono
        val trusted = max(t, data.lastSeen)
        data = data.copy(lastSeen = trusted)
        return trusted
    }

    private fun today(): DayInfo {
        val zone = ZoneId.systemDefault()
        val shifted = Instant.ofEpochMilli(now()).atZone(zone).minusHours(data.settings.resetHour.toLong())
        val date = shifted.toLocalDate()
        val nextReset = date.plusDays(1).atStartOfDay(zone).plusHours(data.settings.resetHour.toLong())
        return DayInfo(date.toString(), date, shifted.dayOfWeek, nextReset)
    }

    fun resetLabel(day: DayInfo): String = day.nextReset.format(timeFmt)

    // ---------- Status ----------

    private fun statusOf(rule: Rule, day: DayInfo): RuleStatus {
        val used = data.usage[day.key]?.get(rule.id) ?: 0.0
        val limit = rule.limitSec(day.weekday)
        val reached = rule.enabled && used >= limit
        return RuleStatus(
            rule = rule,
            usedSec = used,
            limitSec = limit,
            running = running[rule.id] == true,
            reached = reached,
            locked = reached && rule.lockWhenReached,
        )
    }

    private fun buildSnapshot(): Snapshot {
        val day = today()
        val rules = data.rules.map { statusOf(it, day) }
        val busy = rules.any { it.locked || (it.running && it.rule.enabled) }
        return Snapshot(day, rules, data.settings, canStop = !data.settings.guardStop || !busy)
    }

    private fun publish() {
        _snapshot.value = buildSnapshot()
    }

    private fun isLocked(ruleId: String): Boolean {
        val day = today()
        return data.rules.find { it.id == ruleId }?.let { statusOf(it, day).locked } == true
    }

    private fun anyLocked(): Boolean {
        val day = today()
        return data.rules.any { statusOf(it, day).locked }
    }

    private fun lockedError(name: String) =
        "\"$name\" is locked until ${resetLabel(today())}. Its limit can't be changed today."

    // ---------- Enforcement ----------

    class TickResult(val alerts: List<Alert>, val block: BlockRequest?)

    /**
     * Called every second by the limiter service with the app currently on screen.
     * Adds play time, decides which warnings to show and whether the app must be blocked.
     */
    @Synchronized
    fun tick(foreground: String?, dt: Double): TickResult {
        val day = today()
        val resetAt = resetLabel(day)
        val alerts = mutableListOf<Alert>()
        var block: BlockRequest? = null
        val dayUsage = (data.usage[day.key] ?: emptyMap()).toMutableMap()
        running.clear()

        for (rule in data.rules.filter { it.enabled }) {
            val onScreen = foreground != null && foreground in rule.packages
            val limit = rule.limitSec(day.weekday)
            val before = dayUsage[rule.id] ?: 0.0
            val wasUnder = before < limit
            val used = if (onScreen && wasUnder) min(before + dt, limit) else before
            if (used != before || rule.id !in dayUsage) {
                dayUsage[rule.id] = used
                dirty = true
            }
            if (onScreen) running[rule.id] = true

            // Re-arm warnings when the limit went up, and reset them on a new day.
            val remaining = limit - used
            var entry = fired[rule.id]
            if (entry == null || entry.first != day.key) {
                entry = day.key to mutableSetOf()
                fired[rule.id] = entry
            }
            val shown = entry.second
            shown.retainAll { remaining <= it * 60.0 }

            if (!onScreen) continue

            if (remaining <= 0.0) {
                block = BlockRequest(rule.name, foreground!!, resetAt)
                if (wasUnder) {
                    alerts += Alert(Level.Danger, "Time's up — ${rule.name}", "Your daily limit is reached. It unlocks at $resetAt.")
                }
                continue
            }

            // Only show the most urgent warning that's due; mark the others as shown.
            val due = rule.warningsMin.filter { remaining <= it * 60.0 && it !in shown }
            if (due.isNotEmpty()) {
                shown += due
                alerts += Alert(
                    Level.Warning,
                    "${rule.name} — ${fmtRemaining(remaining)} left",
                    "Save your progress now. It will be closed when the time runs out.",
                )
            }
        }

        data = data.copy(usage = data.usage + (day.key to dayUsage))
        if (dirty && SystemClock.elapsedRealtime() - lastSave >= SAVE_EVERY_MS) save()
        publish()
        return TickResult(alerts, block)
    }

    private fun fmtRemaining(sec: Double): String {
        val s = ceil(sec).toLong()
        return if (s >= 60) {
            val m = (s + 59) / 60
            "$m minute${if (m == 1L) "" else "s"}"
        } else "$s seconds"
    }

    // ---------- Mutations (return an error message, or null on success) ----------

    @Synchronized
    fun saveRule(input: Rule): String? {
        val rule = input.copy(
            name = input.name.trim(),
            packages = input.packages.distinct(),
            warningsMin = input.warningsMin.distinct().sortedDescending(),
        )
        if (rule.name.isEmpty()) return "Give this limit a name."
        if (rule.packages.isEmpty()) return "Pick at least one app to limit."
        if (rule.dailyLimitMin > 24 * 60 || (rule.weekendLimitMin ?: 0) > 24 * 60) return "A daily limit can't be longer than 24 hours."

        data = if (rule.id.isEmpty()) {
            data.copy(rules = data.rules + rule.copy(id = "r${System.currentTimeMillis()}"))
        } else {
            if (isLocked(rule.id)) return lockedError(rule.name)
            if (data.rules.none { it.id == rule.id }) return "This limit no longer exists."
            data.copy(rules = data.rules.map { if (it.id == rule.id) rule else it })
        }
        commit()
        return null
    }

    @Synchronized
    fun deleteRule(id: String): String? {
        val rule = data.rules.find { it.id == id } ?: return null
        if (isLocked(id)) return lockedError(rule.name)
        data = data.copy(rules = data.rules.filter { it.id != id })
        commit()
        return null
    }

    @Synchronized
    fun setRuleEnabled(id: String, enabled: Boolean): String? {
        val rule = data.rules.find { it.id == id } ?: return null
        if (isLocked(id)) return lockedError(rule.name)
        data = data.copy(rules = data.rules.map { if (it.id == id) it.copy(enabled = enabled) else it })
        commit()
        return null
    }

    @Synchronized
    fun saveSettings(new: Settings): String? {
        val old = data.settings
        val protectedChanged = old.resetHour != new.resetHour || (old.guardStop && !new.guardStop)
        if (protectedChanged && anyLocked()) {
            return "The day start and stop protection can't be changed while a limit is locked."
        }
        data = data.copy(settings = new.copy(resetHour = new.resetHour.coerceIn(0, 12)))
        commit()
        return null
    }

    /** Stopping the limiter is refused while protection applies. */
    @Synchronized
    fun setLimiterOn(on: Boolean): String? {
        if (!on && !buildSnapshot().canStop) return "Stop protection is on while a limited app is open or locked."
        data = data.copy(settings = data.settings.copy(limiterOn = on))
        if (!on) running.clear()
        commit()
        return null
    }

    @Synchronized
    fun history(days: Int): List<DayUsage> {
        val today = today().date
        return (days - 1 downTo 0).map { i ->
            val d = today.minusDays(i.toLong())
            DayUsage(d, data.usage[d.toString()] ?: emptyMap())
        }
    }

    private fun commit() {
        save()
        publish()
    }

    @Synchronized
    fun save() {
        val cutoff = today().date.minusDays(HISTORY_DAYS).toString()
        data = data.copy(usage = data.usage.filterKeys { it >= cutoff })
        runCatching {
            val tmp = File(file.parentFile, "data.json.tmp")
            tmp.writeText(json.encodeToString(AppData.serializer(), data))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            dirty = false
            lastSave = SystemClock.elapsedRealtime()
        }
    }
}
