package com.rayan.gametimelimiter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayan.gametimelimiter.data.Snapshot
import com.rayan.gametimelimiter.data.Store
import com.rayan.gametimelimiter.ui.components.AppAvatar
import com.rayan.gametimelimiter.ui.components.Card
import com.rayan.gametimelimiter.ui.fmtDuration
import com.rayan.gametimelimiter.ui.theme.C
import java.time.format.TextStyle
import java.util.Locale

private val RANGES = listOf(7, 14, 30)

@Composable
fun History(snap: Snapshot, contentPadding: PaddingValues) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    // Refresh when the range changes or today's usage moves on (every 30s of play).
    val todayTick = (snap.rules.sumOf { it.usedSec } / 30).toInt()
    val history = remember(days, todayTick, snap.day.key) { Store.history(days) }
    val totals = history.map { d -> d.usage.values.sum() }
    val max = maxOf(3600.0, totals.maxOrNull() ?: 0.0)
    val sum = totals.sum()

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Last $days days", color = C.Muted, fontSize = 13.sp)
                    Text("History", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(11.dp)).background(C.Surface).border(1.dp, C.Border, RoundedCornerShape(11.dp)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RANGES.forEach { r ->
                        Text(
                            "${r}d",
                            color = if (days == r) C.Text else C.Muted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (days == r) C.Surface3 else C.Surface)
                                .clickable { days = r }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        item {
            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    Summary("Total", fmtDuration(sum))
                    Summary("Daily average", fmtDuration(if (history.isEmpty()) 0.0 else sum / history.size))
                }
                Row(
                    Modifier.fillMaxWidth().height(180.dp).padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (days > 14) 3.dp else 6.dp),
                ) {
                    history.forEachIndexed { i, d ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.weight(1f).fillMaxWidth().widthIn(max = 40.dp).clip(RoundedCornerShape(7.dp)).background(C.Surface2),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight((totals[i] / max).toFloat().coerceAtLeast(0.02f))
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Brush.verticalGradient(listOf(C.Accent, androidx.compose.ui.graphics.Color(0xFF5F50D8)))),
                                )
                            }
                            val label = if (days <= 14) d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3) else d.date.dayOfMonth.toString()
                            Text(
                                if (days > 14 && i % 3 != 0) "" else label,
                                color = if (d.date == snap.day.date) C.Text else C.Muted,
                                fontSize = 11.sp,
                                fontWeight = if (d.date == snap.day.date) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Card {
                Text("Per app", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                if (snap.rules.isEmpty()) Text("No apps limited yet.", color = C.Muted)
                snap.rules.forEachIndexed { index, r ->
                    val perDay = history.map { it.usage[r.rule.id] ?: 0.0 }
                    val total = perDay.sum()
                    val overDays = perDay.count { r.limitSec > 0 && it >= r.limitSec }
                    val peak = maxOf(r.limitSec, perDay.maxOrNull() ?: 0.0, 1.0)
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppAvatar(r.rule.name, r.rule.packages.firstOrNull(), 34.dp)
                        Column(Modifier.weight(1f)) {
                            Text(r.rule.name, color = C.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${fmtDuration(total)} · limit hit $overDays×", color = C.Muted, fontSize = 12.5.sp)
                        }
                        Row(
                            Modifier.weight(1f).height(36.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            perDay.forEach { s ->
                                val full = r.limitSec > 0 && s >= r.limitSec
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight((s / peak).toFloat().coerceAtLeast(0.1f))
                                        .alpha(if (s > 0) 1f else 0.25f)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (full) C.Danger else C.Accent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(label: String, value: String) {
    Column {
        Text(label, color = C.Muted, fontSize = 12.5.sp)
        Text(value, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
