package com.rayan.gametimelimiter.ui

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayan.gametimelimiter.R
import com.rayan.gametimelimiter.data.ExtraInfo
import com.rayan.gametimelimiter.data.Store
import com.rayan.gametimelimiter.ui.components.Btn
import com.rayan.gametimelimiter.ui.components.BtnStyle
import com.rayan.gametimelimiter.ui.components.ExtraSteps
import com.rayan.gametimelimiter.ui.components.Ring
import com.rayan.gametimelimiter.ui.components.Tone
import com.rayan.gametimelimiter.ui.theme.C
import com.rayan.gametimelimiter.ui.theme.GtlTheme
import com.rayan.gametimelimiter.ui.theme.Ic

/** Full-screen lock shown on top of an app whose daily limit is reached. */
class BlockActivity : ComponentActivity() {
    private var ruleId by mutableStateOf("")
    private var pkg by mutableStateOf("")
    private var name by mutableStateOf("")
    private var resetAt by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Store.init(this)
        read(intent)
        setContent {
            GtlTheme {
                BackHandler { goHome() }
                val snap by Store.snapshot.collectAsState()
                val extra = snap?.rules?.find { it.rule.id == ruleId }?.extra
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(C.Danger.copy(alpha = 0.10f), C.Bg, C.Bg)))
                        .systemBarsPadding()
                        .padding(24.dp),
                ) {
                    Column(
                        Modifier.align(Alignment.Center).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Ring(1f, Tone.Danger, size = 150.dp) {
                            Icon(Ic.Lock, null, tint = C.Danger, modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Time's up", color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "You've used all of today's time for $name.",
                            color = C.Text, fontSize = 16.sp, textAlign = TextAlign.Center,
                        )
                        Text(
                            if (extra?.nextMinutes != null) "It stays locked until $resetAt — unless you need a few more minutes to finish something."
                            else "It stays locked until $resetAt. Go do something else — it'll be here tomorrow.",
                            color = C.Muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (extra != null && (extra.nextMinutes != null || extra.used > 0)) ExtraPanel(extra)
                        Btn("Go to home screen", ::goHome, Modifier.fillMaxWidth(), style = if (extra?.canStart == true) BtnStyle.Ghost else BtnStyle.Primary)
                    }
                    Image(
                        painterResource(R.drawable.logo), null,
                        Modifier.align(Alignment.BottomCenter).size(36.dp).clip(CircleShape),
                    )
                }
            }
        }
    }

    @Composable
    private fun ExtraPanel(extra: ExtraInfo) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(C.Surface)
                .border(1.dp, if (extra.canStart) C.Accent.copy(alpha = 0.4f) else C.Border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Extra time", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                ExtraSteps(extra.used)
            }
            when {
                extra.nextMinutes == null -> Text("No extra time left today.", color = C.Muted, fontSize = 13.5.sp)
                extra.cooldownLeft != null -> Text(
                    "${extra.nextMinutes} more minute${if (extra.nextMinutes == 1) "" else "s"} available in ${fmtCountdown(extra.cooldownLeft)}.",
                    color = C.Muted, fontSize = 13.5.sp,
                )
                else -> Btn(
                    "Use ${extra.nextMinutes} more minute${if (extra.nextMinutes == 1) "" else "s"}",
                    ::startExtra,
                    Modifier.fillMaxWidth(),
                    style = BtnStyle.Primary,
                )
            }
        }
    }

    private fun startExtra() {
        Store.startExtra(ruleId)
            .onSuccess {
                // Straight back into the app (it was kept alive behind this screen).
                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                finish()
            }
            .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent) {
        ruleId = intent.getStringExtra(EXTRA_RULE) ?: ""
        pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        name = intent.getStringExtra(EXTRA_NAME) ?: "this app"
        resetAt = intent.getStringExtra(EXTRA_RESET) ?: "tomorrow"
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_RULE = "rule"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_NAME = "name"
        const val EXTRA_RESET = "reset"
    }
}
