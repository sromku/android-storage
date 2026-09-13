package com.snatik.storage.core.intents

import com.snatik.storage.core.shell.ShellExecutor
import com.snatik.storage.core.shell.run

/** Outcome of trying to recover a monitored intent's full data URI from the live activity records. */
sealed interface FullDataResult {
    /** Exactly one record matched — this is its full, un-redacted data. */
    data class Resolved(val data: String) : FullDataResult
    /** More than one record could match; we won't guess. */
    data object Ambiguous : FullDataResult
    /** No matching record is still around (or the data wasn't truncated). */
    data object NotFound : FullDataResult
}

/**
 * The live monitor only sees the privacy-redacted data URI (`scheme://host/...`). The full URI is
 * still un-redacted in `dumpsys activity activities` while the activity is around. This recovers it,
 * but only when a single record matches with full confidence — otherwise it declines to guess.
 */
class IntentDataResolver {

    suspend fun resolve(shell: ShellExecutor, target: MonitoredIntent): FullDataResult {
        val redacted = target.data ?: return FullDataResult.NotFound
        if (!redacted.endsWith("...")) return FullDataResult.Resolved(redacted)
        // Need a real scheme://host prefix to match against; a bare "scheme:" is too weak to be sure.
        if (!redacted.removeSuffix("...").trimEnd('/').contains("://")) return FullDataResult.NotFound
        if (target.packageName == null) return FullDataResult.NotFound

        val out = runCatching { shell.run("dumpsys activity activities", timeoutMs = 20_000) }
            .getOrNull()?.takeIf { it.out.isNotBlank() }?.out ?: return FullDataResult.NotFound
        return match(out, target)
    }

    /** Pure matcher over `dumpsys activity activities` output. Confident single match, or declines. */
    fun match(dumpsys: String, target: MonitoredIntent): FullDataResult {
        val redacted = target.data ?: return FullDataResult.NotFound
        if (!redacted.endsWith("...")) return FullDataResult.Resolved(redacted)
        val prefix = redacted.removeSuffix("...").trimEnd('/')
        val pkg = target.packageName ?: return FullDataResult.NotFound

        val matches = INTENT_BLOCK.findAll(dumpsys).mapNotNull { m ->
            val inner = m.groupValues[1]
            val cmp = CMP.find(inner)?.groupValues?.get(1) ?: return@mapNotNull null
            Candidate(pkg = cmp.substringBefore('/'), action = ACT.find(inner)?.groupValues?.get(1), data = DAT.find(inner)?.groupValues?.get(1))
        }.filter { c ->
            c.pkg == pkg &&
                c.action == target.action &&
                c.data != null && !c.data.endsWith("...") && startsWithBoundary(c.data, prefix)
        }.map { it.data!! }.distinct().toList()

        return when (matches.size) {
            1 -> FullDataResult.Resolved(matches.first())
            0 -> FullDataResult.NotFound
            else -> FullDataResult.Ambiguous
        }
    }

    /** True when [data] begins with [prefix] at a real boundary (so example.com doesn't match example.com.evil). */
    private fun startsWithBoundary(data: String, prefix: String): Boolean =
        data.startsWith(prefix) && (data.length == prefix.length || data[prefix.length] in "/?#")

    private data class Candidate(val pkg: String, val action: String?, val data: String?)

    private companion object {
        val INTENT_BLOCK = Regex("""Intent \{([^{}]*)\}""")
        val ACT = Regex("""\bact=(\S+)""")
        val DAT = Regex("""\bdat=(\S+)""")
        val CMP = Regex("""\bcmp=(\S+)""")
    }
}
