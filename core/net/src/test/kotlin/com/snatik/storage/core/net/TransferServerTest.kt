package com.snatik.storage.core.net

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.snatik.storage.Storage
import com.snatik.storage.core.fs.LocalFileSystem
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TransferServerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun server(): TransferServer {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TransferServer(LocalFileSystem(Storage(context)), File(temp.root, "inbox"), "Test device", "0.1")
    }

    @Test
    fun uploadsWithResumeAndListsAndDownloads() = testApplication {
        val server = server()
        val code = server.code
        application { server.module(this) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/files").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/info").status)
        assertContains(client.get("/?code=$code").bodyAsText(), "Send to Test device")

        // First half, then resume the rest.
        val payload = ByteArray(5000) { (it % 251).toByte() }
        val first = client.put("/api/upload/data.bin?offset=0&complete=false") { header("X-Code", code); setBody(payload.copyOfRange(0, 3000)) }
        assertEquals(HttpStatusCode.OK, first.status)
        assertContains(first.bodyAsText(), "\"size\":3000")
        assertContains(client.get("/api/upload/data.bin") { header("X-Code", code) }.bodyAsText(), "\"size\":3000")
        val second = client.put("/api/upload/data.bin?offset=3000") { header("Authorization", "Bearer $code"); setBody(payload.copyOfRange(3000, 5000)) }
        assertEquals(HttpStatusCode.OK, second.status)
        val stored = File(temp.root, "inbox/data.bin")
        assertTrue(stored.readBytes().contentEquals(payload))
        assertEquals(1, server.state.value.received.size)

        // An offset past the end is refused so the sender falls back to a fresh upload.
        assertEquals(HttpStatusCode.Conflict, client.put("/api/upload/data.bin?offset=9000") { header("X-Code", code); setBody(ByteArray(1)) }.status)

        val listing = client.get("/api/files?code=$code").bodyAsText()
        assertContains(listing, "\"name\":\"data.bin\"")
        val download = client.get("/api/download?path=${stored.absolutePath}") { header("X-Code", code) }
        assertEquals(HttpStatusCode.OK, download.status)
        assertTrue(download.bodyAsBytes().contentEquals(payload))
        val ranged = client.get("/api/download?path=${stored.absolutePath}") { header("X-Code", code); header("Range", "bytes=0-9") }
        assertEquals(HttpStatusCode.PartialContent, ranged.status)
        assertEquals(10, ranged.bodyAsBytes().size)
    }

    @Test
    fun rejectsPathTricksInNames() {
        assertEquals("evil.txt", TransferServer.safeName("../../evil.txt"))
        assertEquals(null, TransferServer.safeName(".."))
        assertEquals(null, TransferServer.safeName(""))
        assertEquals(6, TransferServer.newCode().length)
    }
}
