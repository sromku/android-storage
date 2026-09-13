package com.snatik.storage.core.intents

import android.content.Intent
import android.os.Build

/** One broadcast the monitor received, with its full decoded intent (extras values included). */
data class BroadcastEvent(val id: Long, val time: Long, val spec: IntentSpec)

/** A broadcast action offered in the picker; [dataScheme] is added to the filter when set. */
data class BroadcastAction(val action: String, val group: String, val dataScheme: String? = null)

/** Broadcasts any app may register a dynamic receiver for. Protected ones can be received, not sent. */
object BroadcastCatalog {
    val actions: List<BroadcastAction> = buildList {
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
