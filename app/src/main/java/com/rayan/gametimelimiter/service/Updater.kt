package com.rayan.gametimelimiter.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Updates the app from the latest GitHub release. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/rayanMELZI/game-time-limiter--mobile/releases/latest"

    data class Release(val version: String, val apkUrl: String)

    data class Status(
        val current: String,
        val available: String? = null,
        val checking: Boolean = false,
        val installing: Boolean = false,
        /** Android asked the user to confirm the install (notification or dialog shown). */
        val waitingForUser: Boolean = false,
        val error: String? = null,
        val checkedAt: Long? = null,
    )

    private val _status = MutableStateFlow(Status(current = "?"))
    val status: StateFlow<Status> = _status
    private var latest: Release? = null

    fun init(context: Context) {
        if (_status.value.current == "?") _status.value = _status.value.copy(current = currentVersion(context))
    }

    private fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** "1.10.0" > "1.9.2" */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Asks GitHub for the latest release; returns it when it's newer than this app. */
    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        init(context)
        _status.value = _status.value.copy(checking = true, error = null)
        val result = runCatching {
            val conn = URL(LATEST).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = Json.parseToJsonElement(body).jsonObject
            val version = json["tag_name"]!!.jsonPrimitive.content.removePrefix("v")
            val apk = json["assets"]!!.jsonArray
                .map { it.jsonObject }
                .first { it["name"]!!.jsonPrimitive.content.endsWith(".apk") }["browser_download_url"]!!
                .jsonPrimitive.content
            Release(version, apk).takeIf { isNewer(it.version, _status.value.current) }
        }
        latest = result.getOrNull()
        _status.value = _status.value.copy(
            checking = false,
            available = latest?.version,
            checkedAt = if (result.isSuccess) System.currentTimeMillis() else _status.value.checkedAt,
            error = result.exceptionOrNull()?.let { "Couldn't check for updates." },
        )
        latest
    }

    /** Downloads the APK and hands it to Android's package installer. */
    suspend fun install(context: Context, release: Release? = latest): String? = withContext(Dispatchers.IO) {
        val target = release ?: check(context) ?: return@withContext "You already have the latest version."
        _status.value = _status.value.copy(installing = true, error = null)
        val error = runCatching {
            val apk = File(context.cacheDir, "update.apk")
            val conn = URL(target.apkUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.inputStream.use { input -> apk.outputStream().use { input.copyTo(it) } }

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                // Once this app installed itself, later updates can go through without a prompt.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val callback = PendingIntent.getBroadcast(
                    context, sessionId,
                    Intent(context, UpdateReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
        }.exceptionOrNull()?.let { "Update failed: ${it.message}" }
        if (error != null) _status.value = _status.value.copy(installing = false, error = error)
        error
    }

    fun onWaitingForUser() {
        _status.value = _status.value.copy(waitingForUser = true)
    }

    fun onFailed(message: String) {
        _status.value = _status.value.copy(installing = false, waitingForUser = false, error = message)
    }
}
