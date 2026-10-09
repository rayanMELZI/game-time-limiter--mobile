package com.rayan.gametimelimiter.ui

import android.Manifest
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayan.gametimelimiter.data.Rule
import com.rayan.gametimelimiter.data.Store
import com.rayan.gametimelimiter.service.LimiterService
import com.rayan.gametimelimiter.service.Permissions
import com.rayan.gametimelimiter.ui.screens.Dashboard
import com.rayan.gametimelimiter.ui.screens.History
import com.rayan.gametimelimiter.ui.screens.Onboarding
import com.rayan.gametimelimiter.ui.screens.Perm
import com.rayan.gametimelimiter.ui.screens.PermState
import com.rayan.gametimelimiter.ui.screens.RuleEditor
import com.rayan.gametimelimiter.ui.screens.SettingsScreen
import com.rayan.gametimelimiter.ui.theme.C
import com.rayan.gametimelimiter.ui.theme.GtlTheme
import com.rayan.gametimelimiter.ui.theme.Ic
import kotlinx.coroutines.delay

private enum class Tab(val label: String) { Today("Today"), History("History"), Settings("Settings") }

private data class Notice(val text: String, val error: Boolean, val id: Long = System.nanoTime())

class MainActivity : ComponentActivity() {
    private var perms by mutableStateOf<PermState?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            // Already denied before: Android won't ask again, so open the settings page instead.
            startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName))
        }
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Store.init(this)
        refresh()
        setContent { GtlTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val state = PermState.read(this)
        perms = state
        if (state.required) LimiterService.start(this)
    }

    private fun fix(perm: Perm) {
        runCatching {
            when (perm) {
                Perm.Usage -> runCatching { startActivity(Permissions.usageAccessIntent(this)) }
                    .onFailure { startActivity(Permissions.usageAccessFallbackIntent()) }
                Perm.Overlay -> startActivity(Permissions.overlayIntent(this))
                Perm.Notifications -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Perm.Battery -> startActivity(Permissions.batteryIntent(this))
            }
        }
    }

    @Composable
    private fun Root() {
        val p = perms ?: return
        val snap by Store.snapshot.collectAsState()
        val s = snap ?: return

        if (!p.required) {
            Onboarding(p, ::fix)
            return
        }

        var tab by rememberSaveable { mutableStateOf(Tab.Today) }
        var editing by rememberSaveable { mutableStateOf<String?>(null) } // rule id, "new", or null
        var notice by androidx.compose.runtime.remember { mutableStateOf<Notice?>(null) }

        LaunchedEffect(notice) {
            if (notice != null) {
                delay(4000)
                notice = null
            }
        }

        val run = { error: String?, success: String? ->
            notice = when {
                error != null -> Notice(error, true)
                success != null -> Notice(success, false)
                else -> null
            }
            error == null
        }

        Box(Modifier.fillMaxSize().background(C.Bg)) {
            Scaffold(
                containerColor = C.Bg,
                bottomBar = {
                    NavigationBar(containerColor = C.Surface, tonalElevation = 0.dp) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = {
                                    Icon(
                                        when (t) {
                                            Tab.Today -> Ic.Home
                                            Tab.History -> Ic.Chart
                                            Tab.Settings -> Ic.Settings
                                        },
                                        t.label,
                                    )
                                },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = C.Accent,
                                    selectedTextColor = C.Text,
                                    indicatorColor = C.AccentSoft,
                                    unselectedIconColor = C.Muted,
                                    unselectedTextColor = C.Muted,
                                ),
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab == Tab.Today && s.rules.isNotEmpty()) {
                        Row(
                            Modifier
                                .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = C.Accent)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF9A8CFF), C.AccentDeep)))
                                .clickable { editing = "new" }
                                .padding(horizontal = 20.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Ic.Plus, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("Limit an app", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                },
            ) { padding ->
                when (tab) {
                    Tab.Today -> Dashboard(
                        snap = s,
                        contentPadding = padding,
                        onAdd = { editing = "new" },
                        onEdit = { editing = it.id },
                        onToggle = { r: Rule ->
                            run(Store.setRuleEnabled(r.id, !r.enabled), if (r.enabled) "${r.name} paused" else "${r.name} resumed")
                        },
                        onDelete = { r -> run(Store.deleteRule(r.id), "${r.name} removed") },
                        onStartLimiter = {
                            run(Store.setLimiterOn(true), "Limiter started")
                            LimiterService.start(this@MainActivity)
                        },
                    )
                    Tab.History -> History(s, padding)
                    Tab.Settings -> SettingsScreen(
                        snap = s,
                        perms = p,
                        contentPadding = padding,
                        onSave = { run(Store.saveSettings(it), "Settings saved") },
                        onStopLimiter = { run(Store.setLimiterOn(false), "Limiter stopped") },
                        onStartLimiter = {
                            run(Store.setLimiterOn(true), "Limiter started")
                            LimiterService.start(this@MainActivity)
                        },
                        onFixPermission = ::fix,
                    )
                }
            }

            editing?.let { id ->
                val initial = s.rules.find { it.rule.id == id }?.rule
                RuleEditor(
                    initial = initial,
                    onClose = { editing = null },
                    onSave = { rule ->
                        if (run(Store.saveRule(rule), if (initial == null) "${rule.name} is now limited" else "Changes saved")) editing = null
                    },
                )
            }

            AnimatedVisibility(
                visible = notice != null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
            ) {
                val n = notice ?: return@AnimatedVisibility
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (n.error) Color(0xFF2A1820) else C.Surface3)
                        .border(1.dp, if (n.error) C.Danger.copy(alpha = 0.4f) else C.BorderStrong, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (n.error) Icon(Ic.Alert, null, tint = C.Danger, modifier = Modifier.size(16.dp))
                    Text(n.text, color = C.Text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }
        }
    }
}
