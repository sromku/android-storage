package com.snatik.storage.core.fs

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileKindTest {

    @Test
    fun classifiesByExtension() {
        assertEquals(FileKind.DIRECTORY, FileKind.of("anything.jpg", isDirectory = true))
        assertEquals(FileKind.IMAGE, FileKind.of("Photo.JPG", isDirectory = false))
        assertEquals(FileKind.VIDEO, FileKind.of("clip.mp4", isDirectory = false))
        assertEquals(FileKind.CODE, FileKind.of("Main.kt", isDirectory = false))
        assertEquals(FileKind.JSON, FileKind.of("config.json", isDirectory = false))
        assertEquals(FileKind.APK, FileKind.of("app-debug.apk", isDirectory = false))
        assertEquals(FileKind.DATABASE, FileKind.of("app.db", isDirectory = false))
        assertEquals(FileKind.ARCHIVE, FileKind.of("lib.aar", isDirectory = false))
        assertEquals(FileKind.OTHER, FileKind.of("README", isDirectory = false))
        assertEquals(FileKind.OTHER, FileKind.of("weird.zzz", isDirectory = false))
        assertTrue(FileKind.JSON.isTextLike)
    }
}
