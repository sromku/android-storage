package com.snatik.storage.app.feature.intents

import com.snatik.storage.core.intents.Extra
import com.snatik.storage.core.intents.ExtraType
import com.snatik.storage.core.intents.IntentSpec

/** Ready-to-run example intents to load into the builder and try. */
object IntentExamples {
    data class Example(val name: String, val summary: String, val spec: IntentSpec)

    val list: List<Example> = listOf(
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
        Example(
            "Plain-text note to any editor", "ACTION_VIEW · text/plain",
            IntentSpec(action = "android.intent.action.VIEW", type = "text/plain", data = "content://"),
        ),
    )
}
