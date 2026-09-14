package com.snatik.storage.core.capture

import android.content.Context
import android.os.Build
import android.os.FileObserver
import com.snatik.storage.core.intents.BroadcastStore
import com.snatik.storage.core.shell.PrivilegeManager
import com.snatik.storage.core.shell.lines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class RecordSource { LOGCAT, BROADCASTS, FILES, SCREEN }

data class RecordingConfig(
    val name: String,
    val sources: Set<RecordSource>,
    /** logcat filter spec such as `MyTag:V *:S`, or empty for everything. */
    val logcatFilter: String = "",
    /** Restrict logcat to these packages' uids; empty means every app. */
    val logcatPackages: List<String> = emptyList(),
    val watchPaths: List<String> = emptyList(),
)

/**
 * One recording at a time: logcat through the shell, broadcasts from the monitor, file changes
 * from FileObserver, all stored as timestamped events. The screen part is driven by the caller,
 * which owns the MediaProjection.
 */
class RecordingEngine(
    private val context: Context,
    private val db: CaptureDatabase,
    private val privilege: PrivilegeManager,
    private val broadcasts: BroadcastStore,
    private val scope: CoroutineScope,
) {
    val recordings: Flow<List<RecordingEntity>> = db.recordings().recordings()

    private val _active = MutableStateFlow<RecordingEntity?>(null)
    val active: StateFlow<RecordingEntity?> = _active.asStateFlow()

    private val _liveCount = MutableStateFlow(0)
    val liveCount: StateFlow<Int> = _liveCount.asStateFlow()

    private val jobs = ArrayList<Job>()
    private val observers = ArrayList<FileObserver>()
    private val pending = ArrayList<RecordingEventEntity>()
    private var flushJob: Job? = null
    private var count = 0

    val isRecording: Boolean get() = _active.value != null

    suspend fun start(config: RecordingConfig, videoPath: String?): RecordingEntity {
        require(!isRecording) { "Already recording" }
        val entity = RecordingEntity(
            name = config.name.ifBlank { "Recording" },
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            sources = config.sources.joinToString(",") { it.name },
            logcatFilter = config.logcatFilter.ifBlank { null },
            logcatPackage = config.logcatPackages.joinToString(",").ifEmpty { null },
            watchPaths = config.watchPaths.joinToString("\n"),
            videoPath = videoPath,
            eventCount = 0,
        )
        val id = db.recordings().insert(entity)
        val stored = entity.copy(id = id)
        _active.value = stored
        count = 0
        _liveCount.value = 0

        if (RecordSource.LOGCAT in config.sources) startLogcat(id, config)
        if (RecordSource.BROADCASTS in config.sources) startBroadcasts(id)
        if (RecordSource.FILES in config.sources) startFileWatch(id, config.watchPaths)
        flushJob = scope.launch { while (true) { delay(500); flush() } }
        return stored
    }

    suspend fun stop(): RecordingEntity? {
        val current = _active.value ?: return null
        jobs.forEach { it.cancel() }
        jobs.clear()
        observers.forEach { it.stopWatching() }
        observers.clear()
        flushJob?.cancel()
        flushJob = null
        flush()
        val finished = current.copy(endedAt = System.currentTimeMillis(), eventCount = count)
        db.recordings().update(finished)
        _active.value = null
        return finished
    }

    suspend fun recording(id: Long): RecordingEntity? = db.recordings().recording(id)
    suspend fun events(id: Long): List<RecordingEventEntity> = db.recordings().events(id)

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.recordings().recording(id)?.videoPath?.let { File(it).delete() }
        db.recordings().deleteEvents(id)
        db.recordings().delete(id)
    }

    /** Zip the events as text plus the video into [targetDir]. Returns the zip. */
    suspend fun export(id: Long, targetDir: File): File = withContext(Dispatchers.IO) {
        val recording = db.recordings().recording(id) ?: error("Recording $id is gone")
        val events = db.recordings().events(id)
        targetDir.mkdirs()
        val zip = File(targetDir, "recording-${recording.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}-${recording.startedAt}.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("events.jsonl"))
            val writer = out.bufferedWriter()
            for (e in events) {
                writer.write("""{"time":${e.time},"source":"${e.source}","tag":${e.tag?.let { "\"" + escape(it) + "\"" } ?: "null"},"text":"${escape(e.text)}"}""")
                writer.newLine()
            }
            writer.flush()
            out.closeEntry()
            out.putNextEntry(ZipEntry("events.txt"))
            for (e in events) {
                writer.write("${e.time} ${e.source}${e.tag?.let { " [$it]" } ?: ""} ${e.text}")
                writer.newLine()
            }
            writer.flush()
            out.closeEntry()
            recording.videoPath?.let { File(it) }?.takeIf { it.isFile }?.let { video ->
                out.putNextEntry(ZipEntry(video.name))
                video.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        zip
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun record(id: Long, source: RecordSource, tag: String?, text: String) {
        synchronized(pending) {
            pending += RecordingEventEntity(sessionId = id, time = System.currentTimeMillis(), source = source.name, tag = tag, text = text)
            count++
        }
        _liveCount.value = count
    }

    private suspend fun flush() {
        val batch = synchronized(pending) { if (pending.isEmpty()) null else ArrayList(pending).also { pending.clear() } } ?: return
        db.recordings().insertEvents(batch)
    }

    private fun startLogcat(id: Long, config: RecordingConfig) {
        val shell = privilege.executor.value
        if (shell == null) {
            record(id, RecordSource.LOGCAT, "storage", "logcat needs shell access; connect Shizuku")
            return
        }
        val uids = config.logcatPackages
            .mapNotNull { pkg -> runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull() }
            .distinct()
        val command = buildString {
            append("logcat -v threadtime -T 1")
            if (uids.isNotEmpty()) append(" --uid=").append(uids.joinToString(","))
            if (config.logcatFilter.isNotBlank()) append(' ').append(config.logcatFilter)
        }
        jobs += scope.launch(Dispatchers.IO) {
            try {
                shell.lines(command).collect { line -> parseLogcat(id, line) }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) record(id, RecordSource.LOGCAT, "storage", "logcat stopped: ${e.message}")
            }
        }
    }

    private fun parseLogcat(id: Long, line: String) {
        // threadtime: "09-08 12:34:56.789  1234  5678 D Tag     : message"
        val m = LOGCAT.find(line)
        if (m != null) record(id, RecordSource.LOGCAT, "${m.groupValues[1]}/${m.groupValues[2].trim()}", m.groupValues[3])
        else if (line.isNotBlank() && !line.startsWith("--------- beginning")) record(id, RecordSource.LOGCAT, null, line)
    }

    private fun startBroadcasts(id: Long) {
        jobs += scope.launch {
            var seen: Long? = null
            var first = true
            broadcasts.recent.collect { events ->
                if (first) {
                    seen = events.firstOrNull()?.id
                    first = false
                    return@collect
                }
                val fresh = events.takeWhile { it.id != seen }
                seen = events.firstOrNull()?.id ?: seen
                fresh.asReversed().forEach { e -> record(id, RecordSource.BROADCASTS, e.spec.action, e.spec.toJson()) }
            }
        }
    }

    private fun startFileWatch(id: Long, paths: List<String>) {
        val files = paths.map(::File).filter { it.exists() }
        if (files.isEmpty()) return
        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(files, MASK) {
                override fun onEvent(event: Int, path: String?) { record(id, RecordSource.FILES, eventName(event), path ?: "") }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(files.first().absolutePath, MASK) {
                override fun onEvent(event: Int, path: String?) { record(id, RecordSource.FILES, eventName(event), path ?: "") }
            }
        }
        observer.startWatching()
        observers += observer
    }

    private fun eventName(event: Int): String = when (event and FileObserver.ALL_EVENTS) {
        FileObserver.CREATE -> "create"
        FileObserver.DELETE -> "delete"
        FileObserver.MODIFY -> "modify"
        FileObserver.MOVED_FROM -> "moved_from"
        FileObserver.MOVED_TO -> "moved_to"
        FileObserver.ATTRIB -> "attrib"
        FileObserver.CLOSE_WRITE -> "close_write"
        FileObserver.DELETE_SELF -> "delete_self"
        FileObserver.MOVE_SELF -> "move_self"
        else -> "event_$event"
    }

    private companion object {
        const val MASK = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MODIFY or FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or FileObserver.ATTRIB or FileObserver.CLOSE_WRITE or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
        val LOGCAT = Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+\s+([VDIWEF])\s+(.*?)\s*:\s?(.*)$""")
    }
}
