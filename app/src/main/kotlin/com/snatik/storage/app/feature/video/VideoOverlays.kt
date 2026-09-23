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

/** Entrance/exit animations for an overlay. */
enum class TextAnim { NONE, FADE, SLIDE_UP, SLIDE_DOWN, SLIDE_LEFT, SLIDE_RIGHT, POP, SPIN }

/** Selectable animations: display name to enum. */
val TEXT_ANIMS: List<Pair<String, TextAnim>> = listOf(
    "None" to TextAnim.NONE, "Fade" to TextAnim.FADE,
    "Up" to TextAnim.SLIDE_UP, "Down" to TextAnim.SLIDE_DOWN,
    "Left" to TextAnim.SLIDE_LEFT, "Right" to TextAnim.SLIDE_RIGHT,
    "Pop" to TextAnim.POP, "Spin" to TextAnim.SPIN,
)

/** alpha 0..1, scale, dx/dy as a fraction of the frame, extra rotation degrees. */
data class AnimTransform(val alpha: Float, val scale: Float, val dxNorm: Float, val dyNorm: Float, val rotation: Float)
private val ANIM_IDENTITY = AnimTransform(1f, 1f, 0f, 0f, 0f)

private fun easeOut(t: Float): Float { val c = t.coerceIn(0f, 1f); return 1f - (1f - c) * (1f - c) }

private fun transformFor(anim: TextAnim, progress: Float, isOut: Boolean): AnimTransform {
    if (anim == TextAnim.NONE) return ANIM_IDENTITY
    val p = easeOut(progress) // p: 0 = hidden/entering, 1 = fully shown
    val hx = when (anim) { TextAnim.SLIDE_LEFT -> if (isOut) -0.6f else 0.6f; TextAnim.SLIDE_RIGHT -> if (isOut) 0.6f else -0.6f; else -> 0f }
    val hy = when (anim) { TextAnim.SLIDE_UP -> if (isOut) -0.45f else 0.45f; TextAnim.SLIDE_DOWN -> if (isOut) 0.45f else -0.45f; else -> 0f }
    val scale = when (anim) { TextAnim.POP -> 0.2f + 0.8f * p; TextAnim.SPIN -> 0.5f + 0.5f * p; else -> 1f }
    val rot = when (anim) { TextAnim.SPIN -> (1f - p) * (if (isOut) 120f else -120f); else -> 0f }
    return AnimTransform(p, scale, (1f - p) * hx, (1f - p) * hy, rot)
}

/** The overlay's animation transform at [globalMs] within its [start]..[end] window. */
fun overlayAnim(overlay: VideoOverlay, globalMs: Long, end: Long): AnimTransform {
    val start = overlay.startMs
    if (globalMs < start || globalMs >= end) return ANIM_IDENTITY
    val inMs = overlay.animInMs.coerceAtLeast(1)
    val outMs = overlay.animOutMs.coerceAtLeast(1)
    return when {
        overlay.animIn != TextAnim.NONE && globalMs < start + inMs -> transformFor(overlay.animIn, (globalMs - start).toFloat() / inMs, false)
        overlay.animOut != TextAnim.NONE && globalMs >= end - outMs -> transformFor(overlay.animOut, (end - globalMs).toFloat() / outMs, true)
        else -> ANIM_IDENTITY
    }
}

/** Selectable fonts for text overlays: display name to Android family name. */
val TEXT_FONTS: List<Pair<String, String>> = listOf(
    "Sans" to "sans-serif",
    "Serif" to "serif",
    "Mono" to "monospace",
    "Condensed" to "sans-serif-condensed",
    "Black" to "sans-serif-black",
    "Light" to "sans-serif-light",
    "Cursive" to "cursive",
)

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
    val font: String = "sans-serif", // Android family name, see TEXT_FONTS
    val bold: Boolean = true,
    val italic: Boolean = false,
    val outline: Boolean = false,
    val outlineColor: Int = Color.BLACK,
    val rotationDegrees: Float = 0f,
    val animIn: TextAnim = TextAnim.NONE,
    val animOut: TextAnim = TextAnim.NONE,
    val animInMs: Long = 400,
    val animOutMs: Long = 400,
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
    val base = runCatching { Typeface.create(overlay.font.ifBlank { "sans-serif" }, Typeface.NORMAL) }.getOrDefault(Typeface.DEFAULT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface = Typeface.create(base, style) // apply bold/italic on top of the chosen family
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
private fun settingsFor(overlay: VideoOverlay, t: AnimTransform): OverlaySettings {
    // Normalized top-left (0..1) centre -> NDC (-1..1, y up), plus the animation offset.
    val ndcX = (overlay.xNorm + t.dxNorm) * 2f - 1f
    val ndcY = 1f - (overlay.yNorm + t.dyNorm) * 2f
    return StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(ndcX, ndcY)
        .setOverlayFrameAnchor(0f, 0f)
        .setScale(t.scale, t.scale)
        .setAlphaScale(t.alpha.coerceIn(0f, 1f))
        .setRotationDegrees(overlay.rotationDegrees + t.rotation) // matches the preview's clockwise rotation
        .build()
}

/** A bitmap overlay whose settings animate over time (fade/slide/pop/spin in and out). */
@UnstableApi
private class TimedBitmap(
    private val bitmap: Bitmap,
    private val overlay: VideoOverlay,
    private val clipStartGlobalMs: Long,
    private val windowEndMs: Long,
    private val startUs: Long,
    private val endUs: Long,
) : BitmapOverlay() {
    private val blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    override fun getBitmap(presentationTimeUs: Long): Bitmap =
        if (presentationTimeUs in startUs until endUs) bitmap else blank
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val globalMs = clipStartGlobalMs + presentationTimeUs / 1000
        return settingsFor(overlay, overlayAnim(overlay, globalMs, windowEndMs))
    }
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
    videoTotalMs: Long,
    frameHeightPx: Int,
    frameWidthPx: Int,
): List<androidx.media3.effect.TextureOverlay> = overlays.mapNotNull { ov ->
    val windowEnd = if (ov.endMs == Long.MAX_VALUE) videoTotalMs else ov.endMs // for the out-animation
    val s = maxOf(ov.startMs, clipStartGlobalMs)
    val e = minOf(windowEnd, clipEndGlobalMs)
    if (e <= s) return@mapNotNull null
    val startUs = (s - clipStartGlobalMs) * 1000
    val endUs = (e - clipStartGlobalMs) * 1000
    when (ov.kind) {
        OverlayKind.TEXT -> {
            val bmp = renderTextOverlayBitmap(ov, frameHeightPx) ?: return@mapNotNull null
            TimedBitmap(bmp, ov, clipStartGlobalMs, windowEnd, startUs, endUs)
        }
        OverlayKind.IMAGE -> {
            val bmp = ov.bitmap ?: return@mapNotNull null
            TimedBitmap(bmp, ov, clipStartGlobalMs, windowEnd, startUs, endUs)
        }
    }
}
