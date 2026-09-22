package com.snatik.storage.app.feature.video

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay

enum class OverlayKind { TEXT, IMAGE }

/**
 * A text/sticker/image overlay placed on the output at a normalized centre ([xNorm], [yNorm], y from
 * top) for a time window ([startMs]..[endMs] on the global timeline). Text with [background] on reads
 * as a price tag / chip.
 */
data class VideoOverlay(
    val id: Long,
    val kind: OverlayKind,
    val text: String = "",
    val imageUri: Uri? = null,
    val bitmap: Bitmap? = null,       // decoded sticker/image, for preview and export
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.5f,
    val sizeFraction: Float = 0.07f,  // text height / image width as a fraction of the frame
    val color: Int = Color.WHITE,
    val background: Boolean = false,
    val startMs: Long = 0,
    val endMs: Long = Long.MAX_VALUE,
) {
    fun activeAt(globalMs: Long): Boolean = globalMs in startMs until endMs
}

@UnstableApi
private fun settingsFor(overlay: VideoOverlay): OverlaySettings {
    // Normalized top-left (0..1) centre -> NDC (-1..1, y up), overlay anchored at its own centre.
    val ndcX = overlay.xNorm * 2f - 1f
    val ndcY = 1f - overlay.yNorm * 2f
    return OverlaySettings.Builder()
        .setBackgroundFrameAnchor(ndcX, ndcY)
        .setOverlayFrameAnchor(0f, 0f)
        .build()
}

@UnstableApi
private class TimedText(
    private val span: SpannableString,
    private val settings: OverlaySettings,
    private val startUs: Long,
    private val endUs: Long,
) : TextOverlay() {
    private val empty = SpannableString("")
    override fun getText(presentationTimeUs: Long): SpannableString =
        if (presentationTimeUs in startUs until endUs) span else empty
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}

@UnstableApi
private class TimedBitmap(
    private val bitmap: Bitmap,
    private val settings: OverlaySettings,
    private val startUs: Long,
    private val endUs: Long,
) : BitmapOverlay() {
    private val blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    override fun getBitmap(presentationTimeUs: Long): Bitmap =
        if (presentationTimeUs in startUs until endUs) bitmap else blank
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}

/**
 * Build the timed overlays for one clip that spans global [clipStartGlobalMs]..[clipEndGlobalMs].
 * Presentation time inside a clipped item starts at 0, so windows are shifted into clip-local time.
 */
@UnstableApi
fun overlaysForClip(
    overlays: List<VideoOverlay>,
    clipStartGlobalMs: Long,
    clipEndGlobalMs: Long,
    frameHeightPx: Int,
    frameWidthPx: Int,
): List<androidx.media3.effect.TextureOverlay> = overlays.mapNotNull { ov ->
    val s = maxOf(ov.startMs, clipStartGlobalMs)
    val e = minOf(if (ov.endMs == Long.MAX_VALUE) clipEndGlobalMs else ov.endMs, clipEndGlobalMs)
    if (e <= s) return@mapNotNull null
    val startUs = (s - clipStartGlobalMs) * 1000
    val endUs = (e - clipStartGlobalMs) * 1000
    val settings = settingsFor(ov)
    when (ov.kind) {
        OverlayKind.TEXT -> {
            if (ov.text.isBlank()) return@mapNotNull null
            val span = SpannableString(ov.text)
            val px = (frameHeightPx * ov.sizeFraction).toInt().coerceIn(14, frameHeightPx)
            span.setSpan(AbsoluteSizeSpan(px), 0, ov.text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            span.setSpan(ForegroundColorSpan(ov.color), 0, ov.text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            span.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, ov.text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            if (ov.background) span.setSpan(BackgroundColorSpan(0xCC000000.toInt()), 0, ov.text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            TimedText(span, settings, startUs, endUs)
        }
        OverlayKind.IMAGE -> {
            val bmp = ov.bitmap ?: return@mapNotNull null
            TimedBitmap(bmp, settings, startUs, endUs)
        }
    }
}
