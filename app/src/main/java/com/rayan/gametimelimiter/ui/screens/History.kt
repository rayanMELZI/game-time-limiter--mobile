package com.rayan.gametimelimiter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayan.gametimelimiter.data.AppCatalog
import com.rayan.gametimelimiter.data.AppHistory
import com.rayan.gametimelimiter.data.Snapshot
import com.rayan.gametimelimiter.data.Store
import com.rayan.gametimelimiter.ui.components.AppAvatar
import com.rayan.gametimelimiter.ui.components.Card
import com.rayan.gametimelimiter.ui.fmtDuration
import com.rayan.gametimelimiter.ui.theme.C
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 0 = everything since the first recorded day. */
private val RANGES = listOf(7 to "7d", 30 to "30d", 90 to "90d", 365 to "1y", 0 to "All")

private data class Bucket(val label: String, val seconds: Double, val isToday: Boolean)

@Composable
fun History(snap: Snapshot, contentPadding: PaddingValues) {
    val context = LocalContext.current
    var range by rememberSaveable { mutableIntStateOf(7) }
    val today = snap.day.date
    // Recompute when the range changes or usage moves on (every 30 s of tracked time).
    val tick = AppHistory.version / 30 + (snap.rules.sumOf { it.usedSec } / 30).toInt()

    val model = remember(range, tick, snap.day.key) {
        val first = AppHistory.firstDay()
        val from = if (range == 0) minOf(first ?: today.minusDays(6), today.minusDays(6)) else today.minusDays(range - 1L)
        val days = ChronoUnit.DAYS.between(from, today).toInt() + 1
        val ruleHistory = Store.history(days).associateBy { it.date }
        // Per day: all-app screen time, or the limited apps' time for days before full tracking existed.
        val perDay = (0 until days).map { i ->
            val d = from.plusDays(i.toLong())
            val all = AppHistory.day(d).values.sum()
            val limited = ruleHistory[d]?.usage?.values?.sum() ?: 0.0
            d to maxOf(all, limited)
        }
        Triple(from, perDay, ruleHistory)
    }
    val (from, perDay, ruleHistory) = model
    val buckets = remember(model) { bucketize(perDay, today, range) }
    val total = perDay.sumOf { it.second }
    val max = maxOf(3600.0, buckets.maxOfOrNull { it.seconds } ?: 0.0)
    val top = remember(model) { AppHistory.topApps(from, today).take(10) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        if (range == 0) "Since ${from.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))}" else "Last ${RANGES.first { it.first == range }.second}",
                        color = C.Muted, fontSize = 13.sp,
                    )
                    Text("History", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(11.dp)).background(C.Surface).border(1.dp, C.Border, RoundedCornerShape(11.dp)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RANGES.forEach { (r, label) ->
                        Text(
                            label,
                            color = if (range == r) C.Text else C.Muted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (range == r) C.Surface3 else C.Surface)
                                .clickable { range = r }
                                .padding(vertical = 7.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }

        item {
            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    Summary("Total", fmtHours(total))
                    Summary("Daily average", fmtDuration(if (perDay.isEmpty()) 0.0 else total / perDay.size))
                }
                Row(
                    Modifier.fillMaxWidth().height(160.dp).padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (buckets.size > 20) 2.dp else 6.dp),
                ) {
                    buckets.forEach { b ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.weight(1f).fillMaxWidth().widthIn(max = 40.dp).clip(RoundedCornerShape(if (buckets.size > 20) 3.dp else 7.dp)).background(C.Surface2),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight((b.seconds / max).toFloat().coerceAtLeast(0.02f))
                                        .clip(RoundedCornerShape(if (buckets.size > 20) 3.dp else 7.dp))
                                        .background(Brush.verticalGradient(listOf(C.Accent, Color(0xFF5F50D8)))),
                                )
                            }
                        }
                    }
                }
                ChartLabels(buckets)
                Text(
                    when {
                        range in 1..30 -> "Screen time per day"
                        range in 31..120 -> "Average per day, by week"
                        else -> "Average per day, by month"
                    },
                    color = C.Faint, fontSize = 11.5.sp, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        if (snap.rules.isNotEmpty()) item {
            Card {
                Text("Limited apps", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                snap.rules.forEachIndexed { index, r ->
                    val days = perDay.map { (d, _) ->
                        val limited = ruleHistory[d]?.usage?.get(r.rule.id) ?: 0.0
                        val all = AppHistory.day(d).filterKeys { it in r.rule.packages }.values.sum()
                        maxOf(limited, all)
                    }
                    val sum = days.sum()
                    val overDays = days.count { r.limitSec > 0 && it >= r.limitSec }
                    val spark = bucketize(perDay.mapIndexed { i, (d, _) -> d to days[i] }, today, range).map { it.seconds }
                    val peak = maxOf(spark.maxOrNull() ?: 0.0, 1.0)
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppAvatar(r.rule.name, r.rule.packages.firstOrNull(), 34.dp)
                        Column(Modifier.weight(1f)) {
                            Text(r.rule.name, color = C.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${fmtHours(sum)} · limit hit $overDays×", color = C.Muted, fontSize = 12.5.sp)
                        }
                        Row(Modifier.weight(1f).height(36.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                            spark.forEach { s ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight((s / peak).toFloat().coerceAtLeast(0.1f))
                                        .alpha(if (s > 0) 1f else 0.25f)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(C.Accent),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card {
                Text("Top apps", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                if (top.isEmpty()) {
                    Text("No app usage recorded yet. It fills in as you use your phone — or import your ActionDash history in Settings.", color = C.Muted, fontSize = 13.sp)
                }
                val topMax = top.firstOrNull()?.second ?: 1.0
                top.forEach { (pkg, sec) ->
                    val label = remember(pkg) { AppCatalog.label(context, pkg) }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppAvatar(label, pkg, 32.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row {
                                Text(label, color = C.Text, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(fmtHours(sec), color = C.Muted, fontSize = 13.sp)
                            }
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.Surface2)) {
                                Box(Modifier.fillMaxWidth((sec / topMax).toFloat()).height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.Accent))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Labels placed under their bar's center; free to be wider than the bar. */
@Composable
private fun ChartLabels(buckets: List<Bucket>) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp).padding(top = 6.dp)) {
        val slot = maxWidth / buckets.size.coerceAtLeast(1)
        buckets.forEachIndexed { i, b ->
            if (b.label.isEmpty()) return@forEachIndexed
            Text(
                b.label,
                color = if (b.isToday) C.Text else C.Muted,
                fontSize = 10.5.sp,
                fontWeight = if (b.isToday) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .width(48.dp)
                    .offset(x = (slot * i + slot / 2 - 24.dp).coerceIn(0.dp, maxWidth - 48.dp)),
            )
        }
    }
}

/** Daily bars up to 30 days, weekly averages up to ~4 months, monthly averages beyond. */
private fun bucketize(perDay: List<Pair<LocalDate, Double>>, today: LocalDate, range: Int): List<Bucket> {
    val n = perDay.size
    return when {
        n <= 31 -> perDay.mapIndexed { i, (d, s) ->
            val label = if (n <= 7) d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3)
            else if (i % 5 == 0 || d == today) d.dayOfMonth.toString() else ""
            Bucket(label, s, d == today)
        }
        n <= 125 -> perDay.chunked(7).mapIndexed { i, week ->
            val d = week.first().first
            Bucket(if (i % 3 == 0) "${d.dayOfMonth}/${d.monthValue}" else "", week.sumOf { it.second } / week.size, week.any { it.first == today })
        }
        else -> perDay.groupBy { it.first.withDayOfMonth(1) }.entries.toList().let { months ->
            months.mapIndexed { i, (month, days) ->
                val name = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3)
                val label = when {
                    // Years only when showing several years.
                    months.size > 15 -> if (month.monthValue == 1) month.year.toString() else ""
                    i % 3 == 0 -> name
                    else -> ""
                }
                Bucket(label, days.sumOf { it.second } / days.size, days.any { it.first == today })
            }
        }
    }
}

/** "1,234h" for long totals, otherwise the usual "1h 05m". */
private fun fmtHours(sec: Double): String =
    if (sec >= 100 * 3600) "%,dh".format((sec / 3600).toLong()) else fmtDuration(sec)

@Composable
private fun Summary(label: String, value: String) {
    Column {
        Text(label, color = C.Muted, fontSize = 12.5.sp)
        Text(value, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
