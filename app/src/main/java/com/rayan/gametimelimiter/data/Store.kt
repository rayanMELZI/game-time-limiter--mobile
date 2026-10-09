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
    /** (rule id, extra stage) whose "1 minute left" warning was shown */
    private val extraWarned = mutableSetOf<Pair<String, Int>>()

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

    private fun today(): DayInfo = dayAt(now())

    private fun dayAt(t: Long): DayInfo {
        val zone = ZoneId.systemDefault()
        val shifted = Instant.ofEpochMilli(t).atZone(zone).minusHours(data.settings.resetHour.toLong())
        val date = shifted.toLocalDate()
        val nextReset = date.plusDays(1).atStartOfDay(zone).plusHours(data.settings.resetHour.toLong())
        return DayInfo(date.toString(), date, shifted.dayOfWeek, nextReset)
    }

    fun resetLabel(day: DayInfo): String = day.nextReset.format(timeFmt)

    // ---------- Status ----------

    private fun statusOf(rule: Rule, day: DayInfo, now: Long): RuleStatus {
        val used = data.usage[day.key]?.get(rule.id) ?: 0.0
        val limit = rule.limitSec(day.weekday)
        val reached = rule.enabled && used >= limit
        val state = data.extra[rule.id]?.takeIf { it.day == day.key } ?: ExtraState(day.key)
        return RuleStatus(
            rule = rule,
            usedSec = used,
            limitSec = limit,
            running = running[rule.id] == true,
            reached = reached,
            locked = reached && rule.lockWhenReached,
            extra = extraInfo(state, now, rule.allowExtra),
        )
    }

    private fun extraInfo(state: ExtraState, now: Long, allowed: Boolean) = ExtraInfo(
        activeLeft = state.activeUntil?.let { max(0L, it - now) / 1000.0 },
        cooldownLeft = state.cooldownUntil?.let { max(0L, it - now) / 1000.0 },
        nextMinutes = if (allowed) EXTRA_STEPS_MIN.getOrNull(state.stage) else null,
        used = state.stage,
    )

    private fun buildSnapshot(): Snapshot {
        val now = now()
        val day = dayAt(now)
        val rules = data.rules.map { statusOf(it, day, now) }
        val busy = rules.any { it.locked || (it.running && it.rule.enabled) }
        return Snapshot(day, rules, data.settings, canStop = !data.settings.guardStop || !busy)
    }

    private fun publish() {
        _snapshot.value = buildSnapshot()
    }

    private fun isLocked(ruleId: String): Boolean {
        val now = now()
        val day = dayAt(now)
        return data.rules.find { it.id == ruleId }?.let { statusOf(it, day, now).locked } == true
    }

    private fun anyLocked(): Boolean {
        val now = now()
        val day = dayAt(now)
        return data.rules.any { statusOf(it, day, now).locked }
    }

    // ---------- Extra time ----------

    /**
     * Today's extra-time state for a rule. Ends sessions whose time is up and returns
     * `true`/`false` as second value when one just ended (`true`: no extra time left after it).
     */
    private fun extraTick(ruleId: String, day: String, now: Long): Pair<ExtraState, Boolean?> {
        var e = data.extra[ruleId]?.takeIf { it.day == day } ?: ExtraState(day)
        var ended: Boolean? = null
        val until = e.activeUntil
        if (until != null && now >= until) {
            val last = e.stage >= EXTRA_STEPS_MIN.size
            e = e.copy(activeUntil = null, cooldownUntil = if (last) null else until + EXTRA_COOLDOWN_MS)
            ended = last
        }
        if (e.cooldownUntil?.let { now >= it } == true) e = e.copy(cooldownUntil = null)
        if (e != data.extra[ruleId]) {
            data = data.copy(extra = data.extra + (ruleId to e))
            dirty = true
        }
        return e to ended
    }

    /** Starts the next extra session (5, then 2, then 1 minute). Returns its length, or an error message. */
    @Synchronized
    fun startExtra(ruleId: String): Result<Int> {
        val now = now()
        val day = dayAt(now)
        val rule = data.rules.find { it.id == ruleId } ?: return Result.failure(Exception("This limit no longer exists."))
        if (!rule.allowExtra) return Result.failure(Exception("Extra time is turned off for ${rule.name}."))
        if (!statusOf(rule, day, now).reached) return Result.failure(Exception("${rule.name} still has time left today."))
        val (state, _) = extraTick(ruleId, day.key, now)
        if (state.activeUntil != null) return Result.failure(Exception("Extra time is already running."))
        state.cooldownUntil?.let {
            val left = (it - now) / 1000
            return Result.failure(Exception("Next extra time in ${left / 60}:${(left % 60).toString().padStart(2, '0')}."))
        }
        val minutes = EXTRA_STEPS_MIN.getOrNull(state.stage)
            ?: return Result.failure(Exception("No extra time left today. ${rule.name} unlocks at ${resetLabel(day)}."))
        data = data.copy(extra = data.extra + (ruleId to state.copy(stage = state.stage + 1, activeUntil = now + minutes * 60_000L)))
        commit()
        return Result.success(minutes)
    }

    /** Sets the display name of the custom sound (the file itself is managed by the caller). */
    @Synchronized
    fun setSoundName(name: String?) {
        data = data.copy(settings = data.settings.copy(soundName = name))
        commit()
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
        val now = now()
        val day = dayAt(now)
        val resetAt = resetLabel(day)
        val alerts = mutableListOf<Alert>()
        var block: BlockRequest? = null
        val dayUsage = (data.usage[day.key] ?: emptyMap()).toMutableMap()
        running.clear()

        for (rule in data.rules.filter { it.enabled }) {
            val onScreen = foreground != null && foreground in rule.packages
            val limit = rule.limitSec(day.weekday)
            val (extra, extraEnded) = extraTick(rule.id, day.key, now)
            val inExtra = extra.activeUntil != null
            val nextExtra = if (rule.allowExtra) EXTRA_STEPS_MIN.getOrNull(extra.stage) else null

            val before = dayUsage[rule.id] ?: 0.0
            val wasUnder = before < limit
            val used = when {
                onScreen && wasUnder -> min(before + dt, limit)
                onScreen && inExtra -> before + dt // extra time still shows up in the history
                else -> before
            }
            if (used != before || rule.id !in dayUsage) {
                dayUsage[rule.id] = used
                dirty = true
            }
            if (onScreen) running[rule.id] = true

            if (extraEnded != null) {
                alerts += Alert(
                    Level.Danger,
                    "Extra time is over — ${rule.name}",
                    if (extraEnded) "That was the last extra time. It unlocks at $resetAt."
                    else "You can come back for ${nextExtra ?: 1} more minute${if (nextExtra == 1) "" else "s"} in 5 minutes.",
                )
            }

            if (onScreen && limit - used <= 0.0 && inExtra) {
                val left = (extra.activeUntil!! - now) / 1000
                val sessionMin = EXTRA_STEPS_MIN[(extra.stage - 1).coerceIn(0, EXTRA_STEPS_MIN.lastIndex)]
                if (left <= 60 && sessionMin > 1 && extraWarned.add(rule.id to extra.stage)) {
                    alerts += Alert(Level.Warning, "${rule.name} — 1 minute of extra time left", "Wrap up now. It will be locked when the extra time runs out.")
                }
                continue
            }

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
                // While extra time is still possible the app stays alive behind the lock screen.
                block = BlockRequest(rule.id, rule.name, foreground!!, resetAt, kill = nextExtra == null)
                if (wasUnder) {
                    alerts += Alert(
                        Level.Danger,
                        "Time's up — ${rule.name}",
                        if (nextExtra != null) "Your daily limit is reached. Need to finish something? You can come back for $nextExtra more minutes."
                        else "Your daily limit is reached. It unlocks at $resetAt.",
                    )
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
        // The sound is only changed through setSoundName.
        data = data.copy(settings = new.copy(resetHour = new.resetHour.coerceIn(0, 12), soundName = old.soundName))
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
