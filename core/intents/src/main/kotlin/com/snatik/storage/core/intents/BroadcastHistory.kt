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
        private val queueHeader = Regex("""^\s*Historical broadcasts \[(\w+)\]""")
        private val queueHeaderAlt = Regex("""^\s*Historical Broadcast (\w+) #\d+""")
        private val intentLine = Regex("""(Intent \{[^}]*\})""")
        private val actionInIntent = Regex("""act=([^\s\}]+)""")
        private val enqueueLine = Regex("""(?:enqueueClockTime|enqueueTime|enqueued)[=:]?\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?:\.\d+)?|\S+)""")

        /** Pull the intents out of the dump in order, tolerant of the format differences across releases. */
        fun parse(text: String): List<HistoricalBroadcast> {
            val out = ArrayList<HistoricalBroadcast>()
            var queue = "?"
            var pendingIntent: String? = null
            var pendingAction: String? = null
            for (line in text.lineSequence()) {
                queueHeader.find(line)?.let { queue = it.groupValues[1] }
                queueHeaderAlt.find(line)?.let { queue = it.groupValues[1] }
                val intent = intentLine.find(line)?.groupValues?.get(1)
                if (intent != null) {
                    pendingIntent?.let { out += HistoricalBroadcast(queue, it, pendingAction, null) }
                    pendingIntent = intent
                    pendingAction = actionInIntent.find(intent)?.groupValues?.get(1)
                    continue
                }
                val enqueued = enqueueLine.find(line)?.groupValues?.get(1)
                if (enqueued != null && pendingIntent != null) {
                    out += HistoricalBroadcast(queue, pendingIntent, pendingAction, enqueued.trim())
                    pendingIntent = null
                    pendingAction = null
                }
            }
            pendingIntent?.let { out += HistoricalBroadcast(queue, it, pendingAction, null) }
            return out
        }
    }
}
