package com.snatik.storage.core.shell

import com.snatik.storage.StorageException
import com.snatik.storage.core.fs.FileKind
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Answers commands from a table so the parser can be tested without a device. */
private class FakeShell(private val answers: List<Pair<(String) -> Boolean, ShellResult>>) : ShellExecutor {
    override val tier = PrivilegeTier.SHIZUKU
    val commands = mutableListOf<String>()

    override suspend fun start(command: String): ShellProcess {
        commands += command
        val result = answers.firstOrNull { it.first(command) }?.second ?: ShellResult(127, ByteArray(0), "no answer for $command".toByteArray())
        return object : ShellProcess {
            override val stdin: OutputStream = ByteArrayOutputStream()
            override val stdout: InputStream = ByteArrayInputStream(result.stdout)
            override val stderr: InputStream = ByteArrayInputStream(result.stderr)
            override suspend fun waitFor(): Int = result.exitCode
            override fun kill() {}
        }
    }
}

private fun ok(out: String) = ShellResult(0, out.toByteArray(), ByteArray(0))
private fun fail(code: Int, err: String = "") = ShellResult(code, ByteArray(0), err.toByteArray())

class ShellFileSystemTest {

    @Test
    fun parsesStatLines() {
        val e = ShellFileSystem.parseStat("regular file|1234|1700000000|644|photo.jpg", "/data/x")!!
        assertEquals("/data/x/photo.jpg", e.path)
        assertEquals(1234, e.size)
        assertEquals(1700000000_000L, e.lastModified)
        assertTrue(e.canWrite)
        assertEquals(FileKind.IMAGE, e.kind)

        val d = ShellFileSystem.parseStat("directory|4096|1|555|cache", "/data/x")!!
        assertTrue(d.isDirectory)
        assertEquals(0, d.size)
        assertTrue(!d.canWrite)

        val hidden = ShellFileSystem.parseStat("regular empty file|0|1|600|.nomedia", "/x")!!
        assertTrue(hidden.isHidden)

        val withPipe = ShellFileSystem.parseStat("regular file|5|1|644|odd|name.txt", "/x")!!
        assertEquals("odd|name.txt", withPipe.name)

        assertNull(ShellFileSystem.parseStat("directory|4096|1|755|..", "/x"))
        assertNull(ShellFileSystem.parseStat("garbage", "/x"))
    }

    @Test
    fun listsDirectoryAndResolvesLinks() = runTest {
        val shell = FakeShell(
            listOf(
                ({ c: String -> c.contains("stat -L") } to ok("directory\nbroken\n")),
                ({ c: String -> c.contains("cd '/data/app'") } to ok(
                    "directory|4096|10|755|lib\nsymbolic link|20|11|777|link-to-dir\nsymbolic link|20|11|777|dangling\nregular file|99|12|644|base.apk\n",
                )),
            ),
        )
        val fs = ShellFileSystem(shell)
        val entries = fs.list("/data/app").sortedBy { it.name }
        assertEquals(listOf("base.apk", "dangling", "lib", "link-to-dir"), entries.map { it.name })
        assertTrue(entries.first { it.name == "link-to-dir" }.let { it.isSymlink && it.isDirectory })
        assertTrue(entries.first { it.name == "dangling" }.let { it.isSymlink && !it.isDirectory })
    }

    @Test
    fun listErrorsMapToStorageExceptions() = runTest {
        val fs = ShellFileSystem(FakeShell(listOf(({ c: String -> c.contains("/missing") } to fail(3)), ({ c: String -> c.contains("/file") } to fail(4)))))
        assertFailsWith<StorageException.NotFound> { fs.list("/missing") }
        assertFailsWith<StorageException.NotADirectory> { fs.list("/file") }
    }

    @Test
    fun statMissingIsNull() = runTest {
        val fs = ShellFileSystem(FakeShell(listOf(({ _: String -> true } to fail(1, "stat: '/nope': No such file or directory")))))
        assertNull(fs.stat("/nope"))
    }

    @Test
    fun createFileRefusesExisting() = runTest {
        val fs = ShellFileSystem(FakeShell(listOf(({ c: String -> c.startsWith("[ -e") } to fail(3)))))
        assertFailsWith<StorageException.AlreadyExists> { fs.createFile("/data/x") }
    }

    @Test
    fun quotesPathsInCommands() = runTest {
        val shell = FakeShell(listOf(({ _: String -> true } to ok("regular file|1|1|644|a b\n"))))
        val fs = ShellFileSystem(shell)
        fs.stat("/data/it's/a b")
        assertTrue(shell.commands.single().contains("'/data/it'\\''s/a b'"))
    }
}
