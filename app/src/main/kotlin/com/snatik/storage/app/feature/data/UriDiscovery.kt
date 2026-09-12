package com.snatik.storage.app.feature.data

import android.content.Context
import com.snatik.storage.app.feature.viewer.AndroidJadxSecurity
import com.snatik.storage.core.apps.AppRepository
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * Recovers a provider's real content:// paths by decompiling its base APK and scraping
 * `UriMatcher.addURI(...)` calls. Obfuscation-safe: `addURI` is an SDK method (never renamed) and
 * string literals survive R8, so we take the 2nd argument (the path) and pair it with the known
 * authority. Code lives in base.apk, so splits are ignored. Best-effort.
 *
 * By default only the app's own packages are decompiled (huge apps bundle tens of thousands of
 * library classes); [scanAll] widens to every class.
 */
class UriDiscovery(private val context: Context, private val apps: AppRepository) {

    enum class Phase { READING, LOADING, SCANNING }

    sealed interface Event {
        data class Status(val phase: Phase, val scanned: Int = 0, val total: Int = 0, val found: Int = 0, val dexIndex: Int = 0, val dexCount: Int = 0) : Event
        data class Done(val paths: List<String>, val scopedOnly: Boolean) : Event
    }

    class DiscoveryException(message: String) : Exception(message)

    fun discover(packageName: String, providerClass: String, authority: String, scanAll: Boolean = false): Flow<Event> = flow {
        emit(Event.Status(Phase.READING))
        val info = apps.applicationInfo(packageName) ?: throw DiscoveryException("App not found")
        val apk = info.publicSourceDir ?: info.sourceDir ?: throw DiscoveryException("Base APK not found")
        val dexes = extractDexes(File(apk)).sortedBy { it.name }
        if (dexes.isEmpty()) throw DiscoveryException("No dex in base APK")

        val providerPkg = providerClass.substringBeforeLast('.', "")
        val prefixes = listOf(packageName, providerPkg).filter { it.isNotEmpty() }.distinct()
        val found = LinkedHashSet<String>()
        var lastEmit = 0L

        // Decompile one dex at a time and close it before the next, so peak memory stays bounded to a
        // single dex — loading a huge app's whole dex set at once OOMs the 512MB heap.
        dexes.forEachIndexed { di, dex ->
            currentCoroutineContext().ensureActive()
            emit(Event.Status(Phase.LOADING, dexIndex = di + 1, dexCount = dexes.size))
            val args = JadxArgs().apply {
                inputFiles.add(dex)
                security = AndroidJadxSecurity()
                setSkipResources(true)
                isShowInconsistentCode = true
                setDebugInfo(false)
                threadsCount = 1
            }
            val jadx = JadxDecompiler(args)
            runCatching { Files.createDirectories(args.filesGetter.tempDir) }
            try {
                jadx.load()
                val target = if (scanAll) jadx.classes
                else jadx.classes.filter { c -> prefixes.any { c.fullName == it || c.fullName.startsWith("$it.") } }
                val total = target.size
                target.forEachIndexed { i, cls ->
                    currentCoroutineContext().ensureActive()
                    val code = runCatching { cls.code }.getOrNull()
                    if (code != null && code.contains("addURI")) {
                        extractPaths(code, authority).forEach { found.add(it) }
                    }
                    runCatching { cls.unload() } // free the decompiled AST immediately
                    val now = System.currentTimeMillis()
                    if (now - lastEmit > 150 || i == total - 1) {
                        lastEmit = now
                        emit(Event.Status(Phase.SCANNING, i + 1, total, found.size, di + 1, dexes.size))
                    }
                }
            } catch (oom: OutOfMemoryError) {
                // Skip a dex too big to fit; keep whatever we already found.
            } finally {
                runCatching { jadx.close() }
            }
        }
        emit(Event.Done(found.sorted(), scopedOnly = !scanAll))
    }.flowOn(Dispatchers.IO)

    private fun extractDexes(apk: File): List<File> {
        val out = File(context.cacheDir, "uridisc").apply { deleteRecursively(); mkdirs() }
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().filter { it.name.matches(Regex("""classes\d*\.dex""")) }.forEach { e ->
                val f = File(out, e.name)
                zip.getInputStream(e).use { input -> f.outputStream().use { input.copyTo(it) } }
            }
        }
        return out.listFiles()?.toList().orEmpty()
    }

    companion object {
        private val STRING = Regex(""""((?:\\.|[^"\\])*)"""")

        /** From decompiled code, take the 2nd argument of each addURI(...) call as the path. */
        fun extractPaths(code: String, authority: String): List<String> {
            val out = ArrayList<String>()
            var idx = 0
            while (true) {
                val at = code.indexOf("addURI", idx)
                if (at < 0) break
                idx = at + 6
                val open = code.indexOf('(', at).takeIf { it in at..(at + 12) } ?: continue
                val close = matchingParen(code, open) ?: continue
                val args = splitTopLevel(code.substring(open + 1, close))
                val pathArg = args.getOrNull(1) ?: continue
                val lit = STRING.find(pathArg)?.groupValues?.get(1) ?: continue
                val path = lit.trim().trimStart('/')
                if (path.isNotEmpty() && path != authority) out.add(path)
            }
            return out
        }

        private fun matchingParen(s: String, open: Int): Int? {
            var depth = 0; var inStr = false; var i = open
            while (i < s.length) {
                val c = s[i]
                when {
                    inStr -> if (c == '\\') i++ else if (c == '"') inStr = false
                    c == '"' -> inStr = true
                    c == '(' -> depth++
                    c == ')' -> { depth--; if (depth == 0) return i }
                }
                i++
            }
            return null
        }

        private fun splitTopLevel(args: String): List<String> {
            val parts = ArrayList<String>()
            val sb = StringBuilder(); var depth = 0; var inStr = false; var i = 0
            while (i < args.length) {
                val c = args[i]
                when {
                    inStr -> { sb.append(c); if (c == '\\') { i++; if (i < args.length) sb.append(args[i]) } else if (c == '"') inStr = false }
                    c == '"' -> { inStr = true; sb.append(c) }
                    c == '(' -> { depth++; sb.append(c) }
                    c == ')' -> { depth--; sb.append(c) }
                    c == ',' && depth == 0 -> { parts.add(sb.toString()); sb.setLength(0) }
                    else -> sb.append(c)
                }
                i++
            }
            if (sb.isNotEmpty()) parts.add(sb.toString())
            return parts
        }
    }
}
