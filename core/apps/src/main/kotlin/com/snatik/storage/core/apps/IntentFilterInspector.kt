package com.snatik.storage.core.apps

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * One action a component advertises it can receive, with the data shape (scheme/host/mime) declared
 * alongside it in the same intent-filter — enough to build a matching Intent to try.
 */
data class ReceivableIntent(
    val componentType: String, // activity, activity-alias, receiver, service
    val className: String,
    val action: String,
    val categories: List<String>,
    val schemes: List<String>,
    val hosts: List<String>,
    val mimeTypes: List<String>,
) {
    /** A data:// template matching this filter, for prefilling the builder; null when it takes no URI. */
    val sampleData: String?
        get() {
            val scheme = schemes.firstOrNull() ?: return null
            val host = hosts.firstOrNull()
            return when {
                scheme == "http" || scheme == "https" -> "$scheme://${host ?: "example.com"}/"
                host != null -> "$scheme://$host/"
                else -> "$scheme:"
            }
        }
}

/**
 * Reads an installed app's manifest and lists the intents its exported components can receive, by
 * scraping their `<intent-filter>`s. Answers "what can I send to this app?".
 */
class IntentFilterInspector(private val context: Context, private val decoder: ManifestDecoder) {

    suspend fun inspect(packageName: String): List<ReceivableIntent> = withContext(Dispatchers.IO) {
        val apk = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
            ?.let { it.publicSourceDir ?: it.sourceDir } ?: return@withContext emptyList()
        val xml = runCatching { decoder.decode(packageName, apk) }.getOrNull() ?: return@withContext emptyList()
        runCatching { parse(xml, packageName) }.getOrDefault(emptyList())
    }

    private fun parse(xml: String, pkg: String): List<ReceivableIntent> {
        // Namespace processing OFF: our decoded manifest writes attributes as the literal string
        // "android:name", which we read back verbatim. With namespaces on, the parser would try to
        // resolve the "android" prefix and throw on the first prefixed tag.
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            .newPullParser().apply { setInput(StringReader(xml)) }
        val out = ArrayList<ReceivableIntent>()

        var compType: String? = null
        var compName: String? = null
        var compExported: Boolean? = null
        var inFilter = false
        val actions = ArrayList<String>()
        val cats = ArrayList<String>()
        val schemes = LinkedHashSet<String>()
        val hosts = LinkedHashSet<String>()
        val mimes = LinkedHashSet<String>()

        fun attr(name: String) = parser.getAttributeValue(null, "android:$name")

        fun flush() {
            // Only surface components a sender can actually reach.
            if (compName != null && compExported != false) {
                val visibleCats = cats.filterNot { it == "android.intent.category.DEFAULT" }
                for (a in actions) {
                    out += ReceivableIntent(compType.orEmpty(), compName!!, a, visibleCats, schemes.toList(), hosts.toList(), mimes.toList())
                }
            }
            inFilter = false
            actions.clear(); cats.clear(); schemes.clear(); hosts.clear(); mimes.clear()
        }

        var event = try { parser.eventType } catch (t: Throwable) { return out.distinct() }
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "activity", "activity-alias", "receiver", "service" -> {
                        compType = parser.name
                        compName = normalize(attr("name"), pkg)
                        compExported = when (attr("exported")?.lowercase()) { "true" -> true; "false" -> false; else -> null }
                    }
                    "intent-filter" -> inFilter = true
                    "action" -> if (inFilter) attr("name")?.let { actions += it }
                    "category" -> if (inFilter) attr("name")?.let { cats += it }
                    "data" -> if (inFilter) {
                        attr("scheme")?.let { schemes += it }
                        attr("host")?.let { hosts += it }
                        attr("mimeType")?.let { mimes += it }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "intent-filter" -> flush()
                    "activity", "activity-alias", "receiver", "service" -> { compType = null; compName = null; compExported = null }
                }
            }
            // A malformed tag deep in a large manifest shouldn't discard everything parsed so far.
            event = try { parser.next() } catch (t: Throwable) { break }
        }
        return out.distinct()
    }

    /** Resolve a manifest component name to a fully-qualified class. */
    private fun normalize(name: String?, pkg: String): String? = when {
        name == null -> null
        name.startsWith(".") -> pkg + name
        !name.contains(".") -> "$pkg.$name"
        else -> name
    }
}
