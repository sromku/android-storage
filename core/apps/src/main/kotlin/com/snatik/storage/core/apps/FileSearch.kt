package com.snatik.storage.core.apps

import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.shellQuote
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** One search hit: a file path, its size when known, and the matching line for content hits. */
data class SearchHit(
    val path: String,
    val size: Long = -1,
    val line: String? = null,
)

enum class SearchMode { NAME, CONTENT }

/**
 * Finds files by name or by their text content under a root. Uses shell `find`/`grep` when a
 * privileged executor is available so it can reach paths this process cannot, and falls back to
 * walking the routed [FileSystem] otherwise. Results stream in as they are found.
 */
class FileSearch(
    private val fs: FileSystem,
    private val privilege: PrivilegeManager,
) {
    fun search(root: String, query: String, mode: SearchMode, regex: Boolean = false, limit: Int = 500): Flow<SearchHit> = flow {
        val q = query.trim()
        if (q.isEmpty()) return@flow
        val executor = privilege.executor.value
        val rx = if (regex) runCatching { Regex(q) }.getOrNull() else null
        var count = 0
        if (executor != null) {
            when (mode) {
                SearchMode.NAME -> {
                    // -iname is case-insensitive; escape glob metacharacters in the query.
                    val glob = "*" + q.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?").replace("[", "\\[") + "*"
                    val cmd = "find ${root.shellQuote()} -iname ${glob.shellQuote()} 2>/dev/null | head -n $limit"
                    val result = executor.run(cmd, timeoutMs = 120_000)
                    result.out.lineSequence().filter { it.isNotBlank() }.forEach {
                        if (count++ >= limit) return@flow
                        emit(SearchHit(it.trim()))
                    }
                }
                SearchMode.CONTENT -> {
                    // -I skips binaries, -n line numbers, -r recurses. -F = literal; -E = extended regex.
                    val flags = if (regex) "-rInaE" else "-rInaF"
                    val cmd = "grep $flags -m 5 -e ${q.shellQuote()} ${root.shellQuote()} 2>/dev/null | head -n $limit"
                    val result = executor.run(cmd, timeoutMs = 180_000)
                    result.out.lineSequence().filter { it.isNotBlank() }.forEach { raw ->
                        if (count++ >= limit) return@flow
                        // grep -n prints path:lineno:text; split on the first two colons.
                        val firstColon = raw.indexOf(':')
                        val secondColon = if (firstColon >= 0) raw.indexOf(':', firstColon + 1) else -1
                        if (secondColon > firstColon && firstColon >= 0) {
                            emit(SearchHit(raw.substring(0, firstColon), line = raw.substring(secondColon + 1).trim().take(300)))
                        } else {
                            emit(SearchHit(raw.trim()))
                        }
                    }
                }
            }
        } else {
            // Unprivileged fallback: walk the routed file system.
            val lower = q.lowercase()
            fs.walk(root).collect { entry ->
                if (count >= limit) return@collect
                when (mode) {
                    SearchMode.NAME -> if (entry.path.substringAfterLast('/').lowercase().contains(lower)) {
                        count++; emit(SearchHit(entry.path, entry.size))
                    }
                    SearchMode.CONTENT -> if (entry.size in 1..2_000_000) {
                        val text = runCatching { String(fs.readBytes(entry.path, 0, 2_000_000)) }.getOrNull()
                        val hit = text?.lineSequence()?.firstOrNull { l -> if (rx != null) rx.containsMatchIn(l) else l.contains(q, ignoreCase = true) }
                        if (hit != null) { count++; emit(SearchHit(entry.path, entry.size, hit.trim().take(300))) }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
