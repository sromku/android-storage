package com.snatik.storage.core.apps

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BinaryXmlTest {

    private val manifest: ByteArray = javaClass.getResourceAsStream("/storage-app-manifest.axml")!!.readBytes()

    @Test
    fun decodesTheAppManifest() {
        val xml = BinaryXml(manifest).decode()
        assertTrue(xml.startsWith("<?xml"))
        assertContains(xml, "<manifest")
        assertContains(xml, "xmlns:android=\"http://schemas.android.com/apk/res/android\"")
        assertContains(xml, "package=\"com.snatik.storage.app\"")
        assertContains(xml, "android:name=\"android.permission.MANAGE_EXTERNAL_STORAGE\"")
        assertContains(xml, "<activity")
        assertContains(xml, "android:exported=\"true\"")
        assertContains(xml, "android.intent.action.MAIN")
        assertContains(xml, "rikka.shizuku.ShizukuProvider")
        assertContains(xml, "</manifest>")
        // Nesting is reflected by indentation.
        assertContains(xml, "\n    <application")
        assertContains(xml, "\n        <activity")
    }

    @Test
    fun resolvesReferencesThroughTheResolver() {
        val xml = BinaryXml(manifest) { id -> if (id ushr 24 == 0x7f) "mipmap/ic_launcher" else null }.decode()
        assertContains(xml, "android:icon=\"@mipmap/ic_launcher\"")
        val raw = BinaryXml(manifest).decode()
        assertContains(raw, "android:icon=\"@0x7f")
    }

    @Test
    fun rejectsNonXml() {
        assertFailsWith<IllegalArgumentException> { BinaryXml(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)).decode() }
    }
}
