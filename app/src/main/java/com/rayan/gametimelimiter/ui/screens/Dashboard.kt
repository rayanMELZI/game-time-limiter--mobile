package com.rayan.gametimelimiter.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayan.gametimelimiter.R
import com.rayan.gametimelimiter.data.AppCatalog
import com.rayan.gametimelimiter.data.ExtraInfo
import com.rayan.gametimelimiter.data.Rule
import com.rayan.gametimelimiter.data.RuleStatus
import com.rayan.gametimelimiter.data.Snapshot
import com.rayan.gametimelimiter.data.Store
import com.rayan.gametimelimiter.ui.components.AppAvatar
import com.rayan.gametimelimiter.ui.components.Btn
import com.rayan.gametimelimiter.ui.components.BtnStyle
import com.rayan.gametimelimiter.ui.components.Card
import com.rayan.gametimelimiter.ui.components.Chip
import com.rayan.gametimelimiter.ui.components.ExtraSteps
import com.rayan.gametimelimiter.ui.components.Pill
import com.rayan.gametimelimiter.ui.components.PillKind
import com.rayan.gametimelimiter.ui.components.Ring
import com.rayan.gametimelimiter.ui.components.Tone
import com.rayan.gametimelimiter.ui.fmtCountdown
import com.rayan.gametimelimiter.ui.fmtDuration
import com.rayan.gametimelimiter.ui.fmtMinutes
import com.rayan.gametimelimiter.ui.theme.C
import com.rayan.gametimelimiter.ui.theme.Ic
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.min

@Composable
fun Dashboard(
    snap: Snapshot,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (Rule) -> Unit,
    onToggle: (Rule) -> Unit,
    onDelete: (Rule) -> Unit,
    onExtra: (Rule) -> Unit,
    onStartLimiter: () -> Unit,
) {
    val resetAt = Store.resetLabel(snap.day)
    val total = snap.rules.sumOf { it.usedSec }
    val playing = snap.rules.filter { it.running && it.rule.enabled && (!it.reached || it.extra.running) }
    val reached = snap.rules.count { it.reached }
    val date = snap.day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(date, color = C.Muted, fontSize = 13.sp)
                Text("Today's play time", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (!snap.settings.limiterOn) {
            item {
                Card(border = C.Warn.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Ic.Alert, null, tint = C.Warn, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Limiter is stopped", color = C.Text, fontWeight = FontWeight.SemiBold)
                            Text("Your limits aren't enforced right now.", color = C.Muted, fontSize = 12.5.sp)
                        }
                        Btn("Start", onStartLimiter, style = BtnStyle.Primary, small = true)
                    }
                }
            }
        }

        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Ic.Clock, "Played today", fmtDuration(total), null, Modifier.weight(1f))
                Stat(Ic.Gamepad, "Playing now", playing.firstOrNull()?.rule?.name ?: "Nothing", null, Modifier.weight(1f))
                Stat(Ic.Lock, "Limits reached", "$reached", "new day $resetAt", Modifier.weight(1f))
            }
        }

        if (snap.rules.isEmpty()) {
            item { EmptyState(onAdd) }
        } else {
            items(snap.rules, key = { it.rule.id }) { status ->
                RuleCard(status, resetAt, onEdit, onToggle, onDelete, onExtra)
            }
        }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, sub: String?, modifier: Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Surface)
            .border(1.dp, C.Border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(C.AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = C.Accent, modifier = Modifier.size(17.dp)) }
        Column {
            Text(label, color = C.Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = C.Muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Card(padding = 28.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(72.dp))
            Text("No limits yet", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Pick a game or app and set how long you can use it each day. You'll be warned before time runs out — then it gets closed and stays locked until tomorrow.",
                color = C.Muted, textAlign = TextAlign.Center, fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Btn("Limit your first app", onAdd, icon = Ic.Plus, style = BtnStyle.Primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleCard(
    status: RuleStatus,
    resetAt: String,
    onEdit: (Rule) -> Unit,
    onToggle: (Rule) -> Unit,
    onDelete: (Rule) -> Unit,
    onExtra: (Rule) -> Unit,
) {
    val context = LocalContext.current
    val rule = status.rule
    var confirmDelete by remember { mutableStateOf(false) }
    val remaining = (status.limitSec - status.usedSec).coerceAtLeast(0.0)
    val progress = if (status.limitSec > 0) (status.usedSec / status.limitSec).toFloat() else 1f
    val inExtra = status.extra.running
    val live = status.running && rule.enabled && (!status.reached || inExtra)

    val tone = when {
        !rule.enabled -> Tone.Off
        inExtra -> Tone.Warn
        status.reached -> Tone.Danger
        remaining <= min(600.0, status.limitSec * 0.2) -> Tone.Warn
        else -> Tone.Ok
    }
    val (pillText, pillKind) = when {
        !rule.enabled -> "Paused" to PillKind.Off
        inExtra -> "Extra time" to PillKind.Live
        status.reached -> (if (status.limitSec == 0.0) "Blocked" else "Limit reached") to PillKind.Danger
        status.running -> "Playing now" to PillKind.Live
        else -> "Not running" to PillKind.Idle
    }

    Card(
        modifier = Modifier.alpha(if (rule.enabled) 1f else 0.62f),
        border = if (live) C.Ok.copy(alpha = 0.35f) else C.Border,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppAvatar(rule.name, rule.packages.firstOrNull())
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rule.name, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Pill(pillText, pillKind)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val extraLen = com.rayan.gametimelimiter.data.EXTRA_STEPS_MIN[(status.extra.used - 1).coerceIn(0, 2)] * 60.0
                Ring(if (!rule.enabled) 0f else if (inExtra) ((status.extra.activeLeft ?: 0.0) / extraLen).toFloat() else progress, tone) {
                    if (inExtra) {
                        Text(fmtCountdown(status.extra.activeLeft ?: 0.0), color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text("extra time", color = C.Muted, fontSize = 11.5.sp)
                    } else if (status.locked) {
                        Icon(Ic.Lock, null, tint = C.Danger, modifier = Modifier.size(22.dp))
                        Text("until $resetAt", color = C.Muted, fontSize = 11.5.sp)
                    } else {
                        Text(if (status.reached) "0m" else fmtDuration(remaining), color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text("left today", color = C.Muted, fontSize = 11.5.sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatLine("Played", fmtDuration(status.usedSec))
                    StatLine("Today's limit", fmtMinutes((status.limitSec / 60).toInt()))
                    StatLine("Warnings", if (rule.warningsMin.isEmpty()) "None" else rule.warningsMin.joinToString(" · ") { "${it}m" })
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rule.packages.forEach { Chip(AppCatalog.label(context, it), Ic.Apps) }
                rule.weekendLimitMin?.let { Chip("Weekend ${fmtMinutes(it)}") }
                if (rule.lockWhenReached) Chip("Strict", Ic.Lock, color = C.Warn, highlight = true)
            }

            if (status.reached && rule.enabled && rule.allowExtra) ExtraRow(status.extra) { onExtra(rule) }

            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    status.locked -> {
                        Icon(Ic.Lock, null, tint = C.Danger, modifier = Modifier.size(14.dp))
                        Text("Locked — editing unlocks at $resetAt", color = C.Muted, fontSize = 13.sp)
                    }
                    confirmDelete -> {
                        Text("Remove this limit?", color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Btn("Cancel", { confirmDelete = false }, small = true)
                        Btn("Remove", { onDelete(rule) }, style = BtnStyle.Danger, small = true)
                    }
                    else -> {
                        Btn("Edit", { onEdit(rule) }, icon = Ic.Edit, small = true)
                        Btn(if (rule.enabled) "Pause" else "Resume", { onToggle(rule) }, icon = if (rule.enabled) Ic.Pause else Ic.Play, small = true)
                        Spacer(Modifier.weight(1f))
                        Btn(null, { confirmDelete = true }, icon = Ic.Trash, small = true)
                    }
                }
            }
        }
    }
}

/** Extra time after the limit: available, running, cooling down or used up. */
@Composable
private fun ExtraRow(extra: ExtraInfo, onStart: () -> Unit) {
    val (border, bg) = when {
        extra.running -> C.Warn.copy(alpha = 0.35f) to C.Warn.copy(alpha = 0.07f)
        extra.canStart -> C.Accent.copy(alpha = 0.35f) to C.AccentSoft
        else -> C.Border to C.Surface2
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        when {
            extra.running -> {
                Icon(Ic.Clock, null, tint = C.Warn, modifier = Modifier.size(15.dp))
                Text("Extra time — ${fmtCountdown(extra.activeLeft ?: 0.0)} left", color = C.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                ExtraSteps(extra.used)
            }
            extra.nextMinutes == null -> {
                Icon(Ic.Lock, null, tint = C.Muted, modifier = Modifier.size(15.dp))
                Text("No extra time left today", color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                ExtraSteps(extra.used)
            }
            extra.cooldownLeft != null -> {
                Icon(Ic.Clock, null, tint = C.Muted, modifier = Modifier.size(15.dp))
                Text("${extra.nextMinutes} more min in ${fmtCountdown(extra.cooldownLeft)}", color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                ExtraSteps(extra.used)
            }
            else -> {
                Text("Need to finish something?", color = C.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Btn("Use ${extra.nextMinutes} more min", onStart, style = BtnStyle.Primary, small = true)
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Column {
        Text(label, color = C.Muted, fontSize = 12.sp)
        Text(value, color = C.Text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
