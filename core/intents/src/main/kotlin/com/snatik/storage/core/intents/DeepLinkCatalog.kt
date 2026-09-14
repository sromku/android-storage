package com.snatik.storage.core.intents

import com.snatik.storage.core.shell.ShellExecutor
import com.snatik.storage.core.shell.run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A real web deep link (App Link): an https domain a package is registered and approved to open. */
data class WebLink(val domain: String, val packageName: String, val state: String)

/** A custom URI scheme registered on the device and the packages whose activities handle it. */
data class SchemeLink(val scheme: String, val packages: List<String>)

/** A host and the concrete example paths its declared intent filters accept ("" = the bare host). */
data class HostPaths(val host: String, val paths: List<String>)

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

    /** The web hosts and their example paths one package declares, from `dumpsys package <pkg>`. */
    suspend fun fetchAppLinks(shell: ShellExecutor, packageName: String): List<HostPaths> {
        val result = shell.run("dumpsys package $packageName", timeoutMs = 30_000)
        if (!result.ok && result.out.isBlank()) throw IllegalStateException(result.err.ifBlank { "dumpsys failed (${result.exitCode})" })
        return withContext(Dispatchers.Default) { parseAppLinks(result.out) }
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

        private val filterAttr = Regex("""^ +(Action|Category|Scheme|Authority|Path): (.*)$""")
        private val quoted = Regex(""""([^"]*)"""")
        private val patternMatcher = Regex("""PatternMatcher\{(\w+): (.*)\}""")
        private val globMeta = ".*?[]()^$+{}|\\".toSet()

        /**
         * Pull the web hosts and example paths a package advertises from its `dumpsys package <pkg>`
         * intent filters. Only VIEW + BROWSABLE http/https filters count. Each host gets the bare
         * host ("") plus any concrete paths (literal, prefix, or a glob's fixed prefix).
         */
        fun parseAppLinks(text: String): List<HostPaths> {
            val hostPaths = LinkedHashMap<String, LinkedHashSet<String>>()
            var view = false
            var browsable = false
            val schemes = HashSet<String>()
            val authorities = ArrayList<String>()
            val paths = ArrayList<String>()

            fun flush() {
                if (view && browsable && schemes.any { it in WEB_SCHEMES } && authorities.isNotEmpty()) {
                    for (host in authorities) {
                        val set = hostPaths.getOrPut(host) { LinkedHashSet() }
                        set += ""
                        set += paths
                    }
                }
                view = false; browsable = false; schemes.clear(); authorities.clear(); paths.clear()
            }

            for (line in text.lineSequence()) {
                val attr = filterAttr.find(line)
                if (attr == null) { flush(); continue }
                val key = attr.groupValues[1]
                val value = quoted.find(attr.groupValues[2])?.groupValues?.get(1) ?: continue
                when (key) {
                    "Action" -> if (value.endsWith(".VIEW")) view = true
                    "Category" -> if (value.endsWith(".BROWSABLE")) browsable = true
                    "Scheme" -> schemes += value.lowercase()
                    "Authority" -> authorities += value.lowercase()
                    "Path" -> pathExample(value)?.let { paths += it }
                }
            }
            flush()
            return hostPaths.entries.map { HostPaths(it.key, it.value.toList()) }.sortedBy { it.host }
        }

        /** A concrete, launchable path from a PatternMatcher, or null when it can't be made literal. */
        private fun pathExample(pattern: String): String? {
            val m = patternMatcher.find(pattern) ?: return null
            val value = m.groupValues[2]
            return when (m.groupValues[1]) {
                "LITERAL", "PREFIX" -> value.takeIf { it.startsWith("/") }
                "GLOB" -> if (value == ".*") null else value.takeWhile { it !in globMeta }.takeIf { it.length > 1 && it.startsWith("/") }
                else -> null
            }
        }
    }
}
