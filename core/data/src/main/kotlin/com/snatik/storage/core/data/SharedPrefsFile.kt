package com.snatik.storage.core.data

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

enum class PrefType { STRING, INT, LONG, FLOAT, BOOLEAN, SET }

data class PrefEntry(val key: String, val type: PrefType, val value: String, val setValues: List<String> = emptyList())

/** Reads and writes the XML format SharedPreferences uses on disk. */
object SharedPrefsFile {

    fun parse(xml: String): List<PrefEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root = document.documentElement ?: return emptyList()
        val out = ArrayList<PrefEntry>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val key = node.getAttribute("name")
            when (node.tagName) {
                "string" -> out += PrefEntry(key, PrefType.STRING, node.textContent)
                "int" -> out += PrefEntry(key, PrefType.INT, node.getAttribute("value"))
                "long" -> out += PrefEntry(key, PrefType.LONG, node.getAttribute("value"))
                "float" -> out += PrefEntry(key, PrefType.FLOAT, node.getAttribute("value"))
                "boolean" -> out += PrefEntry(key, PrefType.BOOLEAN, node.getAttribute("value"))
                "set" -> {
                    val values = ArrayList<String>()
                    val items = node.childNodes
                    for (j in 0 until items.length) {
                        val item = items.item(j) as? Element ?: continue
                        if (item.tagName == "string") values += item.textContent
                    }
                    out += PrefEntry(key, PrefType.SET, "", values)
                }
            }
        }
        return out
    }

    fun serialize(entries: List<PrefEntry>): String = buildString {
        append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n")
        for (e in entries) {
            when (e.type) {
                PrefType.STRING -> append("    <string name=\"${esc(e.key)}\">${esc(e.value)}</string>\n")
                PrefType.INT -> append("    <int name=\"${esc(e.key)}\" value=\"${esc(e.value)}\" />\n")
                PrefType.LONG -> append("    <long name=\"${esc(e.key)}\" value=\"${esc(e.value)}\" />\n")
                PrefType.FLOAT -> append("    <float name=\"${esc(e.key)}\" value=\"${esc(e.value)}\" />\n")
                PrefType.BOOLEAN -> append("    <boolean name=\"${esc(e.key)}\" value=\"${esc(e.value)}\" />\n")
                PrefType.SET -> {
                    append("    <set name=\"${esc(e.key)}\">\n")
                    for (v in e.setValues) append("        <string>${esc(v)}</string>\n")
                    append("    </set>\n")
                }
            }
        }
        append("</map>\n")
    }

    /** Validate a value for its type; null means acceptable. */
    fun validate(type: PrefType, value: String): String? = when (type) {
        PrefType.INT -> if (value.toIntOrNull() == null) "Not an int" else null
        PrefType.LONG -> if (value.toLongOrNull() == null) "Not a long" else null
        PrefType.FLOAT -> if (value.toFloatOrNull() == null) "Not a float" else null
        PrefType.BOOLEAN -> if (value != "true" && value != "false") "Must be true or false" else null
        PrefType.STRING, PrefType.SET -> null
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }
}
