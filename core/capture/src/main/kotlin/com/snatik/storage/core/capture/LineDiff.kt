package com.snatik.storage.core.capture

enum class DiffOp { EQUAL, INSERT, DELETE }

data class DiffLine(val op: DiffOp, val text: String, val oldLine: Int?, val newLine: Int?)

/** Line diff with Myers' O(ND) algorithm. Falls back to replace-all on very large inputs. */
object LineDiff {

    private const val MAX_LINES = 20_000

    fun diff(oldText: String, newText: String): List<DiffLine> = diff(oldText.lines(), newText.lines())

    fun diff(a: List<String>, b: List<String>): List<DiffLine> {
        if (a.size + b.size > MAX_LINES) {
            return a.mapIndexed { i, s -> DiffLine(DiffOp.DELETE, s, i + 1, null) } + b.mapIndexed { i, s -> DiffLine(DiffOp.INSERT, s, null, i + 1) }
        }
        val n = a.size
        val m = b.size
        val max = n + m
        val offset = max
        val trace = ArrayList<IntArray>()
        var v = IntArray(2 * max + 2)
        var found = false
        loop@ for (d in 0..max) {
            trace += v.copyOf()
            for (k in -d..d step 2) {
                var x = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) v[offset + k + 1] else v[offset + k - 1] + 1
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) { x++; y++ }
                v[offset + k] = x
                if (x >= n && y >= m) { found = true; break@loop }
            }
        }
        if (!found) return emptyList()
        // Walk the trace backwards to recover the edit script.
        val ops = ArrayList<DiffLine>()
        var x = n
        var y = m
        for (d in trace.size - 1 downTo 0) {
            val vd = trace[d]
            val k = x - y
            val prevK = if (k == -d || (k != d && vd[offset + k - 1] < vd[offset + k + 1])) k + 1 else k - 1
            val prevX = vd[offset + prevK]
            val prevY = prevX - prevK
            while (x > prevX && y > prevY) {
                ops += DiffLine(DiffOp.EQUAL, a[x - 1], x, y)
                x--; y--
            }
            if (d > 0) {
                if (x == prevX) {
                    ops += DiffLine(DiffOp.INSERT, b[y - 1], null, y)
                    y--
                } else {
                    ops += DiffLine(DiffOp.DELETE, a[x - 1], x, null)
                    x--
                }
            }
        }
        return ops.asReversed()
    }
}
