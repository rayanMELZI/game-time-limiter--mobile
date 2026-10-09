package com.rayan.gametimelimiter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayan.gametimelimiter.data.Alert
import com.rayan.gametimelimiter.data.Level
import com.rayan.gametimelimiter.data.Settings
import com.rayan.gametimelimiter.data.Snapshot
import com.rayan.gametimelimiter.service.Alerts
import com.rayan.gametimelimiter.ui.components.Btn
import com.rayan.gametimelimiter.ui.components.BtnStyle
import com.rayan.gametimelimiter.ui.components.Card
import com.rayan.gametimelimiter.ui.components.GSwitch
import com.rayan.gametimelimiter.ui.theme.C
import com.rayan.gametimelimiter.ui.theme.Ic

@Composable
fun SettingsScreen(
    snap: Snapshot,
    perms: PermState,
    contentPadding: PaddingValues,
    onSave: (Settings) -> Unit,
    onStopLimiter: () -> Unit,
    onStartLimiter: () -> Unit,
    onFixPermission: (Perm) -> Unit,
) {
    val context = LocalContext.current
    val s = snap.settings
    val update = { patch: Settings -> onSave(patch) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Preferences", color = C.Muted, fontSize = 13.sp)
                Text("Settings", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            Group(Ic.Clock, "Day") {
                Setting("New day starts at", "Limits reset at this hour. Using an app past midnight still counts toward the previous day until then.") {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        Text(
                            "%02d:00".format(s.resetHour),
                            color = C.Text, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.Surface2).clickable { open = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                        DropdownMenu(open, { open = false }) {
                            (0..12).forEach { h ->
                                DropdownMenuItem(text = { Text("%02d:00".format(h)) }, onClick = { open = false; update(s.copy(resetHour = h)) })
                            }
                        }
                    }
                }
            }
        }

        item {
            Group(Ic.Bell, "Warnings") {
                Setting("On-screen banner", "A banner at the top of the screen, shown over the app you're using.") {
                    GSwitch(s.overlay) { update(s.copy(overlay = it)) }
                }
                Setting("Warning sound", "Plays a beep with every warning.") {
                    GSwitch(s.sound) { update(s.copy(sound = it)) }
                }
                Setting("Notifications", "Also sends a regular notification.") {
                    GSwitch(s.notifications) { update(s.copy(notifications = it)) }
                }
                Setting("Try it", "Show a sample warning right now.") {
                    Btn("Test", {
                        Alerts.alert(context, Alert(Level.Warning, "Clash Royale — 5 minutes left", "This is a test. Real warnings look exactly like this."))
                    }, small = true)
                }
            }
        }

        item {
            Group(Ic.Shield, "Protection") {
                Setting("Stop protection", "Blocks stopping the limiter while a limited app is open or a limit is locked.") {
                    GSwitch(s.guardStop) { update(s.copy(guardStop = it)) }
                }
                Setting(
                    if (s.limiterOn) "Limiter is running" else "Limiter is stopped",
                    if (s.limiterOn) "Runs in the background and restarts after a reboot." else "Limits aren't enforced until you start it again.",
                ) {
                    if (s.limiterOn) Btn("Stop", onStopLimiter, icon = Ic.Power, style = BtnStyle.Danger, small = true)
                    else Btn("Start", onStartLimiter, icon = Ic.Play, style = BtnStyle.Primary, small = true)
                }
                Text(
                    "While any limit is locked, the day start and stop protection can't be changed.",
                    color = C.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
            }
        }

        item {
            Group(Ic.Check, "Permissions") {
                PermRow("Usage access", "Detects which app is on screen", perms.usage) { onFixPermission(Perm.Usage) }
                PermRow("Display over other apps", "Shows warnings and the lock screen", perms.overlay) { onFixPermission(Perm.Overlay) }
                PermRow("Notifications", "Warning notifications", perms.notifications) { onFixPermission(Perm.Notifications) }
                PermRow("Unrestricted battery", "Keeps the limiter running reliably", perms.battery) { onFixPermission(Perm.Battery) }
            }
        }
    }
}

@Composable
private fun Group(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(padding = 0.dp) {
        Row(
            Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = C.Accent, modifier = Modifier.size(16.dp))
            Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Column(Modifier.padding(horizontal = 18.dp)) { content() }
    }
}

@Composable
private fun Setting(title: String, desc: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, color = C.Muted, fontSize = 12.5.sp)
        }
        control()
    }
}

@Composable
private fun PermRow(title: String, desc: String, granted: Boolean, onFix: () -> Unit) {
    Setting(title, desc) {
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Ic.Check, null, tint = C.Ok, modifier = Modifier.size(16.dp))
                Text("On", color = C.Ok, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Btn("Allow", onFix, small = true, style = BtnStyle.Primary)
        }
    }
}
