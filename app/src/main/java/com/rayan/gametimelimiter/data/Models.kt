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
    val enabled: Boolean = true,
) {
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
)

@Serializable
data class AppData(
    val rules: List<Rule> = emptyList(),
    val settings: Settings = Settings(),
    /** day ("YYYY-MM-DD") -> rule id -> seconds used */
    val usage: Map<String, Map<String, Double>> = emptyMap(),
    /** Latest trusted timestamp ever seen; time never goes backwards past it. */
    val lastSeen: Long = 0,
)

data class DayInfo(val key: String, val date: LocalDate, val weekday: DayOfWeek, val nextReset: ZonedDateTime)

data class RuleStatus(
    val rule: Rule,
    val usedSec: Double,
    val limitSec: Double,
    /** One of the rule's apps is on screen right now. */
    val running: Boolean,
    val reached: Boolean,
    val locked: Boolean,
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

data class BlockRequest(val ruleName: String, val pkg: String, val resetAt: String)
