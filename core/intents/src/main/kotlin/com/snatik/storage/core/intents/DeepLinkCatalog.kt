package com.snatik.storage.core.intents

import com.snatik.storage.core.shell.ShellExecutor
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A real web deep link (App Link): an https domain a package is registered and approved to open. */
data class WebLink(val domain: String, val packageName: String, val state: String)

/** A custom URI scheme registered on the device and the packages whose activities handle it. */
data class SchemeLink(val scheme: String, val packages: List<String>)

/** What the device actually advertises as openable, rather than a guessed list. */
data class DeepLinkData(val webLinks: List<WebLink>, val schemes: List<SchemeLink>)

/**
 * Discovers the deep links that really exist on this device from `dumpsys package`: the Activity
 * Resolver Table's scheme registrations and the domain-verification approvals. Needs the shell's
 * DUMP permission.
 */
class DeepLinkCatalog {

    suspend fun fetch(shell: ShellExecutor): DeepLinkData {
        val result = shell.run("dumpsys package", timeoutMs = 60_000)
        if (!result.ok && result.out.isBlank()) throw IllegalStateException(result.err.ifBlank { "dumpsys failed (${result.exitCode})" })
        return withContext(Dispatchers.Default) { parse(result.out) }
    }

    companion object {
        // http/https are web links (handled via domain verification), not custom deep-link schemes.
        private val WEB_SCHEMES = setOf("http", "https")
        // States under "Domain verification state:" that mean the app is approved to open the domain.
        private val OPENABLE_STATES = setOf("verified", "system_configured")

        private val schemeLine = Regex("""^ {6}([^\s:]+):$""")
        private val activityLine = Regex("""^ {8}\S+ ([A-Za-z][\w.]+)/""")
        private val subsectionHeader = Regex("""^ {2}(\S.*):$""")
        private val packageHeader = Regex("""^ {2}([A-Za-z][\w.]+):$""")
        private val domainState = Regex("""^\s+([a-z0-9][a-z0-9.\-]*\.[a-z]{2,}): (\w+)$""")

        fun parse(text: String): DeepLinkData {
            val schemeMap = LinkedHashMap<String, LinkedHashSet<String>>()
            val webLinks = ArrayList<WebLink>()

            var inSchemes = false
            var inDomains = false
            var currentScheme: String? = null
            var currentPackage: String? = null

            for (line in text.lineSequence()) {
                // Top-level sections toggle the domain-verification block on and off.
                if (line.isNotEmpty() && !line.startsWith(" ")) {
                    inDomains = line.startsWith("Domain verification status:")
                    inSchemes = false
                    currentPackage = null
                    currentScheme = null
                    continue
                }

                if (inDomains) {
                    val pkgMatch = packageHeader.find(line)
                    if (pkgMatch != null) { currentPackage = pkgMatch.groupValues[1]; continue }
                    val pkg = currentPackage ?: continue
                    domainState.find(line)?.let { m ->
                        if (m.groupValues[2] in OPENABLE_STATES) webLinks += WebLink(m.groupValues[1], pkg, m.groupValues[2])
                    }
                    continue
                }

                // Within the Activity Resolver Table, a 2-space subsection header switches context.
                val header = subsectionHeader.find(line)
                if (header != null) { inSchemes = header.groupValues[1] == "Schemes"; currentScheme = null; continue }

                if (inSchemes) {
                    val schemeMatch = schemeLine.find(line)
                    if (schemeMatch != null) {
                        val scheme = schemeMatch.groupValues[1].lowercase()
                        currentScheme = scheme
                        if (scheme !in WEB_SCHEMES) schemeMap.getOrPut(scheme) { LinkedHashSet() }
                        continue
                    }
                    val scheme = currentScheme
                    if (scheme != null && scheme !in WEB_SCHEMES) {
                        activityLine.find(line)?.let { m -> schemeMap[scheme]?.add(m.groupValues[1]) }
                    }
                }
            }

            val schemes = schemeMap.entries
                .filter { it.value.isNotEmpty() }
                .map { SchemeLink(it.key, it.value.toList()) }
                .sortedBy { it.scheme }
            return DeepLinkData(webLinks = webLinks.distinct(), schemes = schemes)
        }
    }
}
