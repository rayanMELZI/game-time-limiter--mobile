package com.rayan.gametimelimiter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rayan.gametimelimiter.data.Store

/** Restarts the limiter after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Store.init(context)
        if (Permissions.hasRequired(context)) LimiterService.start(context)
    }
}
