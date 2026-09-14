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

    @Test
    fun parsesAppLinkHostsAndPaths() {
        val dump = """
            Activity Resolver Table:
              Full MIME Types:
              http:
                4d98575 com.google.android.youtube/.UrlActivity filter 4c5a90a
                  Action: "android.intent.action.VIEW"
                  Action: "android.media.action.MEDIA_PLAY_FROM_SEARCH"
                  Category: "android.intent.category.DEFAULT"
                  Category: "android.intent.category.BROWSABLE"
                  Scheme: "http"
                  Scheme: "https"
                  Authority: "youtube.com": -1
                  Authority: "youtu.be": -1
                  Path: "PatternMatcher{GLOB: .*}"
                  AutoVerify=true
                4d98575 com.google.android.youtube/.UrlActivity filter 8ca03f1
                  Action: "android.intent.action.VIEW"
                  Category: "android.intent.category.DEFAULT"
                  Category: "android.intent.category.BROWSABLE"
                  Scheme: "https"
                  Authority: "studio.youtube.com": -1
                  Path: "PatternMatcher{GLOB: /channel/UC.*/promotions.*}"
                  Path: "PatternMatcher{LITERAL: /channel-appeal}"
                  Path: "PatternMatcher{PREFIX: /watch}"
                  AutoVerify=true
                deadbeef com.example/.NotBrowsable filter 1
                  Action: "android.intent.action.VIEW"
                  Category: "android.intent.category.DEFAULT"
                  Scheme: "https"
                  Authority: "internal.example.com": -1
        """.trimIndent()

        val hosts = DeepLinkCatalog.parseAppLinks(dump)

        // Non-browsable filter excluded.
        assertTrue(hosts.none { it.host == "internal.example.com" })
        // Bare host is always offered.
        assertEquals(listOf(""), hosts.first { it.host == "youtube.com" }.paths)
        val studio = hosts.first { it.host == "studio.youtube.com" }.paths
        assertTrue("" in studio)                 // root
        assertTrue("/channel-appeal" in studio)  // literal
        assertTrue("/watch" in studio)           // prefix
        assertTrue("/channel/UC" in studio)      // glob fixed prefix
    }

    @Test
    fun cleansPlaceholdersButKeepsEncoding() {
        // Trailing/embedded printf placeholders go; dangling separators trimmed.
        assertEquals("app://settings/device/matterRemoveDevice?hgs_device_id=&matter_instance_name=",
            DeepLinkCatalog.cleanExampleUri("app://settings/device/matterRemoveDevice?hgs_device_id=%s&matter_instance_name=%s"))
        assertEquals("app://open?ref=", DeepLinkCatalog.cleanExampleUri("app://open?ref=%s"))
        assertEquals("app://home/", DeepLinkCatalog.cleanExampleUri("app://home/%s"))
        // Real percent-encoding must survive (not a format specifier).
        assertEquals("app://q?path=%2Fhome%20page", DeepLinkCatalog.cleanExampleUri("app://q?path=%2Fhome%20page"))
    }
}
