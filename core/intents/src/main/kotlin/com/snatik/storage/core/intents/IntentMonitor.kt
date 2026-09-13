package com.snatik.storage.core.intents

/**
 * One activity-start intent observed system-wide from the ActivityTaskManager logcat stream.
 * Extras values aren't in the logs (the system logs only "has extras"), but the shape and the
 * caller are.
 */
data class MonitoredIntent(
    val id: Long,
    val time: Long,
    val action: String?,
    val data: String?,
    val type: String?,
    val categories: List<String>,
    val flags: Int,
    val packageName: String?,
    val className: String?,
    val callerUid: Int?,
    val callerPackage: String?,
    val hasExtras: Boolean,
) {
    fun toSpec(): IntentSpec = IntentSpec(
        action = action,
        data = data,
        type = type,
        categories = categories,
        packageName = packageName,
        className = className,
        flags = flags,
        extras = emptyList(),
    )
}

/**
 * Parses the `ActivityTaskManager: START` lines logcat emits for every activity launch, e.g.
 * `START u0 {act=android.intent.action.VIEW dat=https://… cmp=pkg/cls (has extras)} with … from uid 10225 (com.android.chrome) …`
 */
object IntentMonitorParser {

    // Values run until whitespace or the block's closing brace.
    private val act = Regex("""\bact=([^\s}]+)""")
    private val dat = Regex("""\bdat=([^\s}]+)""")
    private val typ = Regex("""\btyp=([^\s}]+)""")
    private val cat = Regex("""\bcat=\[([^\]]*)]""")
    private val flg = Regex("""\bflg=0x([0-9a-fA-F]+)""")
    private val cmp = Regex("""\bcmp=([^\s}]+)""")
    private val caller = Regex("""from uid (\d+)(?:\s+\(([^)]+)\))?""")

    /** Returns a parsed intent for a START line, or null for anything else. */
    fun parse(line: String, id: Long, time: Long): MonitoredIntent? {
        if (!line.contains("START u")) return null
        val component = cmp.find(line)?.groupValues?.get(1)
        val action = act.find(line)?.groupValues?.get(1)
        if (component == null && action == null) return null
        val pkg = component?.substringBefore('/')
        val cls = component?.substringAfter('/', "")?.takeIf { it.isNotEmpty() }?.let { if (it.startsWith(".")) pkg + it else it }
        val callerMatch = caller.find(line)
        return MonitoredIntent(
            id = id,
            time = time,
            action = action,
            data = dat.find(line)?.groupValues?.get(1),
            type = typ.find(line)?.groupValues?.get(1),
            categories = cat.find(line)?.groupValues?.get(1)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            flags = flg.find(line)?.groupValues?.get(1)?.toLongOrNull(16)?.toInt() ?: 0,
            packageName = pkg,
            className = cls,
            callerUid = callerMatch?.groupValues?.get(1)?.toIntOrNull(),
            // The token right after the uid is the caller package on most lines, but some carry a
            // BAL reason there instead; only trust it when it looks like a package name.
            callerPackage = callerMatch?.groupValues?.get(2)?.takeIf { it.contains('.') && !it.contains('=') },
            hasExtras = line.contains("(has extras)"),
        )
    }
}
