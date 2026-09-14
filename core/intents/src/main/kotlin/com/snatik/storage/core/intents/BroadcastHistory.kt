package com.snatik.storage.core.intents

import com.snatik.storage.core.shell.ShellExecutor
import com.snatik.storage.core.shell.run

data class HistoricalBroadcast(val queue: String, val intent: String, val action: String?, val enqueued: String?)

/**
 * Recent broadcasts as reported by `dumpsys activity broadcasts history`, which needs the
 * shell's DUMP permission.
 */
class BroadcastHistory {

    suspend fun fetchRaw(shell: ShellExecutor): String {
        val result = shell.run("dumpsys activity broadcasts history", timeoutMs = 60_000)
        if (!result.ok && result.out.isBlank()) throw IllegalStateException(result.err.ifBlank { "dumpsys failed (${result.exitCode})" })
        return result.out
    }

    companion object {
        // Legacy releases group records under "[foreground]" / "[background]" sections; the modern
        // per-process queue has no such header and instead each record is a BroadcastRecord{…} block.
        private val queueHeader = Regex("""Historical broadcasts \[(\w+)\]""")
        // A record boundary in either format: the legacy per-record header or a BroadcastRecord line.
        private val recordBoundary = Regex("""Historical Broadcast \w+ #\d+|BroadcastRecord\{""")
        private val intentLine = Regex("""(Intent \{[^{}]*\})""")
        private val actionInIntent = Regex("""act=([^\s\}]+)""")
        private val flagsInIntent = Regex("""flg=0x([0-9a-fA-F]+)""")
        private val enqueueLine = Regex("""(?:enqueueClockTime|enqueueTime|enqueued)[=:]?\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?:\.\d+)?)""")
        private const val FLAG_RECEIVER_FOREGROUND = 0x10000000L

        /**
         * Pull the broadcasts out of the dump in order, tolerant of the format differences across
         * releases. One record per boundary, so only the first Intent line in a block counts — the
         * `reason: … Intent {…}` lines in the modern format are ignored. The foreground/background
         * queue comes from the section header when present, else from FLAG_RECEIVER_FOREGROUND.
         */
        fun parse(text: String): List<HistoricalBroadcast> {
            val out = ArrayList<HistoricalBroadcast>()
            var sectionQueue: String? = null
            var intent: String? = null
            var action: String? = null
            var recordQueue: String? = null
            var flagQueue: String? = null
            var enqueued: String? = null

            fun flush() {
                intent?.let { out += HistoricalBroadcast(recordQueue ?: flagQueue ?: "?", it, action, enqueued) }
                intent = null; action = null; recordQueue = null; flagQueue = null; enqueued = null
            }

            for (line in text.lineSequence()) {
                queueHeader.find(line)?.let { sectionQueue = it.groupValues[1] }
                if (recordBoundary.containsMatchIn(line)) {
                    if (intent != null) flush() else { action = null; recordQueue = null; flagQueue = null; enqueued = null }
                    continue
                }
                if (intent == null) {
                    intentLine.find(line)?.let { m ->
                        val value = m.groupValues[1]
                        intent = value
                        action = actionInIntent.find(value)?.groupValues?.get(1)
                        recordQueue = sectionQueue
                        flagQueue = flagsInIntent.find(value)?.groupValues?.get(1)?.toLongOrNull(16)?.let {
                            if (it and FLAG_RECEIVER_FOREGROUND != 0L) "foreground" else "background"
                        }
                    }
                }
                if (intent != null) enqueueLine.find(line)?.let { enqueued = it.groupValues[1] }
            }
            flush()
            return out
        }
    }
}
