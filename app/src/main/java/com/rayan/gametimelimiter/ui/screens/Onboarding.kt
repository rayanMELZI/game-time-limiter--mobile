package com.rayan.gametimelimiter.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayan.gametimelimiter.R
import com.rayan.gametimelimiter.service.Permissions
import com.rayan.gametimelimiter.ui.components.Btn
import com.rayan.gametimelimiter.ui.components.BtnStyle
import com.rayan.gametimelimiter.ui.theme.C
import com.rayan.gametimelimiter.ui.theme.Ic

enum class Perm { Usage, Overlay, Notifications, Battery }

data class PermState(val usage: Boolean, val overlay: Boolean, val notifications: Boolean, val battery: Boolean) {
    val required get() = usage && overlay

    companion object {
        fun read(context: Context) = PermState(
            usage = Permissions.hasUsageAccess(context),
            overlay = Permissions.hasOverlay(context),
            notifications = Permissions.hasNotifications(context),
            battery = Permissions.ignoresBatteryOptimizations(context),
        )
    }
}

/** First launch: explains and requests the permissions the limiter needs. */
@Composable
fun Onboarding(perms: PermState, onFix: (Perm) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(84.dp))
        Text("Game Time Limiter", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Set a daily limit for any game or app. You'll be warned before time runs out — then it's closed and stays locked until tomorrow.",
            color = C.Muted, textAlign = TextAlign.Center, fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "To do that, Android needs you to allow a few things:",
            color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth(),
        )

        Step(Ic.Eye, "Usage access", "Lets the limiter see which app is on screen, to count its time. Nothing leaves your phone.", perms.usage, required = true) { onFix(Perm.Usage) }
        Step(Ic.Layers, "Display over other apps", "Shows warnings and the lock screen on top of the app when time is up.", perms.overlay, required = true) { onFix(Perm.Overlay) }
        Step(Ic.Bell, "Notifications", "Warning notifications before an app is closed.", perms.notifications, required = false) { onFix(Perm.Notifications) }
        Step(Ic.Battery, "Unrestricted battery", "Stops Android from putting the limiter to sleep. Recommended.", perms.battery, required = false) { onFix(Perm.Battery) }

        Spacer(Modifier.height(4.dp))
        Text(
            if (perms.required) "All set — opening the app…" else "Usage access and display over other apps are required.",
            color = if (perms.required) C.Ok else C.Faint, fontSize = 12.5.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, desc: String, granted: Boolean, required: Boolean, onFix: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Surface)
            .border(1.dp, if (granted) C.Ok.copy(alpha = 0.3f) else C.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(if (granted) C.Ok.copy(alpha = 0.12f) else C.AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(if (granted) Ic.Check else icon, null, tint = if (granted) C.Ok else C.Accent, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                if (!required) Text("optional", color = C.Faint, fontSize = 11.sp)
            }
            Text(desc, color = C.Muted, fontSize = 12.5.sp)
        }
        if (!granted) Btn("Allow", onFix, small = true, style = BtnStyle.Primary)
    }
}
