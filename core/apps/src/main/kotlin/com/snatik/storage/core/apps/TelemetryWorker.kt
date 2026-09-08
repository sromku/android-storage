package com.snatik.storage.core.apps

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Periodic snapshot job: records one device + per-app telemetry reading. */
class TelemetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            val db = TelemetryDatabase.create(applicationContext)
            TelemetryRepository(applicationContext, AppRepository(applicationContext), db).capture()
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
