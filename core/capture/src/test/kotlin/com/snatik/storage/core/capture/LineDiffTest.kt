package com.snatik.storage.core.capture

import org.junit.Test
import kotlin.test.assertEquals

class LineDiffTest {

    private fun render(lines: List<DiffLine>) = lines.joinToString("\n") { l ->
        when (l.op) { DiffOp.EQUAL -> " "; DiffOp.INSERT -> "+"; DiffOp.DELETE -> "-" } + l.text
    }

    @Test
    fun findsInsertsDeletesAndKeepsContext() {
        val a = listOf("a", "b", "c", "d")
        val b = listOf("a", "x", "c", "d", "e")
        assertEquals(" a\n-b\n+x\n c\n d\n+e", render(LineDiff.diff(a, b)))
    }

    @Test
    fun identicalAndEmpty() {
        assertEquals(" a\n b", render(LineDiff.diff(listOf("a", "b"), listOf("a", "b"))))
        assertEquals("+a", render(LineDiff.diff(emptyList(), listOf("a"))))
        assertEquals("-a", render(LineDiff.diff(listOf("a"), emptyList())))
    }

    @Test
    fun lineNumbersFollowEachSide() {
        val d = LineDiff.diff(listOf("a", "b"), listOf("b", "c"))
        assertEquals(listOf(DiffLine(DiffOp.DELETE, "a", 1, null), DiffLine(DiffOp.EQUAL, "b", 2, 1), DiffLine(DiffOp.INSERT, "c", null, 2)), d)
    }
}
