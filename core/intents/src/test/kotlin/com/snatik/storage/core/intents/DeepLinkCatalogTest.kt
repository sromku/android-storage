package com.snatik.storage.core.intents

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepLinkCatalogTest {

    @Test
    fun parsesSchemesAndWebLinks() {
        val dump = """
            Activity Resolver Table:
              Schemes:
                  googlehome:
                    66331c5 com.google.android.apps.chromecast.app/.deeplink.DeeplinkActivity
                    66331c5 com.google.android.apps.chromecast.app/.deeplink.DeeplinkActivity
                  spotify:
                    a1b2c3d com.spotify.music/.MainActivity
                  http:
                    dead com.android.chrome/org.chromium.Main
                  https:
                    beef com.android.chrome/org.chromium.Main
              Non-Data Actions:
                  android.intent.action.MAIN:
                    1111 com.example/.Main
            Domain verification status:
              com.google.android.youtube:
                ID: 03d61cbf-0ff2-4b6a-acc1-033bb72b6c50
                Signatures: [5A:AD]
                Domain verification state:
                  youtu.be: system_configured
                  youtube.com: system_configured
                User all:
                  Verification link handling allowed: true
              com.example.app:
                Domain verification state:
                  example.com: verified
                  notyet.com: none
        """.trimIndent()

        val data = DeepLinkCatalog.parse(dump)

        // Custom schemes only (http/https excluded), each with its handling package(s), deduped.
        assertEquals(listOf("googlehome", "spotify"), data.schemes.map { it.scheme })
        assertEquals(listOf("com.google.android.apps.chromecast.app"), data.schemes.first { it.scheme == "googlehome" }.packages)

        // Only approved domains (verified / system_configured), not "none".
        val domains = data.webLinks.map { it.domain }
        assertTrue("youtu.be" in domains && "youtube.com" in domains && "example.com" in domains)
        assertTrue("notyet.com" !in domains)
        assertEquals("com.google.android.youtube", data.webLinks.first { it.domain == "youtu.be" }.packageName)
    }
}
