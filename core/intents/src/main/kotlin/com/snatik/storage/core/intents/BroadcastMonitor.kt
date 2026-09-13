package com.snatik.storage.core.intents

import android.content.Intent
import android.os.Build

/** One broadcast the monitor received, with its full decoded intent (extras values included). */
data class BroadcastEvent(val id: Long, val time: Long, val spec: IntentSpec)

/** A broadcast action offered in the picker, with a friendly label and what it means. */
data class BroadcastAction(
    val action: String,
    val group: String,
    val label: String,
    val description: String,
    val dataScheme: String? = null,
)

/** Broadcasts any app may register a dynamic receiver for. Protected ones can be received, not sent. */
object BroadcastCatalog {
    val actions: List<BroadcastAction> = buildList {
        val power = "Power"
        add(BroadcastAction(Intent.ACTION_POWER_CONNECTED, power, "Charger connected", "External power was plugged in."))
        add(BroadcastAction(Intent.ACTION_POWER_DISCONNECTED, power, "Charger disconnected", "External power was unplugged."))
        add(BroadcastAction(Intent.ACTION_BATTERY_LOW, power, "Battery low", "The battery dropped to the low level."))
        add(BroadcastAction(Intent.ACTION_BATTERY_OKAY, power, "Battery okay", "The battery rose back above low."))
        add(BroadcastAction(Intent.ACTION_BATTERY_CHANGED, power, "Battery changed", "Level, temperature or charging state changed (sticky)."))
        val screen = "Screen and user"
        add(BroadcastAction(Intent.ACTION_SCREEN_ON, screen, "Screen on", "The display turned on."))
        add(BroadcastAction(Intent.ACTION_SCREEN_OFF, screen, "Screen off", "The display turned off."))
        add(BroadcastAction(Intent.ACTION_USER_PRESENT, screen, "Device unlocked", "The user unlocked the device."))
        add(BroadcastAction(Intent.ACTION_DREAMING_STARTED, screen, "Screensaver started", "The screensaver (daydream) began."))
        add(BroadcastAction(Intent.ACTION_DREAMING_STOPPED, screen, "Screensaver stopped", "The screensaver ended."))
        add(BroadcastAction(Intent.ACTION_CONFIGURATION_CHANGED, screen, "Configuration changed", "Orientation, locale, font scale or similar changed."))
        val packages = "Packages"
        add(BroadcastAction(Intent.ACTION_PACKAGE_ADDED, packages, "App installed", "A new app package was installed.", "package"))
        add(BroadcastAction(Intent.ACTION_PACKAGE_REMOVED, packages, "App removed", "An app package was uninstalled.", "package"))
        add(BroadcastAction(Intent.ACTION_PACKAGE_REPLACED, packages, "App updated", "An app was replaced with a new version.", "package"))
        add(BroadcastAction(Intent.ACTION_PACKAGE_CHANGED, packages, "App components changed", "An app's components were enabled or disabled.", "package"))
        add(BroadcastAction(Intent.ACTION_PACKAGE_FULLY_REMOVED, packages, "App fully removed", "An app and its data were completely removed.", "package"))
        val connectivity = "Connectivity"
        add(BroadcastAction("android.net.conn.CONNECTIVITY_CHANGE", connectivity, "Connectivity changed", "The active network connection changed."))
        add(BroadcastAction("android.net.wifi.WIFI_STATE_CHANGED", connectivity, "Wi-Fi state", "Wi-Fi was enabled, disabled or is changing."))
        add(BroadcastAction("android.net.wifi.STATE_CHANGE", connectivity, "Wi-Fi network state", "The Wi-Fi network's connection state changed."))
        add(BroadcastAction(Intent.ACTION_AIRPLANE_MODE_CHANGED, connectivity, "Airplane mode", "Airplane mode was toggled."))
        add(BroadcastAction("android.bluetooth.adapter.action.STATE_CHANGED", connectivity, "Bluetooth state", "Bluetooth was turned on or off."))
        add(BroadcastAction("android.bluetooth.device.action.ACL_CONNECTED", connectivity, "Bluetooth connected", "A Bluetooth device connected at the link level."))
        add(BroadcastAction("android.bluetooth.device.action.ACL_DISCONNECTED", connectivity, "Bluetooth disconnected", "A Bluetooth device disconnected."))
        val system = "System"
        add(BroadcastAction(Intent.ACTION_TIME_TICK, system, "Minute tick", "Fires once a minute; only to registered receivers."))
        add(BroadcastAction(Intent.ACTION_TIME_CHANGED, system, "Time set", "The system clock was set."))
        add(BroadcastAction(Intent.ACTION_TIMEZONE_CHANGED, system, "Time zone changed", "The time zone was changed."))
        add(BroadcastAction(Intent.ACTION_LOCALE_CHANGED, system, "Locale changed", "The device language or region changed."))
        add(BroadcastAction(Intent.ACTION_DEVICE_STORAGE_LOW, system, "Storage low", "Internal storage is running low."))
        add(BroadcastAction(Intent.ACTION_DEVICE_STORAGE_OK, system, "Storage okay", "Internal storage is no longer low."))
        add(BroadcastAction(Intent.ACTION_HEADSET_PLUG, system, "Headset plugged", "A wired headset was plugged or unplugged."))
        add(BroadcastAction(Intent.ACTION_INPUT_METHOD_CHANGED, system, "Keyboard changed", "The active input method (keyboard) changed."))
        add(BroadcastAction(Intent.ACTION_MEDIA_MOUNTED, system, "Media mounted", "External media (SD/USB) was mounted.", "file"))
        add(BroadcastAction(Intent.ACTION_MEDIA_UNMOUNTED, system, "Media unmounted", "External media was unmounted.", "file"))
        add(BroadcastAction(Intent.ACTION_MEDIA_BUTTON, system, "Media button", "A media button (play/pause) was pressed."))
        add(BroadcastAction("android.intent.action.PHONE_STATE", system, "Phone state", "The call state changed (ringing, off-hook, idle)."))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(BroadcastAction("android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED", system, "Channel block changed", "A notification channel was blocked or unblocked."))
    }
}
