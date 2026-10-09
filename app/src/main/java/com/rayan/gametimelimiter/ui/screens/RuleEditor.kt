package com.rayan.gametimelimiter.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayan.gametimelimiter.data.AppCatalog
import com.rayan.gametimelimiter.data.InstalledApp
import com.rayan.gametimelimiter.data.Rule
import com.rayan.gametimelimiter.ui.components.AppAvatar
import com.rayan.gametimelimiter.ui.components.Btn
import com.rayan.gametimelimiter.ui.components.BtnStyle
import com.rayan.gametimelimiter.ui.components.Chip
import com.rayan.gametimelimiter.ui.components.GSwitch
import com.rayan.gametimelimiter.ui.components.PresetChip
import com.rayan.gametimelimiter.ui.components.SectionLabel
import com.rayan.gametimelimiter.ui.fmtMinutes
import com.rayan.gametimelimiter.ui.theme.C
import com.rayan.gametimelimiter.ui.theme.Ic

private val LIMIT_PRESETS = listOf(30, 60, 90, 120, 180, 240)
private val WARNING_OPTIONS = listOf(30, 15, 10, 5, 2, 1)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditor(initial: Rule?, onClose: () -> Unit, onSave: (Rule) -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var nameTouched by rememberSaveable { mutableStateOf(initial != null) }
    val packages = remember { mutableStateListOf<String>().apply { addAll(initial?.packages ?: emptyList()) } }
    var limit by rememberSaveable { mutableStateOf(initial?.dailyLimitMin ?: 60) }
    var weekend by rememberSaveable { mutableStateOf(initial?.weekendLimitMin) }
    val warnings = remember { mutableStateListOf<Int>().apply { addAll(initial?.warningsMin ?: listOf(10, 5, 1)) } }
    var strict by rememberSaveable { mutableStateOf(initial?.lockWhenReached ?: true) }
    var allowExtra by rememberSaveable { mutableStateOf(initial?.allowExtra ?: true) }
    var picking by rememberSaveable { mutableStateOf(initial == null) }

    if (picking) {
        AppPicker(
            selected = packages.toList(),
            onDone = { chosen, firstLabel ->
                packages.clear()
                packages.addAll(chosen)
                if (!nameTouched && name.isEmpty() && firstLabel != null) name = firstLabel
                picking = false
                if (initial == null && chosen.isEmpty()) onClose()
            },
        )
        return
    }

    BackHandler(onBack = onClose)
    val canSave = name.isNotBlank() && packages.isNotEmpty()

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Header(if (initial != null) "Edit ${initial.name}" else "Limit an app", onClose)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Field("Limited apps") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    packages.forEach { pkg ->
                        Chip(AppCatalog.label(context, pkg), Ic.Apps, color = C.Text, onRemove = { packages.remove(pkg) })
                    }
                }
                Btn(if (packages.isEmpty()) "Choose apps" else "Change apps", { picking = true }, icon = Ic.Apps, small = true)
            }

            Field("Name") {
                TextInput(name, "e.g. Clash Royale") { name = it; nameTouched = true }
            }

            Field("Daily limit") {
                DurationStepper(limit) { limit = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LIMIT_PRESETS.forEach { p -> PresetChip(fmtMinutes(p), limit == p) { limit = p } }
                }
                if (limit == 0) Text("0 minutes blocks this app completely.", color = C.Warn, fontSize = 12.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Different limit on Saturday & Sunday", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    GSwitch(weekend != null) { weekend = if (it) limit else null }
                }
                weekend?.let { w -> DurationStepper(w) { weekend = it } }
            }

            Field("Warn me before closing") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WARNING_OPTIONS.forEach { w ->
                        PresetChip("$w min", w in warnings) { if (w in warnings) warnings.remove(w) else warnings.add(w) }
                    }
                }
            }

            OptionBox(
                on = allowExtra,
                accent = C.Accent,
                icon = Ic.Clock,
                title = "Extra time after the limit",
                desc = "After time's up, you can come back for 5, then 2, then 1 more minute — with a 5-minute break between each — to finish what you were doing.",
            ) { allowExtra = it }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (strict) C.Warn.copy(alpha = 0.06f) else C.Surface2)
                    .border(1.dp, if (strict) C.Warn.copy(alpha = 0.35f) else C.Border, RoundedCornerShape(14.dp))
                    .clickable { strict = !strict }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Ic.Lock, null, tint = if (strict) C.Warn else C.Muted, modifier = Modifier.size(14.dp))
                        Text("Strict mode", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Text(
                        "Once the limit is reached, this rule can't be edited, paused or removed until the next day. Turn it off if you want to be able to extend your time.",
                        color = C.Muted, fontSize = 12.5.sp,
                    )
                }
                GSwitch(strict) { strict = it }
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(
            Modifier.fillMaxWidth().background(C.Surface).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Btn("Cancel", onClose, Modifier.weight(1f))
            Btn(
                if (initial != null) "Save changes" else "Start limiting",
                {
                    val maxLimit = maxOf(limit, weekend ?: 0)
                    onSave(
                        Rule(
                            id = initial?.id ?: "",
                            name = name.trim(),
                            packages = packages.toList(),
                            dailyLimitMin = limit,
                            weekendLimitMin = weekend,
                            warningsMin = warnings.filter { it < maxLimit }.sortedDescending(),
                            lockWhenReached = strict,
                            enabled = initial?.enabled ?: true,
                            allowExtra = allowExtra,
                        ),
                    )
                },
                Modifier.weight(1.4f),
                style = BtnStyle.Primary,
                enabled = canSave,
            )
        }
    }
}

@Composable
private fun OptionBox(on: Boolean, accent: androidx.compose.ui.graphics.Color, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) accent.copy(alpha = 0.08f) else C.Surface2)
            .border(1.dp, if (on) accent.copy(alpha = 0.4f) else C.Border, RoundedCornerShape(14.dp))
            .clickable { onChange(!on) }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = if (on) accent else C.Muted, modifier = Modifier.size(14.dp))
                Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Text(desc, color = C.Muted, fontSize = 12.5.sp)
        }
        GSwitch(on, onChange)
    }
}

@Composable
private fun Header(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Ic.X, "Close", tint = C.Text)
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun TextInput(value: String, placeholder: String, leading: (@Composable () -> Unit)? = null, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(C.Bg)
            .border(1.dp, C.BorderStrong, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = C.Faint, fontSize = 15.sp)
            BasicTextField(
                value, onChange,
                singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(C.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Hours and minutes with +/- buttons (minutes move in steps of 5). */
@Composable
private fun DurationStepper(minutes: Int, onChange: (Int) -> Unit) {
    val h = minutes / 60
    val m = minutes % 60
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stepper("$h", "h", Modifier.weight(1f),
            onMinus = { onChange(((h - 1).coerceAtLeast(0)) * 60 + m) },
            onPlus = { onChange(((h + 1).coerceAtMost(24)) * 60 + if (h + 1 >= 24) 0 else m) })
        Stepper("$m", "min", Modifier.weight(1f),
            onMinus = { onChange((minutes - 5).coerceAtLeast(0)) },
            onPlus = { onChange((minutes + 5).coerceAtMost(24 * 60)) })
    }
}

@Composable
private fun Stepper(value: String, unit: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(C.Bg)
            .border(1.dp, C.BorderStrong, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clickable(onClick = onMinus), contentAlignment = Alignment.Center) {
            Icon(Ic.Minus, "Less", tint = C.Muted, modifier = Modifier.size(18.dp))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(value, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Box(Modifier.size(48.dp).clickable(onClick = onPlus), contentAlignment = Alignment.Center) {
            Icon(Ic.Plus, "More", tint = C.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** Step 1: choose which installed apps the limit applies to. */
@Composable
private fun AppPicker(selected: List<String>, onDone: (List<String>, String?) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    LaunchedEffect(Unit) { apps = AppCatalog.load(context) }

    val finish = {
        val first = chosen.firstOrNull()?.let { pkg -> apps?.find { it.pkg == pkg }?.label ?: AppCatalog.label(context, pkg) }
        onDone(chosen.toList(), first)
    }
    BackHandler(onBack = finish)

    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        apps.orEmpty().filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
    }

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Header("What should be limited?", finish)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextInput(query, "Search apps…", leading = { Icon(Ic.Search, null, tint = C.Muted, modifier = Modifier.size(18.dp)) }) { query = it }
            SectionLabel("Most used this week first")
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when {
                apps == null -> item { Text("Loading your apps…", color = C.Muted, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center) }
                filtered.isEmpty() -> item { Text("No app matches.", color = C.Muted, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center) }
                else -> items(filtered, key = { it.pkg }) { app ->
                    val isSelected = app.pkg in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) C.AccentSoft else C.Bg)
                            .border(1.dp, if (isSelected) C.Accent.copy(alpha = 0.4f) else C.Bg, RoundedCornerShape(12.dp))
                            .clickable { if (isSelected) chosen.remove(app.pkg) else chosen.add(app.pkg) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppAvatar(app.label, app.pkg, 38.dp)
                        Column(Modifier.weight(1f)) {
                            Text(app.label, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.pkg, color = C.Faint, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (app.weeklyMinutes > 0) Text(fmtMinutes(app.weeklyMinutes.toInt()), color = C.Faint, fontSize = 12.sp)
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) C.Accent else C.Bg)
                                .border(1.5.dp, if (isSelected) C.Accent else C.BorderStrong, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (isSelected) Icon(Ic.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(C.Surface).padding(16.dp)) {
            Btn(
                if (chosen.isEmpty()) "Select at least one app" else "Continue with ${chosen.size} app${if (chosen.size > 1) "s" else ""}",
                finish,
                Modifier.fillMaxWidth(),
                style = BtnStyle.Primary,
                enabled = chosen.isNotEmpty(),
            )
        }
    }
}
