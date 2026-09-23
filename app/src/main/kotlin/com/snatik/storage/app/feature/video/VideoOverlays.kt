package com.snatik.storage.app.feature.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

enum class OverlayKind { TEXT, IMAGE }

/**
 * A text/sticker/image overlay placed on the output at a normalized centre ([xNorm], [yNorm], y from
 * top) for a time window ([startMs]..[endMs] on the global timeline). Text is rendered to a bitmap so
 * it can carry a fill [color], an [outline] in [outlineColor], a [background] chip in [bgColor],
 * [bold]/[italic] styles, [rotationDegrees] and any [sizeFraction].
 */
data class VideoOverlay(
    val id: Long,
    val kind: OverlayKind,
    val text: String = "",
    val imageUri: Uri? = null,
    val bitmap: Bitmap? = null,       // decoded sticker/image, for preview and export
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.5f,
    val sizeFraction: Float = 0.08f,  // text height / image width as a fraction of the frame
    val color: Int = Color.WHITE,
    val background: Boolean = false,
    val bgColor: Int = 0xCC000000.toInt(),
    val bold: Boolean = true,
    val italic: Boolean = false,
    val outline: Boolean = false,
    val outlineColor: Int = Color.BLACK,
    val rotationDegrees: Float = 0f,
    val startMs: Long = 0,
    val endMs: Long = Long.MAX_VALUE,
) {
    fun activeAt(globalMs: Long): Boolean = globalMs in startMs until endMs
}

/**
 * Render a text overlay's styled text to a bitmap (fill, outline, background chip, bold/italic). Used
 * for both the preview and the export so what you see is what you get. Not rotated - rotation is applied
 * as an overlay transform (preview and export) so the bitmap stays axis-aligned.
 */
fun renderTextOverlayBitmap(overlay: VideoOverlay, frameHeightPx: Int): Bitmap? {
    if (overlay.text.isBlank() || frameHeightPx <= 0) return null
    val textSize = (frameHeightPx * overlay.sizeFraction).coerceIn(14f, frameHeightPx.toFloat())
    val style = when {
        overlay.bold && overlay.italic -> Typeface.BOLD_ITALIC
        overlay.bold -> Typeface.BOLD
        overlay.italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface = Typeface.create(Typeface.DEFAULT, style)
    }
    val strokeW = if (overlay.outline) textSize * 0.10f else 0f
    val fm = paint.fontMetrics
    val padX = (textSize * 0.35f + strokeW)
    val padY = (textSize * 0.22f + strokeW)
    val textW = paint.measureText(overlay.text)
    val w = (textW + padX * 2f).toInt().coerceAtLeast(1)
    val h = ((fm.bottom - fm.top) + padY * 2f).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    if (overlay.background) {
        val r = textSize * 0.18f
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = overlay.bgColor })
    }
    val baseline = padY - fm.top
    if (strokeW > 0f) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeW
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = overlay.outlineColor
        canvas.drawText(overlay.text, padX, baseline, paint)
    }
    paint.style = Paint.Style.FILL
    paint.color = overlay.color
    canvas.drawText(overlay.text, padX, baseline, paint)
    return bmp
}

@UnstableApi
private fun settingsFor(overlay: VideoOverlay): OverlaySettings {
    // Normalized top-left (0..1) centre -> NDC (-1..1, y up), overlay anchored at its own centre.
    val ndcX = overlay.xNorm * 2f - 1f
    val ndcY = 1f - overlay.yNorm * 2f
    return StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(ndcX, ndcY)
        .setOverlayFrameAnchor(0f, 0f)
        .setRotationDegrees(overlay.rotationDegrees) // matches the preview's clockwise rotation
        .build()
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
            val bmp = renderTextOverlayBitmap(ov, frameHeightPx) ?: return@mapNotNull null
            TimedBitmap(bmp, settings, startUs, endUs)
        }
        OverlayKind.IMAGE -> {
            val bmp = ov.bitmap ?: return@mapNotNull null
            TimedBitmap(bmp, settings, startUs, endUs)
        }
    }
}
