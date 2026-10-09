package com.rayan.gametimelimiter.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.rayan.gametimelimiter.ui.MainActivity

/** Result of an update install session. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Updater.onWaitingForUser()
                if (MainActivity.visible) {
                    context.startActivity(confirm)
                } else {
                    // Don't pop a dialog over whatever the user is doing: let them tap when ready.
                    val tap = PendingIntent.getActivity(context, 1, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    Alerts.updateReady(context, tap)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // The app restarts as the new version (see BootReceiver).
            else -> {
                if (status == PackageInstaller.STATUS_FAILURE_ABORTED) Updater.onFailed("Update cancelled.")
                else Updater.onFailed("Update failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: status}")
            }
        }
    }
}
