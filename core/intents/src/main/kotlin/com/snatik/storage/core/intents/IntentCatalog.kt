package com.snatik.storage.core.intents

import android.content.Intent

/** One selectable value in a picker: the literal string plus a human explanation. */
data class IntentOption(val value: String, val title: String, val description: String, val group: String)

/** A settable Intent flag with what it actually does. */
data class FlagOption(val name: String, val bit: Int, val description: String, val group: String)

/** Description and a concrete example for one extra type. */
data class ExtraTypeInfo(val type: ExtraType, val label: String, val description: String, val example: String)

/**
 * Curated catalogs used to populate the builder's pickers. They describe what Android understands so a
 * value can be chosen with its meaning in view; every picker also accepts an arbitrary custom string.
 */

val ACTION_OPTIONS: List<IntentOption> = listOf(
    IntentOption(Intent.ACTION_VIEW, "View", "Display data to the user — a URL, a file, a geo point. The most common action.", "Common"),
    IntentOption(Intent.ACTION_EDIT, "Edit", "Open data for editing.", "Common"),
    IntentOption(Intent.ACTION_MAIN, "Main", "Entry point of an app; paired with a category like LAUNCHER.", "Common"),
    IntentOption(Intent.ACTION_SEND, "Send / Share", "Share a single piece of content. Set a MIME type and EXTRA_TEXT or EXTRA_STREAM.", "Share"),
    IntentOption(Intent.ACTION_SEND_MULTIPLE, "Send multiple", "Share several items at once via EXTRA_STREAM (an ArrayList of URIs).", "Share"),
    IntentOption(Intent.ACTION_SENDTO, "Send to", "Send to a specific recipient addressed by the data URI (mailto:, smsto:).", "Share"),
    IntentOption(Intent.ACTION_PICK, "Pick", "Let the user pick an item from data and return it.", "Content"),
    IntentOption(Intent.ACTION_GET_CONTENT, "Get content", "Let the user choose content of a given MIME type.", "Content"),
    IntentOption(Intent.ACTION_OPEN_DOCUMENT, "Open document", "Storage Access Framework picker returning a persistable document URI.", "Content"),
    IntentOption(Intent.ACTION_CREATE_DOCUMENT, "Create document", "Ask the user to name a new document; returns its URI.", "Content"),
    IntentOption(Intent.ACTION_OPEN_DOCUMENT_TREE, "Open document tree", "Grant access to a whole directory subtree.", "Content"),
    IntentOption(Intent.ACTION_INSERT, "Insert", "Insert a new empty item into the data.", "Content"),
    IntentOption(Intent.ACTION_DELETE, "Delete", "Delete the data pointed at by the URI.", "Content"),
    IntentOption(Intent.ACTION_DIAL, "Dial", "Open the dialer with a tel: number, without calling.", "Communication"),
    IntentOption(Intent.ACTION_CALL, "Call", "Place a call directly (needs CALL_PHONE permission).", "Communication"),
    IntentOption(Intent.ACTION_WEB_SEARCH, "Web search", "Search the web for EXTRA_QUERY.", "Communication"),
    IntentOption(Intent.ACTION_SEARCH, "Search", "In-app search for SearchManager.QUERY.", "Communication"),
    IntentOption("android.media.action.IMAGE_CAPTURE", "Capture photo", "Ask a camera app to take a picture.", "Media"),
    IntentOption("android.media.action.VIDEO_CAPTURE", "Capture video", "Ask a camera app to record a video.", "Media"),
    IntentOption("android.intent.action.MEDIA_SCANNER_SCAN_FILE", "Scan media file", "Ask MediaScanner to index a file at the data URI.", "Media"),
    IntentOption("android.intent.action.SET_ALARM", "Set alarm", "Create an alarm in the clock app.", "System"),
    IntentOption("android.intent.action.SET_TIMER", "Set timer", "Start a timer in the clock app.", "System"),
    IntentOption("android.intent.action.INSERT_OR_EDIT", "Insert or edit", "Edit an item if it exists, otherwise insert it.", "Content"),
    IntentOption(Intent.ACTION_APPLICATION_PREFERENCES, "App preferences", "Open the app's own settings screen.", "System"),
    IntentOption("android.settings.SETTINGS", "System settings", "Open the top-level Settings app.", "Settings"),
    IntentOption("android.settings.APPLICATION_DETAILS_SETTINGS", "App details settings", "Open a specific app's info page (data URI package:<name>).", "Settings"),
    IntentOption("android.settings.WIFI_SETTINGS", "Wi-Fi settings", "Open Wi-Fi settings.", "Settings"),
    IntentOption("android.settings.BLUETOOTH_SETTINGS", "Bluetooth settings", "Open Bluetooth settings.", "Settings"),
    IntentOption("android.settings.LOCATION_SOURCE_SETTINGS", "Location settings", "Open location settings.", "Settings"),
    IntentOption("android.settings.DEVELOPMENT_SETTINGS", "Developer options", "Open developer options.", "Settings"),
    IntentOption("android.settings.DATE_SETTINGS", "Date & time settings", "Open date and time settings.", "Settings"),
    IntentOption("android.settings.LOCALE_SETTINGS", "Language settings", "Open language and locale settings.", "Settings"),
)

val CATEGORY_OPTIONS: List<IntentOption> = listOf(
    IntentOption(Intent.CATEGORY_DEFAULT, "Default", "Matched by most implicit intents; add it when targeting an activity implicitly.", "Common"),
    IntentOption(Intent.CATEGORY_BROWSABLE, "Browsable", "Safe to start from a link in a browser.", "Common"),
    IntentOption(Intent.CATEGORY_LAUNCHER, "Launcher", "Appears in the launcher; paired with action MAIN.", "Common"),
    IntentOption(Intent.CATEGORY_HOME, "Home", "The home / launcher screen.", "Common"),
    IntentOption(Intent.CATEGORY_APP_BROWSER, "App: browser", "Launch the device's browser app.", "App"),
    IntentOption(Intent.CATEGORY_APP_EMAIL, "App: email", "Launch the email app.", "App"),
    IntentOption(Intent.CATEGORY_APP_MAPS, "App: maps", "Launch the maps app.", "App"),
    IntentOption(Intent.CATEGORY_APP_CONTACTS, "App: contacts", "Launch the contacts app.", "App"),
    IntentOption(Intent.CATEGORY_APP_CALENDAR, "App: calendar", "Launch the calendar app.", "App"),
    IntentOption(Intent.CATEGORY_APP_GALLERY, "App: gallery", "Launch the gallery app.", "App"),
    IntentOption(Intent.CATEGORY_OPENABLE, "Openable", "Only return content that can be opened as a stream.", "Content"),
    IntentOption(Intent.CATEGORY_PREFERENCE, "Preference", "A preferences panel activity.", "Content"),
)

val MIME_OPTIONS: List<IntentOption> = listOf(
    IntentOption("text/plain", "Plain text", "Unformatted text, e.g. for sharing.", "Text"),
    IntentOption("text/html", "HTML", "HTML markup.", "Text"),
    IntentOption("text/*", "Any text", "Any text subtype.", "Text"),
    IntentOption("image/*", "Any image", "Any image type.", "Image"),
    IntentOption("image/jpeg", "JPEG image", "JPEG image.", "Image"),
    IntentOption("image/png", "PNG image", "PNG image.", "Image"),
    IntentOption("video/*", "Any video", "Any video type.", "Media"),
    IntentOption("audio/*", "Any audio", "Any audio type.", "Media"),
    IntentOption("application/pdf", "PDF", "PDF document.", "Document"),
    IntentOption("application/json", "JSON", "JSON data.", "Document"),
    IntentOption("application/zip", "ZIP", "ZIP archive.", "Document"),
    IntentOption("application/octet-stream", "Binary", "Arbitrary binary data.", "Document"),
    IntentOption("*/*", "Any type", "Match any MIME type.", "Any"),
)

/** Scheme templates for the data URI. The value is a starting point the user completes. */
val SCHEME_OPTIONS: List<IntentOption> = listOf(
    IntentOption("https://", "Web (https)", "A secure web page.", "Web"),
    IntentOption("http://", "Web (http)", "A web page.", "Web"),
    IntentOption("tel:", "Phone number", "A telephone number, e.g. tel:+15551234.", "Communication"),
    IntentOption("mailto:", "Email", "An email address, e.g. mailto:you@example.com.", "Communication"),
    IntentOption("smsto:", "SMS", "An SMS recipient, e.g. smsto:5551234.", "Communication"),
    IntentOption("geo:", "Map point", "Coordinates or a query, e.g. geo:37.42,-122.08.", "Location"),
    IntentOption("content://", "Content URI", "A content-provider URI.", "Storage"),
    IntentOption("file://", "File URI", "A file path URI (limited on modern Android).", "Storage"),
    IntentOption("market://details?id=", "Play Store", "An app page, e.g. market://details?id=com.example.", "Store"),
    IntentOption("package:", "Package", "A package name, used by settings actions.", "System"),
)

/** Ordered so NEW_TASK precedes CLEAR_TOP (see IntentSpecTest). */
val FLAG_CATALOG: List<FlagOption> = listOf(
    FlagOption("NEW_TASK", Intent.FLAG_ACTIVITY_NEW_TASK, "Start the activity in a new task instead of the caller's. Required when starting from a non-activity context.", "Task"),
    FlagOption("CLEAR_TOP", Intent.FLAG_ACTIVITY_CLEAR_TOP, "If the activity is already running, clear everything above it rather than launching a new instance.", "Task"),
    FlagOption("SINGLE_TOP", Intent.FLAG_ACTIVITY_SINGLE_TOP, "Don't create a new instance if the activity is already at the top of the stack.", "Task"),
    FlagOption("CLEAR_TASK", Intent.FLAG_ACTIVITY_CLEAR_TASK, "Clear any existing task associated with the activity before it starts (with NEW_TASK).", "Task"),
    FlagOption("NO_HISTORY", Intent.FLAG_ACTIVITY_NO_HISTORY, "The activity is not kept in history once the user leaves it.", "Launch"),
    FlagOption("EXCLUDE_FROM_RECENTS", Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS, "Keep the new activity out of the recent-apps list.", "Launch"),
    FlagOption("MULTIPLE_TASK", Intent.FLAG_ACTIVITY_MULTIPLE_TASK, "Allow a new, separate task even if one already exists (with NEW_TASK).", "Task"),
    FlagOption("NO_ANIMATION", Intent.FLAG_ACTIVITY_NO_ANIMATION, "Don't play the usual transition animation when starting.", "Launch"),
    FlagOption("REORDER_TO_FRONT", Intent.FLAG_ACTIVITY_REORDER_TO_FRONT, "Bring an already-running instance to the front instead of relaunching.", "Task"),
    FlagOption("GRANT_READ_URI", Intent.FLAG_GRANT_READ_URI_PERMISSION, "Grant the receiver temporary read access to the content URI in the data.", "URI grants"),
    FlagOption("GRANT_WRITE_URI", Intent.FLAG_GRANT_WRITE_URI_PERMISSION, "Grant the receiver temporary write access to the content URI in the data.", "URI grants"),
    FlagOption("GRANT_PERSISTABLE_URI", Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, "Allow the granted URI permission to persist across reboots.", "URI grants"),
    FlagOption("GRANT_PREFIX_URI", Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, "Grant applies to any URI that starts with the given one.", "URI grants"),
    FlagOption("INCLUDE_STOPPED_PACKAGES", Intent.FLAG_INCLUDE_STOPPED_PACKAGES, "Also deliver to apps that are in the stopped state.", "Delivery"),
    FlagOption("DEBUG_LOG_RESOLUTION", Intent.FLAG_DEBUG_LOG_RESOLUTION, "Print extra logging about how this intent is resolved.", "Delivery"),
)

/** Human readable flag names for the bits set in [flags]. */
fun intentFlagNames(flags: Int): List<String> = FLAG_CATALOG.filter { flags and it.bit != 0 }.map { it.name }

fun extraTypeInfo(): List<ExtraTypeInfo> = listOf(
    ExtraTypeInfo(ExtraType.STRING, "String", "Plain text value.", "hello"),
    ExtraTypeInfo(ExtraType.INT, "Int", "32-bit whole number.", "42"),
    ExtraTypeInfo(ExtraType.LONG, "Long", "64-bit whole number.", "1700000000000"),
    ExtraTypeInfo(ExtraType.FLOAT, "Float", "Single-precision decimal.", "3.14"),
    ExtraTypeInfo(ExtraType.DOUBLE, "Double", "Double-precision decimal.", "3.14159"),
    ExtraTypeInfo(ExtraType.BOOLEAN, "Boolean", "true or false.", "true"),
    ExtraTypeInfo(ExtraType.URI, "Uri", "A parsed content or file URI.", "content://…"),
    ExtraTypeInfo(ExtraType.STRING_ARRAY, "String array", "Several strings, one per line.", "a\\nb\\nc"),
)
