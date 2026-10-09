package com.rayan.gametimelimiter.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayan.gametimelimiter.data.AppCatalog
import com.rayan.gametimelimiter.ui.hueFor
import com.rayan.gametimelimiter.ui.theme.C

@Composable
fun Card(
    modifier: Modifier = Modifier,
    border: Color = C.Border,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Surface)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .padding(padding),
        content = content,
    )
}

enum class Tone { Ok, Warn, Danger, Off }

@Composable
fun Ring(progress: Float, tone: Tone, size: Dp = 118.dp, content: @Composable () -> Unit) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "ring")
    val color = when (tone) {
        Tone.Ok -> C.Accent
        Tone.Warn -> C.Warn
        Tone.Danger -> C.Danger
        Tone.Off -> C.Faint
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(C.Surface3, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** Real app icon when available, otherwise colored initials like the desktop app. */
@Composable
fun AppAvatar(name: String, pkg: String? = null, size: Dp = 40.dp) {
    val context = LocalContext.current
    val icon = remember(pkg) { pkg?.let { AppCatalog.icon(context, it) } }
    if (icon != null) {
        Image(icon, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.27f)))
        return
    }
    val hue = hueFor(name)
    val letters = name.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(Brush.linearGradient(listOf(Color.hsl(hue, 0.7f, 0.58f), Color.hsl((hue + 40) % 360, 0.65f, 0.42f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(letters.ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
    }
}

@Composable
fun LiveDot(color: Color = C.Ok, size: Dp = 7.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).alpha(a).clip(CircleShape).background(color))
}

enum class PillKind { Live, Idle, Off, Danger }

@Composable
fun Pill(text: String, kind: PillKind) {
    val (fg, bg) = when (kind) {
        PillKind.Live -> C.Ok to C.Ok.copy(alpha = 0.12f)
        PillKind.Idle -> C.Muted to C.Surface2
        PillKind.Off -> C.Faint to C.Surface2
        PillKind.Danger -> C.Danger to C.Danger.copy(alpha = 0.13f)
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (kind == PillKind.Live) LiveDot(size = 6.dp)
        Text(text, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Chip(text: String, icon: ImageVector? = null, color: Color = C.Muted, highlight: Boolean = false, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlight) color.copy(alpha = 0.08f) else C.Surface2)
            .border(1.dp, if (highlight) color.copy(alpha = 0.25f) else C.Border, RoundedCornerShape(8.dp))
            .padding(start = 9.dp, end = if (onRemove != null) 3.dp else 9.dp, top = 4.dp, bottom = 4.dp)
            .widthIn(max = 260.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (onRemove != null) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Icon(com.rayan.gametimelimiter.ui.theme.Ic.X, "Remove", tint = C.Muted, modifier = Modifier.size(12.dp)) }
        }
    }
}

enum class BtnStyle { Primary, Ghost, Danger }

@Composable
fun Btn(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: BtnStyle = BtnStyle.Ghost,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(if (small) 9.dp else 12.dp)
    val base = modifier
        .height(if (small) 34.dp else 46.dp)
        .alpha(if (enabled) 1f else 0.45f)
        .clip(shape)
    val styled = when (style) {
        BtnStyle.Primary -> base.background(Brush.linearGradient(listOf(Color(0xFF9A8CFF), C.AccentDeep)))
        BtnStyle.Ghost -> base.background(C.Surface2).border(1.dp, C.Border, shape)
        BtnStyle.Danger -> base.background(C.Danger.copy(alpha = 0.16f)).border(1.dp, C.Danger.copy(alpha = 0.3f), shape)
    }
    val fg = when (style) {
        BtnStyle.Primary -> Color.White
        BtnStyle.Ghost -> C.Text
        BtnStyle.Danger -> C.Danger
    }
    Row(
        styled
            .clickable(enabled = enabled, onClick = onClick)
            .padding(PaddingValues(horizontal = if (text == null) if (small) 8.dp else 12.dp else if (small) 11.dp else 18.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, text, tint = fg, modifier = Modifier.size(if (small) 16.dp else 18.dp))
        if (text != null) Text(text, color = fg, fontWeight = if (small) FontWeight.Medium else FontWeight.SemiBold, fontSize = if (small) 13.sp else 15.sp)
    }
}

@Composable
fun GSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.scale(0.85f),
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = C.Accent,
            checkedBorderColor = C.Accent,
            uncheckedThumbColor = Color(0xFFC9CBE0),
            uncheckedTrackColor = C.Surface3,
            uncheckedBorderColor = C.Surface3,
        ),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) C.AccentSoft else C.Surface2)
            .border(1.dp, if (selected) C.Accent.copy(alpha = 0.5f) else C.Border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) C.Text else C.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** The 5m · 2m · 1m extra sessions, crossed out once used. */
@Composable
fun ExtraSteps(used: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        com.rayan.gametimelimiter.data.EXTRA_STEPS_MIN.forEachIndexed { i, m ->
            val done = i < used
            Text(
                "${m}m",
                color = C.Muted,
                fontSize = 11.sp,
                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                modifier = Modifier
                    .alpha(if (done) 0.55f else 1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(C.Surface3)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}
