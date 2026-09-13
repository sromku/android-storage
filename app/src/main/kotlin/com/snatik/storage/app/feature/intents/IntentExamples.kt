package com.snatik.storage.app.feature.intents

import com.snatik.storage.core.intents.Extra
import com.snatik.storage.core.intents.ExtraType
import com.snatik.storage.core.intents.IntentSpec
import com.snatik.storage.core.intents.SendAs

/** Ready-to-run example intents to load into the builder and try. */
object IntentExamples {
    data class Example(val name: String, val summary: String, val spec: IntentSpec)

    /** Started as activities — the everyday "open something" intents. */
    val activities: List<Example> = listOf(
        Example(
            "Open a web page", "ACTION_VIEW · https://",
            IntentSpec(action = "android.intent.action.VIEW", data = "https://example.com"),
        ),
        Example(
            "Share text", "ACTION_SEND · text/plain",
            IntentSpec(
                action = "android.intent.action.SEND", type = "text/plain",
                extras = listOf(Extra("android.intent.extra.TEXT", ExtraType.STRING, "Hello from Storage")),
            ),
        ),
        Example(
            "Web search", "ACTION_WEB_SEARCH",
            IntentSpec(
                action = "android.intent.action.WEB_SEARCH",
                extras = listOf(Extra("query", ExtraType.STRING, "android intents")),
            ),
        ),
        Example(
            "Dial a number", "ACTION_DIAL · tel:",
            IntentSpec(action = "android.intent.action.DIAL", data = "tel:+15551234567"),
        ),
        Example(
            "Compose email", "ACTION_SENDTO · mailto:",
            IntentSpec(
                action = "android.intent.action.SENDTO", data = "mailto:hello@example.com",
                extras = listOf(
                    Extra("android.intent.extra.SUBJECT", ExtraType.STRING, "Hi"),
                    Extra("android.intent.extra.TEXT", ExtraType.STRING, "Sent from Storage"),
                ),
            ),
        ),
        Example(
            "Show a location", "ACTION_VIEW · geo:",
            IntentSpec(action = "android.intent.action.VIEW", data = "geo:0,0?q=Golden Gate Bridge"),
        ),
        Example(
            "Set an alarm", "ACTION_SET_ALARM",
            IntentSpec(
                action = "android.intent.action.SET_ALARM",
                extras = listOf(
                    Extra("android.intent.extra.alarm.HOUR", ExtraType.INT, "7"),
                    Extra("android.intent.extra.alarm.MINUTES", ExtraType.INT, "30"),
                    Extra("android.intent.extra.alarm.MESSAGE", ExtraType.STRING, "Wake up"),
                ),
            ),
        ),
        Example(
            "Capture a photo", "ACTION_IMAGE_CAPTURE",
            IntentSpec(action = "android.media.action.IMAGE_CAPTURE"),
        ),
        Example(
            "Open Wi-Fi settings", "android.settings.WIFI_SETTINGS",
            IntentSpec(action = "android.settings.WIFI_SETTINGS"),
        ),
    )

    /** Sent as broadcasts. Some need a matching receiver, but they show the shape. */
    val broadcasts: List<Example> = listOf(
        Example(
            "Scan a media file", "ACTION_MEDIA_SCANNER_SCAN_FILE",
            IntentSpec(
                action = "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                data = "file:///sdcard/Download/",
                sendAs = SendAs.BROADCAST,
            ),
        ),
        Example(
            "Custom broadcast", "com.example.action.PING",
            IntentSpec(
                action = "com.example.action.PING",
                extras = listOf(Extra("message", ExtraType.STRING, "hello")),
                sendAs = SendAs.BROADCAST,
            ),
        ),
        Example(
            "Broadcast to one app", "package + action",
            IntentSpec(
                action = "com.example.action.SYNC",
                packageName = "com.example.app",
                sendAs = SendAs.BROADCAST,
            ),
        ),
    )

    /** Started as services. Component-targeted, so fill in a real package and class. */
    val services: List<Example> = listOf(
        Example(
            "Start a service (component)", "package + class",
            IntentSpec(
                packageName = "com.example.app",
                className = "com.example.app.MyService",
                sendAs = SendAs.SERVICE,
            ),
        ),
        Example(
            "Start a service (action)", "com.example.action.SYNC",
            IntentSpec(
                action = "com.example.action.SYNC",
                packageName = "com.example.app",
                sendAs = SendAs.SERVICE,
            ),
        ),
    )

    val all: List<Example> = activities + broadcasts + services
}
