package com.rayan.gametimelimiter.service

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.rayan.gametimelimiter.data.BlockRequest
import com.rayan.gametimelimiter.data.Store
import com.rayan.gametimelimiter.ui.BlockActivity
import com.rayan.gametimelimiter.ui.fmtDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs in the background, counts screen time of limited apps and enforces the limits. */
class LimiterService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var tracker: ForegroundTracker
    private var notificationText = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Alerts.createChannels(this)
        startInForeground("Watching your apps")
        tracker = ForegroundTracker(this)
        scope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Store.settings.limiterOn) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        Store.save()
        super.onDestroy()
    }

    private fun startInForeground(text: String) {
        notificationText = text
        val notification = Alerts.serviceNotification(this, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Alerts.SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Alerts.SERVICE_ID, notification)
        }
    }

    private suspend fun loop() {
        val power = getSystemService(PowerManager::class.java)
        var last = SystemClock.elapsedRealtime()
        while (scope.isActive) {
            val screenOn = power.isInteractive
            delay(if (screenOn) 1000 else 5000)
            if (!Store.settings.limiterOn) {
                withContext(Dispatchers.Main) { stopSelf() }
                return
            }

            val now = SystemClock.elapsedRealtime()
            val gap = (now - last) / 1000.0
            last = now
            // Gaps (deep sleep) and screen-off time are never counted.
            val dt = if (!screenOn || gap > 10) 0.0 else gap
            val foreground = if (screenOn) runCatching { tracker.current() }.getOrNull() else null

            val result = Store.tick(foreground?.takeIf { it != packageName }, dt)
            withContext(Dispatchers.Main) {
                result.alerts.forEach { Alerts.alert(this@LimiterService, it) }
                result.block?.let { block(it) }
                updateNotification()
            }
        }
    }

    private fun block(request: BlockRequest) {
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(BlockActivity.EXTRA_RULE, request.ruleId)
                .putExtra(BlockActivity.EXTRA_PKG, request.pkg)
                .putExtra(BlockActivity.EXTRA_NAME, request.ruleName)
                .putExtra(BlockActivity.EXTRA_RESET, request.resetAt),
        )
        // Once no extra time can follow and it's behind the lock screen, close it for real.
        if (request.kill) {
            main.postDelayed({
                getSystemService(ActivityManager::class.java).killBackgroundProcesses(request.pkg)
            }, 1500)
        }
    }

    private fun updateNotification() {
        val snap = Store.snapshot.value ?: return
        val playing = snap.rules.firstOrNull { it.running && it.rule.enabled && (!it.reached || it.extra.running) }
        val enabled = snap.rules.count { it.rule.enabled }
        val text = when {
            playing != null && playing.extra.running -> "${playing.rule.name} · ${fmtDuration(playing.extra.activeLeft ?: 0.0)} of extra time left"
            playing != null -> "${playing.rule.name} · ${fmtDuration(playing.limitSec - playing.usedSec)} left"
            enabled == 0 -> "No limits yet"
            else -> "Watching $enabled app${if (enabled > 1) "s" else ""}"
        }
        if (text != notificationText) {
            notificationText = text
            Alerts.updateServiceNotification(this, text)
        }
    }

    companion object {
        fun start(context: Context) {
            if (!Store.settings.limiterOn) return
            ContextCompat.startForegroundService(context, Intent(context, LimiterService::class.java))
        }
    }
}
