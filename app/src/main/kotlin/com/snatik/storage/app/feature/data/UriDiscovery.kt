package com.snatik.storage.app.feature.data

import android.content.Context
import com.snatik.storage.app.feature.viewer.AndroidJadxSecurity
import com.snatik.storage.core.apps.AppRepository
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.JavaClass
import jadx.api.impl.NoOpCodeCache
import jadx.api.plugins.input.data.IFieldData
import jadx.api.plugins.input.data.IMethodData
import jadx.api.plugins.input.data.ISeqConsumer
import jadx.api.plugins.input.insns.InsnIndexType
import jadx.api.plugins.input.insns.Opcode
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
 * Recovers a provider's real content:// paths from its base APK by scanning `UriMatcher.addURI(...)`
 * calls directly in the dex bytecode — no decompilation. For each method we track which register each
 * `const-string` loads, and when an `invoke …addURI(String, String, int)` appears we read the path
 * from argument register 2 (arg 0 is the matcher, arg 1 the authority). Obfuscation-safe: `addURI` is
 * an SDK method (never renamed) and string literals survive R8. Bytecode scanning uses a tiny
 * fraction of the memory that full decompilation does, so even a 50k-class app fits the 512MB heap.
 * Code lives in base.apk, so splits are ignored. Best-effort.
 *
 * By default only the app's own packages are scanned; [scanAll] widens to every class (a provider can
 * inherit its `UriMatcher` setup from a bundled library base class).
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

        // Load one dex at a time and close it before the next, so peak memory stays bounded to a
        // single dex — loading a huge app's whole dex set at once is wasteful.
        dexes.forEachIndexed { di, dex ->
            currentCoroutineContext().ensureActive()
            emit(Event.Status(Phase.LOADING, dexIndex = di + 1, dexCount = dexes.size))
            val args = JadxArgs().apply {
                inputFiles.add(dex)
                security = AndroidJadxSecurity()
                setSkipResources(true)
                threadsCount = 1
                // We never call getCode(), but keep the code cache disabled defensively so nothing
                // is ever retained across classes.
                setCodeCache(NoOpCodeCache())
            }
            val jadx = JadxDecompiler(args)
            runCatching { Files.createDirectories(args.filesGetter.tempDir) }
            try {
                jadx.load()
                val target = if (scanAll) jadx.classes
                else jadx.classes.filter { c -> prefixes.any { c.fullName == it || c.fullName.startsWith("$it.") } }
                val total = target.size
                // Resolving a method ref (to read its name) isn't free, so remember per dex which
                // method-table indices are UriMatcher.addURI — a scan-all touches millions of invokes.
                val addUriRefs = HashMap<Int, Boolean>()
                target.forEachIndexed { i, cls ->
                    currentCoroutineContext().ensureActive()
                    scanClass(cls, authority, found, addUriRefs)
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

    /**
     * Walk one class's methods at the bytecode level (no decompilation). For each method we track the
     * string most recently loaded into each register, and on every `addURI` invoke read the path from
     * argument register 2 (0 = matcher, 1 = authority, 2 = path).
     */
    private fun scanClass(cls: JavaClass, authority: String, found: MutableSet<String>, addUriRefs: HashMap<Int, Boolean>) {
        val data = runCatching { cls.classNode.clsData }.getOrNull() ?: return
        val fields = ISeqConsumer<IFieldData> { /* ignored */ }
        val methods = ISeqConsumer<IMethodData> { method ->
            val reader = runCatching { method.codeReader }.getOrNull() ?: return@ISeqConsumer
            val regString = HashMap<Int, String>()
            runCatching {
                reader.visitInstructions { insn ->
                    runCatching {
                        insn.decode()
                        when {
                            insn.opcode == Opcode.CONST_STRING && insn.indexType == InsnIndexType.STRING_REF -> {
                                val dest = insn.resultReg.takeIf { it >= 0 } ?: insn.getReg(0)
                                regString[dest] = insn.indexAsString
                            }
                            insn.indexType == InsnIndexType.METHOD_REF -> {
                                // Only real UriMatcher.addURI calls — some apps have unrelated methods
                                // named addURI with a different argument order. Resolving the ref is
                                // costly, so cache the verdict per method-table index.
                                val isAddUri = addUriRefs.getOrPut(insn.index) {
                                    val m = insn.indexAsMethod ?: return@getOrPut false
                                    runCatching { m.load() } // name/parent are empty until resolved
                                    m.name == "addURI" && m.parentClassType == URI_MATCHER
                                }
                                if (isAddUri && insn.regsCount >= 3) {
                                    // addURI(authority, path, code): invoke args are
                                    // [matcher, authority, path, code] → path is register 2.
                                    regString[insn.getReg(2)]?.let { addPath(it, authority, found) }
                                }
                            }
                        }
                    }
                }
            }
        }
        runCatching { data.visitFieldsAndMethods(fields, methods) }
    }

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
        private const val URI_MATCHER = "Landroid/content/UriMatcher;"
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
                addPath(lit, authority, out)
            }
            return out
        }

        /** Normalise a raw path literal and add it (skipping the authority itself and dupes). */
        fun addPath(raw: String, authority: String, into: MutableCollection<String>) {
            val path = raw.trim().trimStart('/')
            if (path.isNotEmpty() && path != authority && path !in into) into.add(path)
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
