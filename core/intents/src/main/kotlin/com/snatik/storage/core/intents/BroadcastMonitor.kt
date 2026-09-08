package com.snatik.storage.core.intents

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BroadcastEvent(val id: Long, val time: Long, val spec: IntentSpec)

data class BroadcastAction(val action: String, val group: String, val dataScheme: String? = null)

/**
 * Registers dynamic receivers for a set of actions and keeps the last few hundred broadcasts
 * that arrived, newest first. Runs only while the app process lives.
 */
class BroadcastMonitor(private val context: Context) {

    private val _events = MutableStateFlow<List<BroadcastEvent>>(emptyList())
    val events: StateFlow<List<BroadcastEvent>> = _events.asStateFlow()

    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    private val receivers = HashMap<String, BroadcastReceiver>()

    fun setActive(action: String, on: Boolean, dataScheme: String? = null) {
        synchronized(receivers) {
            if (on && !receivers.containsKey(action)) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        val event = BroadcastEvent(System.nanoTime(), System.currentTimeMillis(), IntentSpec.describe(intent))
                        _events.update { (listOf(event) + it).take(MAX) }
                    }
                }
                val filter = IntentFilter(action).apply { dataScheme?.let { addDataScheme(it) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(receiver, filter)
                }
                receivers[action] = receiver
            } else if (!on) {
                receivers.remove(action)?.let { runCatching { context.unregisterReceiver(it) } }
            }
            _active.value = receivers.keys.toSet()
        }
    }

    fun stopAll() {
        synchronized(receivers) {
            receivers.values.forEach { runCatching { context.unregisterReceiver(it) } }
            receivers.clear()
            _active.value = emptySet()
        }
    }

    fun clear() {
        _events.value = emptyList()
    }

    companion object {
        private const val MAX = 500

        /** Broadcasts any app may listen to. Protected ones can be received, only not sent, by normal apps. */
        val CATALOGUE: List<BroadcastAction> = buildList {
            val power = "Power"
            add(BroadcastAction(Intent.ACTION_POWER_CONNECTED, power))
            add(BroadcastAction(Intent.ACTION_POWER_DISCONNECTED, power))
            add(BroadcastAction(Intent.ACTION_BATTERY_LOW, power))
            add(BroadcastAction(Intent.ACTION_BATTERY_OKAY, power))
            add(BroadcastAction(Intent.ACTION_BATTERY_CHANGED, power))
            val screen = "Screen and user"
            add(BroadcastAction(Intent.ACTION_SCREEN_ON, screen))
            add(BroadcastAction(Intent.ACTION_SCREEN_OFF, screen))
            add(BroadcastAction(Intent.ACTION_USER_PRESENT, screen))
            add(BroadcastAction(Intent.ACTION_DREAMING_STARTED, screen))
            add(BroadcastAction(Intent.ACTION_DREAMING_STOPPED, screen))
            add(BroadcastAction(Intent.ACTION_CONFIGURATION_CHANGED, screen))
            val packages = "Packages"
            add(BroadcastAction(Intent.ACTION_PACKAGE_ADDED, packages, "package"))
            add(BroadcastAction(Intent.ACTION_PACKAGE_REMOVED, packages, "package"))
            add(BroadcastAction(Intent.ACTION_PACKAGE_REPLACED, packages, "package"))
            add(BroadcastAction(Intent.ACTION_PACKAGE_CHANGED, packages, "package"))
            add(BroadcastAction(Intent.ACTION_PACKAGE_FULLY_REMOVED, packages, "package"))
            val connectivity = "Connectivity"
            add(BroadcastAction("android.net.conn.CONNECTIVITY_CHANGE", connectivity))
            add(BroadcastAction("android.net.wifi.WIFI_STATE_CHANGED", connectivity))
            add(BroadcastAction("android.net.wifi.STATE_CHANGE", connectivity))
            add(BroadcastAction(Intent.ACTION_AIRPLANE_MODE_CHANGED, connectivity))
            add(BroadcastAction("android.bluetooth.adapter.action.STATE_CHANGED", connectivity))
            add(BroadcastAction("android.bluetooth.device.action.ACL_CONNECTED", connectivity))
            add(BroadcastAction("android.bluetooth.device.action.ACL_DISCONNECTED", connectivity))
            val system = "System"
            add(BroadcastAction(Intent.ACTION_TIME_TICK, system))
            add(BroadcastAction(Intent.ACTION_TIME_CHANGED, system))
            add(BroadcastAction(Intent.ACTION_TIMEZONE_CHANGED, system))
            add(BroadcastAction(Intent.ACTION_LOCALE_CHANGED, system))
            add(BroadcastAction(Intent.ACTION_DEVICE_STORAGE_LOW, system))
            add(BroadcastAction(Intent.ACTION_DEVICE_STORAGE_OK, system))
            add(BroadcastAction(Intent.ACTION_HEADSET_PLUG, system))
            add(BroadcastAction(Intent.ACTION_INPUT_METHOD_CHANGED, system))
            add(BroadcastAction(Intent.ACTION_MEDIA_MOUNTED, system, "file"))
            add(BroadcastAction(Intent.ACTION_MEDIA_UNMOUNTED, system, "file"))
            add(BroadcastAction(Intent.ACTION_MEDIA_BUTTON, system))
            add(BroadcastAction("android.intent.action.PHONE_STATE", system))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(BroadcastAction("android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED", system))
        }
    }
}
