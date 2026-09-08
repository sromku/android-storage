package com.snatik.storage.core.shell

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalShellExecutorTest {

    private val sh = LocalShellExecutor.plain()

    @Test
    fun capturesOutputAndExitCode() = runTest {
        val ok = sh.run("echo hello; echo oops 1>&2; exit 0")
        assertTrue(ok.ok)
        assertEquals("hello\n", ok.out)
        assertEquals("oops\n", ok.err)

        val bad = sh.run("exit 7")
        assertEquals(7, bad.exitCode)
        assertFalse(bad.ok)
    }

    @Test
    fun feedsStdin() = runTest {
        val result = sh.run("cat", stdin = "piped".toByteArray())
        assertEquals("piped", result.out)
    }

    @Test
    fun quotingSurvivesHostileNames() = runTest {
        val name = "it's a \"weird\" \$name `x`"
        val result = sh.run("printf %s ${name.shellQuote()}")
        assertEquals(name, result.out)
    }

    @Test
    fun timesOut() = runTest {
        val result = sh.run("sleep 5", timeoutMs = 300)
        assertEquals(-1, result.exitCode)
    }

    @Test
    fun streamsLines() = runTest {
        val lines = sh.lines("printf 'a\\nb\\nc\\n'").toList()
        assertEquals(listOf("a", "b", "c"), lines)
    }
}
