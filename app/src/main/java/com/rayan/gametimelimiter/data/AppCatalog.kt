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
        }.getOrDefault(pkg)
    }

    fun icon(context: Context, pkg: String): ImageBitmap? = icons[pkg] ?: runCatching {
        context.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
    }.getOrNull()?.also { icons[pkg] = it }
}
