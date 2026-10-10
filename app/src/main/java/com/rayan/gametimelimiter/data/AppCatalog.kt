package com.rayan.gametimelimiter.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(val pkg: String, val label: String, val weeklyMinutes: Long)

/** Installed apps, their labels and icons (cached). */
object AppCatalog {
    private val icons = ConcurrentHashMap<String, ImageBitmap>()
    private val labels = ConcurrentHashMap<String, String>()

    /** Launchable apps, most used this week first (so games float to the top). */
    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val now = System.currentTimeMillis()
        val usage = runCatching {
            context.getSystemService(UsageStatsManager::class.java)
                .queryAndAggregateUsageStats(now - 7 * 24 * 3600_000L, now)
                .mapValues { it.value.totalTimeInForeground / 60_000 }
        }.getOrDefault(emptyMap())

        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != context.packageName }
            .onEach { labels[it.first] = it.second }
            .map { (pkg, label) -> InstalledApp(pkg, label, usage[pkg] ?: 0) }
            .sortedWith(compareByDescending<InstalledApp> { it.weeklyMinutes }.thenBy { it.label.lowercase() })
    }

    fun label(context: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(nameFromPackage(pkg))
    }

    private val GENERIC = setOf("android", "app", "apps", "mobile", "client", "main", "free", "lite", "pro", "global", "messenger", "katana", "orca")
    private val KNOWN = mapOf(
        "com.facebook.katana" to "Facebook", "com.facebook.orca" to "Messenger", "org.telegram.messenger" to "Telegram",
        "com.instagram.android" to "Instagram", "com.whatsapp" to "WhatsApp", "com.zhiliaoapp.musically" to "TikTok",
        "com.snapchat.android" to "Snapchat", "com.twitter.android" to "X", "com.discord" to "Discord",
    )

    /** Readable name for apps that aren't installed anymore, e.g. "com.anonimchat" -> "Anonimchat". */
    private fun nameFromPackage(pkg: String): String {
        KNOWN[pkg]?.let { return it }
        val parts = pkg.split('.').filter { it.isNotEmpty() && it !in setOf("com", "org", "net", "io", "app") }
        val word = parts.lastOrNull { it.lowercase() !in GENERIC } ?: parts.lastOrNull() ?: pkg
        return word.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun icon(context: Context, pkg: String): ImageBitmap? = icons[pkg] ?: runCatching {
        context.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
    }.getOrNull()?.also { icons[pkg] = it }
}
