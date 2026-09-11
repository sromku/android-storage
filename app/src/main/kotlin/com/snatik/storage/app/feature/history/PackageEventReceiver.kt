package com.snatik.storage.app.feature.history

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snatik.storage.core.apps.AppEventLog
import com.snatik.storage.core.apps.AppEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Records package installs, updates and uninstalls into the app-event log. Registered in the
 * manifest for the (implicit-broadcast-exempt) PACKAGE_* actions, so events are captured even when
 * the app isn't running.
 */
class PackageEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        val type = when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> if (replacing) return else AppEventType.INSTALLED // the REPLACED event covers updates
            Intent.ACTION_PACKAGE_REPLACED -> AppEventType.UPDATED
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> AppEventType.UNINSTALLED
            else -> return
        }
        val pending = goAsync()
        val log = AppEventLog.from(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                log.record(pkg, type)
            } catch (_: Throwable) {
                // best-effort logging; never crash the receiver
            } finally {
                pending.finish()
            }
        }
    }
}
