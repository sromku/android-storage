package com.snatik.storage.app.feature.viewer

import jadx.api.security.IJadxSecurity
import org.w3c.dom.Document
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * jadx's default security builds a hardened XML parser using features that Android's XML
 * implementation does not support, which makes its static initializer throw before jadx can run.
 * This replacement parses XML with a plain, Android-compatible [DocumentBuilderFactory], hardening
 * only where the platform allows.
 */
class AndroidJadxSecurity : IJadxSecurity {

    override fun verifyAppPackage(appPackage: String): String = appPackage

    override fun parseXml(input: InputStream): Document {
        val dbf = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            // Harden where supported; Android rejects some of these, so ignore failures.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return dbf.newDocumentBuilder().parse(input)
    }
}
