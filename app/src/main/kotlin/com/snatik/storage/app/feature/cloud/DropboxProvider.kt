package com.snatik.storage.app.feature.cloud

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * ====================================================================================
 *  Dropbox app key. Empty by design - the cloud browser stays in its "not configured"
 *  state until you paste a key here.
 *
 *  Setup (about 5 minutes, one time):
 *   1. Go to https://www.dropbox.com/developers/apps  ->  Create app
 *   2. Choose "Scoped access" + "Full Dropbox" (or "App folder").
 *   3. Under Permissions, enable: files.metadata.read, files.content.read
 *   4. Under Settings, add this exact OAuth redirect URI:
 *          snatikstorage://dropbox-auth
 *   5. Copy the "App key" and paste it below.
 * ====================================================================================
 */
object DropboxConfig {
    const val APP_KEY = "" // <-- paste your Dropbox app key here
    const val REDIRECT_URI = "snatikstorage://dropbox-auth"
}

/** One file or folder as returned by the Dropbox metadata API. */
data class CloudEntry(
    val name: String,
    val pathLower: String,
    val isFolder: Boolean,
    val size: Long,
    val id: String,
) {
    val isImage: Boolean
        get() = !isFolder && name.substringAfterLast('.', "").lowercase() in IMAGE_EXTS

    private companion object {
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp", "dng", "arw", "nef", "cr2", "cr3", "raf", "orf", "rw2")
    }
}

/** Minimal provider surface so Drive/OneDrive can slot in behind the same browser later. */
interface CloudProvider {
    val displayName: String
    fun isConfigured(): Boolean
    fun isConnected(): Boolean
    fun authUrl(): String?
    suspend fun exchangeCode(code: String): Boolean
    suspend fun list(path: String): List<CloudEntry>
    suspend fun temporaryLink(pathLower: String): String?
    fun disconnect()
}

/**
 * Dropbox provider using client-side PKCE OAuth (no client secret) and the v2 REST API over
 * HttpURLConnection. Tokens live in private SharedPreferences.
 */
class DropboxProvider(private val context: Context) : CloudProvider {

    private val prefs = context.getSharedPreferences("cloud_dropbox", Context.MODE_PRIVATE)
    private val _connected = MutableStateFlow(prefs.contains(KEY_TOKEN))
    val connected = _connected

    private var verifier: String? = null

    override val displayName = "Dropbox"

    override fun isConfigured(): Boolean = DropboxConfig.APP_KEY.isNotBlank()

    override fun isConnected(): Boolean = prefs.contains(KEY_TOKEN)

    override fun authUrl(): String? {
        if (!isConfigured()) return null
        val v = randomVerifier().also { verifier = it }
        val challenge = base64Url(sha256(v.toByteArray()))
        return "https://www.dropbox.com/oauth2/authorize" +
            "?client_id=${DropboxConfig.APP_KEY}" +
            "&response_type=code" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&token_access_type=offline" +
            "&redirect_uri=${enc(DropboxConfig.REDIRECT_URI)}"
    }

    override suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val v = verifier ?: return@withContext false
        val body = "code=${enc(code)}" +
            "&grant_type=authorization_code" +
            "&client_id=${DropboxConfig.APP_KEY}" +
            "&code_verifier=${enc(v)}" +
            "&redirect_uri=${enc(DropboxConfig.REDIRECT_URI)}"
        val json = postForm("https://api.dropboxapi.com/oauth2/token", body) ?: return@withContext false
        val token = json.optString("access_token").ifBlank { return@withContext false }
        prefs.edit {
            putString(KEY_TOKEN, token)
            json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { putString(KEY_REFRESH, it) }
        }
        _connected.value = true
        true
    }

    override suspend fun list(path: String): List<CloudEntry> = withContext(Dispatchers.IO) {
        val token = prefs.getString(KEY_TOKEN, null) ?: return@withContext emptyList()
        val payload = JSONObject().put("path", path).put("recursive", false).toString()
        val json = postJson("https://api.dropboxapi.com/2/files/list_folder", payload, token) ?: return@withContext emptyList()
        val entries = json.optJSONArray("entries") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                val folder = e.optString(".tag") == "folder"
                add(
                    CloudEntry(
                        name = e.optString("name"),
                        pathLower = e.optString("path_lower"),
                        isFolder = folder,
                        size = e.optLong("size", 0),
                        id = e.optString("id"),
                    ),
                )
            }
        }.sortedWith(compareByDescending<CloudEntry> { it.isFolder }.thenBy { it.name.lowercase() })
    }

    override suspend fun temporaryLink(pathLower: String): String? = withContext(Dispatchers.IO) {
        val token = prefs.getString(KEY_TOKEN, null) ?: return@withContext null
        val payload = JSONObject().put("path", pathLower).toString()
        postJson("https://api.dropboxapi.com/2/files/get_temporary_link", payload, token)?.optString("link")?.ifBlank { null }
    }

    override fun disconnect() {
        prefs.edit { clear() }
        _connected.value = false
    }

    // --- HTTP helpers -------------------------------------------------------------------

    private fun postForm(url: String, body: String): JSONObject? =
        request(url, body.toByteArray(), "application/x-www-form-urlencoded", null)

    private fun postJson(url: String, body: String, token: String): JSONObject? =
        request(url, body.toByteArray(), "application/json", token)

    private fun request(url: String, body: ByteArray, contentType: String, token: String?): JSONObject? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Content-Type", contentType)
        token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        conn.outputStream.use { os -> os.write(body) }
        val ok = conn.responseCode in 200..299
        val text = (if (ok) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (ok && text.isNotBlank()) JSONObject(text) else null
    }.getOrNull()

    // --- PKCE ---------------------------------------------------------------------------

    private fun randomVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}

/** Bridges the OAuth redirect (delivered to MainActivity) to whoever is waiting for the code. */
object CloudAuthBus {
    val pendingCode = MutableStateFlow<String?>(null)
    fun deliver(code: String) { pendingCode.value = code }
    fun consume() { pendingCode.value = null }
}
