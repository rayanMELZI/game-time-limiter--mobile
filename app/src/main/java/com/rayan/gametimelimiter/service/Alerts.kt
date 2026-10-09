package com.rayan.gametimelimiter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rayan.gametimelimiter.R
import com.rayan.gametimelimiter.data.Alert
import com.rayan.gametimelimiter.data.Level
import com.rayan.gametimelimiter.data.Store
import com.rayan.gametimelimiter.ui.MainActivity

/** Warning sound, on-screen banner and notifications. */
object Alerts {
    const val SERVICE_ID = 1
    private const val CH_SERVICE = "limiter"
    private const val CH_WARNINGS = "warnings"
    private const val CH_WARNINGS_QUIET = "warnings_quiet"
    private const val BANNER_MS = 9000L

    private val main = Handler(Looper.getMainLooper())
    private var banner: View? = null
    private var nextId = 100

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Limiter running", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Game Time Limiter watches your apps"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WARNINGS, "Time warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Warnings before an app is closed"
                setSound(null, null) // the app plays its own beep
            },
        )
        // Used while the on-screen banner is shown, so a pop-up notification doesn't cover it.
        nm.createNotificationChannel(
            NotificationChannel(CH_WARNINGS_QUIET, "Time warnings (with banner)", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Warnings that are also shown as an on-screen banner"
                setSound(null, null)
            },
        )
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    fun serviceNotification(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Game Time Limiter")
            .setContentText(text)
            .setColor(0xFF8B7BFF.toInt())
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .build()

    fun updateServiceNotification(context: Context, text: String) {
        runCatching { NotificationManagerCompat.from(context).notify(SERVICE_ID, serviceNotification(context, text)) }
    }

    fun alert(context: Context, alert: Alert) {
        val settings = Store.settings
        val banner = settings.overlay && Settings.canDrawOverlays(context)
        if (settings.sound) playSound(context)
        if (settings.notifications) notify(context, alert, popUp = !banner)
        if (banner) showBanner(context, alert)
    }

    private fun playSound(context: Context) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val am = context.getSystemService(android.media.AudioManager::class.java)
        MediaPlayer.create(context, R.raw.bip, attrs, am.generateAudioSessionId())?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }

    private fun notify(context: Context, alert: Alert, popUp: Boolean) {
        val n = NotificationCompat.Builder(context, if (popUp) CH_WARNINGS else CH_WARNINGS_QUIET)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setColor(if (alert.level == Level.Danger) 0xFFFF5D6C.toInt() else 0xFFFFB547.toInt())
            .setPriority(if (popUp) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(nextId++, n) }
    }

    /** Always-on-top banner at the top of the screen, like the desktop warning overlay. */
    private fun showBanner(context: Context, alert: Alert) {
        val wm = context.getSystemService(WindowManager::class.java)
        hideBanner(wm)

        val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt() }
        val accent = when (alert.level) {
            Level.Danger -> 0xFFFF5D6C.toInt()
            Level.Warning -> 0xFFFFB547.toInt()
            Level.Info -> 0xFF8B7BFF.toInt()
        }

        val icon = ImageView(context).apply {
            setImageResource(if (alert.level == Level.Danger) R.drawable.ic_lock else R.drawable.ic_alert)
            setColorFilter(accent)
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor((accent and 0x00FFFFFF) or 0x26000000)
            }
        }
        val title = TextView(context).apply {
            text = alert.title
            setTextColor(0xFFECEEF7.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val body = TextView(context).apply {
            text = alert.body
            setTextColor(0xFF8B90A8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), 0, 0, 0)
            addView(title)
            addView(body)
        }
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(18f), dp(14f))
            background = GradientDrawable().apply {
                cornerRadius = dp(16f).toFloat()
                setColor(Color.argb(247, 20, 21, 32))
                setStroke(dp(1f), (accent and 0x00FFFFFF) or 0x73000000)
            }
            elevation = dp(8f).toFloat()
            addView(icon, LinearLayout.LayoutParams(dp(44f), dp(44f)))
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { hideBanner(wm) }
            alpha = 0f
            translationY = -dp(16f).toFloat()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = dp(36f)
            horizontalMargin = 0.04f
        }

        runCatching {
            wm.addView(view, params)
            banner = view
            view.animate().alpha(1f).translationY(0f).setDuration(300).start()
            main.postDelayed({ if (banner === view) hideBanner(wm) }, BANNER_MS)
        }
    }

    private fun hideBanner(wm: WindowManager) {
        banner?.let { runCatching { wm.removeView(it) } }
        banner = null
    }
}
