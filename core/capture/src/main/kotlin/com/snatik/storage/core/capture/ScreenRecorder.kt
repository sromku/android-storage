package com.snatik.storage.core.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File

/** Records the screen through a MediaProjection into an MP4 file. */
class ScreenRecorder(private val context: Context) {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null

    val isRecording: Boolean get() = recorder != null

    fun start(projection: MediaProjection, output: File) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        context.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        // Halve very tall displays so the encoder keeps up; keep dimensions even.
        val scale = if (metrics.heightPixels > 1600) 2 else 1
        val width = metrics.widthPixels / scale and 1.inv()
        val height = metrics.heightPixels / scale and 1.inv()

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stop() }
        }, Handler(Looper.getMainLooper()))

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        recorder.setVideoSize(width, height)
        recorder.setVideoFrameRate(30)
        recorder.setVideoEncodingBitRate(6_000_000)
        recorder.setOutputFile(output.absolutePath)
        recorder.prepare()
        display = projection.createVirtualDisplay("storage-recorder", width, height, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder.surface, null, null)
        recorder.start()
        this.recorder = recorder
        this.projection = projection
    }

    fun stop() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        display?.release()
        display = null
        projection?.stop()
        projection = null
    }
}
