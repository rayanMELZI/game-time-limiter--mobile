package com.rayan.gametimelimiter.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

@Serializable
data class Rule(
    val id: String = "",
    val name: String,
    /** Package names of the limited apps, e.g. `com.supercell.clashroyale`. */
    val packages: List<String>,
    val dailyLimitMin: Int,
    val weekendLimitMin: Int? = null,
    val warningsMin: List<Int> = listOf(10, 5, 1),
    /** Once the limit is reached the rule can't be edited, paused or removed until the next day. */
    val lockWhenReached: Boolean = true,
    /** Super strict: lock from the first (biggest) warning instead of at the limit. */
    val lockEarly: Boolean = false,
    val enabled: Boolean = true,
    /** After the limit, allow short extra sessions (see EXTRA_STEPS_MIN). */
    val allowExtra: Boolean = true,
) {
    /** Seconds before the limit at which a super strict rule locks (its biggest warning). */
    val earlyLockSec: Double get() = (warningsMin.maxOrNull() ?: 0) * 60.0

    fun limitSec(day: DayOfWeek): Double {
        val weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
        val min = if (weekend && weekendLimitMin != null) weekendLimitMin else dailyLimitMin
        return min * 60.0
    }
}

@Serializable
data class Settings(
    /** Hour (0-12) at which a new day starts. Using an app past midnight still counts as "today". */
    val resetHour: Int = 4,
    val sound: Boolean = true,
    val overlay: Boolean = true,
    val notifications: Boolean = true,
    /** Refuse to stop the limiter while a limited app is open or a rule is locked. */
    val guardStop: Boolean = true,
    /** False once the user stopped the limiter from the settings. */
    val limiterOn: Boolean = true,
    /** Display name of the custom warning sound (stored as files/custom_sound), null = built-in beep. */
    val soundName: String? = null,
    /** Download and install new versions automatically (when no limited app is on screen). */
    val autoUpdate: Boolean = true,
)

@Serializable
data class AppData(
    val rules: List<Rule> = emptyList(),
    val settings: Settings = Settings(),
    /** day ("YYYY-MM-DD") -> rule id -> seconds used */
    val usage: Map<String, Map<String, Double>> = emptyMap(),
    /** Latest trusted timestamp ever seen; time never goes backwards past it. */
    val lastSeen: Long = 0,
    /** rule id -> extra time used today */
    val extra: Map<String, ExtraState> = emptyMap(),
)

/** Extra time after the limit: 5, then 2, then 1 minute, each followed by a cooldown. */
val EXTRA_STEPS_MIN = listOf(5, 2, 1)
const val EXTRA_COOLDOWN_MS = 5 * 60_000L

@Serializable
data class ExtraState(
    val day: String = "",
    /** Number of extra sessions started today. */
    val stage: Int = 0,
    val activeUntil: Long? = null,
    val cooldownUntil: Long? = null,
)

data class ExtraInfo(
    /** Seconds left in the running extra session. */
    val activeLeft: Double?,
    /** Seconds until the next extra session can start. */
    val cooldownLeft: Double?,
    /** Length of the next extra session, if any is left. */
    val nextMinutes: Int?,
    val used: Int,
) {
    val running get() = activeLeft != null
    val canStart get() = activeLeft == null && cooldownLeft == null && nextMinutes != null
}

data class DayInfo(val key: String, val date: LocalDate, val weekday: DayOfWeek, val nextReset: ZonedDateTime)

data class RuleStatus(
    val rule: Rule,
    val usedSec: Double,
    val limitSec: Double,
    /** One of the rule's apps is on screen right now. */
    val running: Boolean,
    val reached: Boolean,
    val locked: Boolean,
    /** Locked by super strict mode before the limit was reached. */
    val lockedEarly: Boolean,
    val extra: ExtraInfo,
)

data class Snapshot(
    val day: DayInfo,
    val rules: List<RuleStatus>,
    val settings: Settings,
    val canStop: Boolean,
)

data class DayUsage(val date: LocalDate, val usage: Map<String, Double>)

enum class Level { Info, Warning, Danger }

data class Alert(val level: Level, val title: String, val body: String)

/** [kill]: no extra time can follow, so the app is closed for the day. */
data class BlockRequest(val ruleId: String, val ruleName: String, val pkg: String, val resetAt: String, val kill: Boolean)
