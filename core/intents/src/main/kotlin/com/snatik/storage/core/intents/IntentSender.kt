package com.snatik.storage.core.intents

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

data class ResolvedTarget(
    val packageName: String,
    val className: String,
    val label: String,
    val priority: Int,
    val isDefault: Boolean,
    val exported: Boolean,
)

/** Sends intents on behalf of the builder and lists what would receive them. */
class IntentSender(private val context: Context) {

    /** Returns a short description of what happened. Throws on failure with the platform's message. */
    fun send(spec: IntentSpec): String {
        val intent = spec.toIntent()
        return when (spec.sendAs) {
            SendAs.ACTIVITY -> {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "activity"
            }
            SendAs.BROADCAST -> {
                context.sendBroadcast(intent)
                "broadcast"
            }
            SendAs.SERVICE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                "service"
            }
        }
    }

    fun chooser(spec: IntentSpec, title: String): Intent =
        Intent.createChooser(spec.toIntent(), title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Everything that matches the intent for its [SendAs] kind, highest priority first. */
    fun resolve(spec: IntentSpec): List<ResolvedTarget> {
        val pm = context.packageManager
        val intent = spec.toIntent()
        val infos: List<ResolveInfo> = when (spec.sendAs) {
            SendAs.ACTIVITY -> queryActivities(pm, intent)
            SendAs.BROADCAST -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(0)) else @Suppress("DEPRECATION") pm.queryBroadcastReceivers(intent, 0)
            SendAs.SERVICE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0)) else @Suppress("DEPRECATION") pm.queryIntentServices(intent, 0)
        }
        val default = if (spec.sendAs == SendAs.ACTIVITY) resolveDefault(pm, intent) else null
        return infos.map { info ->
            val component = info.activityInfo ?: info.serviceInfo
            val pkg = component?.packageName ?: ""
            val cls = component?.name ?: ""
            ResolvedTarget(
                packageName = pkg,
                className = cls,
                label = info.loadLabel(pm).toString(),
                priority = info.priority,
                isDefault = default != null && default.first == pkg && default.second == cls,
                exported = component?.exported ?: true,
            )
        }.sortedWith(compareByDescending<ResolvedTarget> { it.isDefault }.thenByDescending { it.priority }.thenBy { it.label })
    }

    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }

    private fun resolveDefault(pm: PackageManager, intent: Intent): Pair<String, String>? {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val a = info?.activityInfo ?: return null
        if (a.packageName == "android") return null // the resolver activity, not a real default
        return a.packageName to a.name
    }
}
