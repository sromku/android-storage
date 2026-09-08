package com.snatik.storage.core.apps

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for Android's binary XML (the AndroidManifest.xml inside every APK) back to text.
 *
 * Resource references are shown as `@0x7f...` unless a [resolver] can name them.
 */
class BinaryXml(bytes: ByteArray, private val resolver: (Int) -> String? = { null }) {

    private val buf: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private var strings: List<String> = emptyList()
    private var resourceIds: IntArray = IntArray(0)

    private val out = StringBuilder()
    private var depth = 0
    private val pendingNamespaces = ArrayList<Pair<String, String>>()
    private val prefixes = HashMap<String, String>()

    fun decode(): String {
        out.setLength(0)
        buf.position(0)
        val type = buf.short.toInt() and 0xFFFF
        require(type == RES_XML_TYPE) { "Not a binary XML document (type 0x${type.toString(16)})" }
        buf.short // header size
        val total = buf.int
        out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        while (buf.position() < total && buf.remaining() >= 8) {
            val start = buf.position()
            val chunkType = buf.short.toInt() and 0xFFFF
            val headerSize = buf.short.toInt() and 0xFFFF
            val chunkSize = buf.int
            if (chunkSize <= 0) break
            when (chunkType) {
                RES_STRING_POOL_TYPE -> readStringPool(start, headerSize)
                RES_XML_RESOURCE_MAP_TYPE -> readResourceMap(start, headerSize, chunkSize)
                RES_XML_START_NAMESPACE_TYPE -> startNamespace(start, headerSize)
                RES_XML_END_NAMESPACE_TYPE -> Unit
                RES_XML_START_ELEMENT_TYPE -> startElement(start, headerSize)
                RES_XML_END_ELEMENT_TYPE -> endElement(start, headerSize)
                RES_XML_CDATA_TYPE -> cdata(start, headerSize)
            }
            buf.position(start + chunkSize)
        }
        return out.toString()
    }

    private fun readStringPool(start: Int, headerSize: Int) {
        val stringCount = buf.int
        buf.int // style count
        val flags = buf.int
        val stringsStart = buf.int
        buf.int // styles start
        val utf8 = flags and UTF8_FLAG != 0
        val offsets = IntArray(stringCount) { buf.int }
        val base = start + stringsStart
        strings = offsets.map { offset -> readString(base + offset, utf8) }
        buf.position(start + headerSize)
    }

    private fun readString(position: Int, utf8: Boolean): String {
        buf.position(position)
        return if (utf8) {
            readUtf8Length() // char length, unused
            val byteLength = readUtf8Length()
            val bytes = ByteArray(byteLength).also { buf.get(it) }
            String(bytes, Charsets.UTF_8)
        } else {
            var length = buf.short.toInt() and 0xFFFF
            if (length and 0x8000 != 0) {
                length = ((length and 0x7FFF) shl 16) or (buf.short.toInt() and 0xFFFF)
            }
            val chars = CharArray(length) { buf.char }
            String(chars)
        }
    }

    private fun readUtf8Length(): Int {
        var length = buf.get().toInt() and 0xFF
        if (length and 0x80 != 0) {
            length = ((length and 0x7F) shl 8) or (buf.get().toInt() and 0xFF)
        }
        return length
    }

    private fun readResourceMap(start: Int, headerSize: Int, chunkSize: Int) {
        buf.position(start + headerSize)
        val count = (chunkSize - headerSize) / 4
        resourceIds = IntArray(count) { buf.int }
    }

    private fun startNamespace(start: Int, headerSize: Int) {
        buf.position(start + headerSize)
        val prefix = string(buf.int)
        val uri = string(buf.int)
        prefixes[uri] = prefix
        pendingNamespaces += prefix to uri
    }

    private fun startElement(start: Int, headerSize: Int) {
        buf.position(start + headerSize)
        val ns = buf.int
        val name = string(buf.int)
        val attributeStart = buf.short.toInt() and 0xFFFF
        buf.short // attribute size
        val attributeCount = buf.short.toInt() and 0xFFFF
        buf.position(start + headerSize + attributeStart)

        indent()
        out.append('<').append(qualified(ns, name))
        val attrs = ArrayList<String>()
        for ((prefix, uri) in pendingNamespaces) attrs += "xmlns:$prefix=\"$uri\""
        pendingNamespaces.clear()
        repeat(attributeCount) {
            val attrNs = buf.int
            val attrNameIndex = buf.int
            val rawValue = buf.int
            buf.short // typed value size
            buf.get() // res0
            val dataType = buf.get().toInt() and 0xFF
            val data = buf.int
            val attrName = attributeName(attrNameIndex)
            val value = if (rawValue >= 0) string(rawValue) else typedValue(dataType, data)
            attrs += "${qualified(attrNs, attrName)}=\"${escape(value)}\""
        }
        if (attrs.size == 1) {
            out.append(' ').append(attrs[0])
        } else if (attrs.isNotEmpty()) {
            for (a in attrs) out.append('\n').append("    ".repeat(depth + 1)).append(a)
        }
        out.append(">\n")
        depth++
    }

    private fun endElement(start: Int, headerSize: Int) {
        buf.position(start + headerSize)
        val ns = buf.int
        val name = string(buf.int)
        depth--
        indent()
        out.append("</").append(qualified(ns, name)).append(">\n")
    }

    private fun cdata(start: Int, headerSize: Int) {
        buf.position(start + headerSize)
        val text = string(buf.int)
        indent()
        out.append(escape(text)).append('\n')
    }

    private fun attributeName(index: Int): String {
        val name = string(index)
        if (name.isNotEmpty()) return name
        // Obfuscated manifests drop the attribute name; the resource map still identifies it.
        val id = resourceIds.getOrNull(index) ?: return "attr$index"
        return resolver(id)?.substringAfterLast('/') ?: "attr_0x%08x".format(id)
    }

    private fun typedValue(type: Int, data: Int): String = when (type) {
        TYPE_NULL -> if (data == 0) "@null" else "@empty"
        TYPE_REFERENCE -> resolver(data)?.let { "@$it" } ?: "@0x%08x".format(data)
        TYPE_ATTRIBUTE -> resolver(data)?.let { "?$it" } ?: "?0x%08x".format(data)
        TYPE_STRING -> string(data)
        TYPE_FLOAT -> java.lang.Float.intBitsToFloat(data).toString()
        TYPE_DIMENSION -> complex(data, DIMENSION_UNITS)
        TYPE_FRACTION -> complex(data, FRACTION_UNITS)
        TYPE_INT_DEC -> data.toString()
        TYPE_INT_HEX -> "0x%x".format(data)
        TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
        in TYPE_INT_COLOR_ARGB8..TYPE_INT_COLOR_RGB4 -> "#%08x".format(data)
        else -> "0x%x".format(data)
    }

    private fun complex(data: Int, units: Array<String>): String {
        val mantissa = (data and -256) shr 8
        val radix = (data shr 4) and 0x3
        val value = mantissa.toFloat() / (1 shl (radix * 8 - (if (radix == 0) 0 else 1))).coerceAtLeast(1)
        val unit = units.getOrElse(data and 0xF) { "?" }
        val text = if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
        return text + unit
    }

    private fun qualified(ns: Int, name: String): String {
        if (ns < 0) return name
        val uri = strings.getOrNull(ns) ?: return name
        val prefix = prefixes[uri] ?: if (uri == ANDROID_NS) "android" else return name
        return "$prefix:$name"
    }

    private fun string(index: Int): String = if (index in strings.indices) strings[index] else ""

    private fun indent() {
        out.append("    ".repeat(depth.coerceAtLeast(0)))
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
    }

    private companion object {
        const val RES_STRING_POOL_TYPE = 0x0001
        const val RES_XML_TYPE = 0x0003
        const val RES_XML_START_NAMESPACE_TYPE = 0x0100
        const val RES_XML_END_NAMESPACE_TYPE = 0x0101
        const val RES_XML_START_ELEMENT_TYPE = 0x0102
        const val RES_XML_END_ELEMENT_TYPE = 0x0103
        const val RES_XML_CDATA_TYPE = 0x0104
        const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
        const val UTF8_FLAG = 1 shl 8
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        const val TYPE_NULL = 0x00
        const val TYPE_REFERENCE = 0x01
        const val TYPE_ATTRIBUTE = 0x02
        const val TYPE_STRING = 0x03
        const val TYPE_FLOAT = 0x04
        const val TYPE_DIMENSION = 0x05
        const val TYPE_FRACTION = 0x06
        const val TYPE_INT_DEC = 0x10
        const val TYPE_INT_HEX = 0x11
        const val TYPE_INT_BOOLEAN = 0x12
        const val TYPE_INT_COLOR_ARGB8 = 0x1c
        const val TYPE_INT_COLOR_RGB4 = 0x1f

        val DIMENSION_UNITS = arrayOf("px", "dip", "sp", "pt", "in", "mm")
        val FRACTION_UNITS = arrayOf("%", "%p")
    }
}
