package com.snatik.storage.core.fs

import android.webkit.MimeTypeMap

enum class FileKind {
    DIRECTORY, IMAGE, VIDEO, AUDIO, TEXT, CODE, JSON, XML, PDF, ARCHIVE, APK, DATABASE, FONT, OTHER;

    /** Kinds the built-in text viewer can display. */
    val isTextLike: Boolean get() = this == TEXT || this == CODE || this == JSON || this == XML

    companion object {
        private val image = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "ico")
        private val video = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts")
        private val audio = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "mid")
        private val text = setOf("txt", "md", "log", "csv", "tsv", "ini", "cfg", "conf", "properties", "env", "rst", "srt", "vtt", "nfo")
        private val code = setOf(
            "kt", "kts", "java", "c", "cc", "cpp", "h", "hpp", "rs", "go", "py", "rb", "js", "ts", "tsx", "jsx", "mjs",
            "sh", "bash", "zsh", "bat", "ps1", "sql", "gradle", "toml", "yaml", "yml", "html", "htm", "css", "scss",
            "dart", "swift", "m", "lua", "pl", "php", "proto", "smali", "mk", "cmake", "prop",
        )
        private val archive = setOf("zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar", "aar", "apks", "xapk", "zst")
        private val database = setOf("db", "sqlite", "sqlite3", "db-wal", "db-shm", "db-journal", "realm")
        private val font = setOf("ttf", "otf", "woff", "woff2")

        fun of(name: String, isDirectory: Boolean): FileKind {
            if (isDirectory) return DIRECTORY
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                ext.isEmpty() -> OTHER
                ext == "apk" -> APK
                ext == "json" -> JSON
                ext == "xml" -> XML
                ext == "pdf" -> PDF
                ext in image -> IMAGE
                ext in video -> VIDEO
                ext in audio -> AUDIO
                ext in text -> TEXT
                ext in code -> CODE
                ext in archive -> ARCHIVE
                ext in database -> DATABASE
                ext in font -> FONT
                else -> OTHER
            }
        }
    }
}

object MimeTypes {
    private val extra = mapOf(
        "kt" to "text/x-kotlin", "kts" to "text/x-kotlin", "json" to "application/json", "md" to "text/markdown",
        "log" to "text/plain", "yaml" to "text/yaml", "yml" to "text/yaml", "toml" to "text/plain", "properties" to "text/plain",
        "apk" to "application/vnd.android.package-archive", "db" to "application/vnd.sqlite3", "sqlite" to "application/vnd.sqlite3",
        "sh" to "text/x-shellscript", "gradle" to "text/plain", "proto" to "text/plain",
    )

    fun of(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return extra[ext]
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }
}
