package com.snatik.storage.core.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentOutputParserTest {

    @Test
    fun parsesRows() {
        val out = """
            Row: 0 _id=1, name=Roman, note=NULL
            Row: 1 _id=2, name=Hello, world, note=has, commas
        """.trimIndent()
        val t = ProviderQuery.parseContentOutput(out)
        assertEquals(listOf("_id", "name", "note"), t.columns)
        assertEquals(2, t.rows.size)
        assertEquals(listOf("1", "Roman", null), t.rows[0])
        assertEquals("Hello, world", t.rows[1][1])
        assertEquals("has, commas", t.rows[1][2])
        assertNull(t.rows[0][2])
        assertEquals(Tabular.Source.SHELL, t.source)
    }

    @Test
    fun exportsCsvAndJson() {
        val t = Tabular(listOf("a", "b"), listOf(listOf("1", "x,y"), listOf(null, "q\"t")))
        assertEquals("a,b\n1,\"x,y\"\n,\"q\"\"t\"\n", t.toCsv())
        assertEquals("""[{"a":"1","b":"x,y"},{"a":null,"b":"q\"t"}]""", t.toJson())
    }
}
