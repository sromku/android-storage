package com.snatik.storage.core.intents

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class IntentSpecTest {

    @Test
    fun buildsAnIntentWithTypedExtras() {
        val spec = IntentSpec(
            action = Intent.ACTION_VIEW,
            data = "https://example.com/x",
            type = "text/html",
            categories = listOf(Intent.CATEGORY_BROWSABLE),
            packageName = "com.example",
            className = ".Main",
            flags = Intent.FLAG_ACTIVITY_NEW_TASK,
            extras = listOf(
                Extra("s", ExtraType.STRING, "hi"),
                Extra("i", ExtraType.INT, "42"),
                Extra("b", ExtraType.BOOLEAN, "true"),
                Extra("arr", ExtraType.STRING_ARRAY, "a\nb"),
            ),
        )
        val intent = spec.toIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com/x", intent.dataString)
        assertEquals("text/html", intent.type)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals("com.example.Main", intent.component!!.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("hi", intent.getStringExtra("s"))
        assertEquals(42, intent.getIntExtra("i", 0))
        assertTrue(intent.getBooleanExtra("b", false))
        assertEquals(listOf("a", "b"), intent.getStringArrayExtra("arr")!!.toList())
    }

    @Test
    fun describesAndRoundTripsThroughJson() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hello").putExtra("n", 7L)
        val spec = IntentSpec.describe(intent)
        assertEquals(Intent.ACTION_SEND, spec.action)
        assertEquals("text/plain", spec.type)
        assertEquals(Extra("android.intent.extra.TEXT", ExtraType.STRING, "hello"), spec.extras.first { it.key == Intent.EXTRA_TEXT })
        assertEquals(Extra("n", ExtraType.LONG, "7"), spec.extras.first { it.key == "n" })
        val again = IntentSpec.fromJson(spec.toJson())
        assertEquals(spec, again)
    }

    @Test
    fun namesFlags() {
        assertEquals(listOf("NEW_TASK", "CLEAR_TOP"), intentFlagNames(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }
}
