package com.snatik.storage.app.feature.timemachine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snatik.storage.core.apps.TelemetryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Restarts the storage recorder after a reboot when the user had it enabled. */
class TelemetryBootReceiver : BroadcastReceiver(), KoinComponent {
    private val repo: TelemetryRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && repo.enabled) {
            TelemetryRecorderService.start(context)
        }
    }
}
