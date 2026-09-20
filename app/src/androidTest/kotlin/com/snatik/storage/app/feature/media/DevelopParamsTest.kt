package com.snatik.storage.app.feature.media

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Drives RawDeveloper directly and measures the mean per-channel pixel change each develop
 * control produces vs a baseline render. A near-zero diff means the control is a no-op.
 * Run: adb shell am instrument -w -e class ...DevelopParamsTest <pkg>/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class DevelopParamsTest {

    private val TAG = "PARAMTEST"

    private fun pixels(b: Bitmap): IntArray = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private fun meanDiff(a: IntArray, b: IntArray): Double {
        if (a.size != b.size) return -1.0
        var sum = 0L
        val step = if (a.size > 400_000) a.size / 400_000 else 1  // sample for speed
        var n = 0
        var i = 0
        while (i < a.size) {
            val pa = a[i]; val pb = b[i]
            sum += abs(((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF)).toLong()
            sum += abs(((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF)).toLong()
            sum += abs((pa and 0xFF) - (pb and 0xFF)).toLong()
            n += 3; i += step
        }
        return if (n == 0) 0.0 else sum.toDouble() / n
    }

    private fun greenHsl(hue: Float = 0f, sat: Float = 0f, lum: Float = 0f): List<Float> {
        val l = MutableList(24) { 0f }
        l[HslBand.GREEN.ordinal * 3] = hue
        l[HslBand.GREEN.ordinal * 3 + 1] = sat
        l[HslBand.GREEN.ordinal * 3 + 2] = lum
        return l
    }

    @Test
    fun eachControlChangesOutput() {
        val path = listOf(
            "/sdcard/Pictures/DSC09166.ARW",
            "/sdcard/Pictures/DSC09404.ARW",
        ).firstOrNull { File(it).exists() }
        org.junit.Assume.assumeTrue("no test ARW on device; skipping", path != null)
        path!!

        val dev = RawDeveloper.open(path) ?: error("RawDeveloper.open failed for $path")
        try {
            // half-res baseline (matches what most controls run on in preview)
            val baseHalf = pixels(dev.render(DevelopParams(), half = true) ?: error("baseline half render failed"))
            // full-res baseline for demosaic/NR, which half_size binning ignores
            val baseFull = pixels(dev.render(DevelopParams(), half = false) ?: error("baseline full render failed"))

            data class Case(val name: String, val p: DevelopParams, val full: Boolean = false)
            val sCurve = ToneCurve(rgb = listOf(0f to 0f, 0.25f to 0.12f, 0.75f to 0.88f, 1f to 1f))
            val cases = listOf(
                Case("exposure", DevelopParams(exposure = 1.5f)),
                Case("brightness", DevelopParams(bright = 1.8f)),
                Case("highlight-blend", DevelopParams(highlight = 2)),
                Case("highlight-rebuild", DevelopParams(highlight = 3, highlightLevel = 7)),
                Case("contrast", DevelopParams(contrast = 80f)),
                Case("highlightsTone", DevelopParams(highlightsTone = -80f)),
                Case("shadows", DevelopParams(shadows = 80f)),
                Case("whites", DevelopParams(whites = 90f)),
                Case("blacks", DevelopParams(blacks = -80f)),
                Case("texture", DevelopParams(texture = 100f)),
                Case("clarity", DevelopParams(clarity = 100f)),
                Case("dehaze", DevelopParams(dehaze = 100f)),
                Case("sharpen", DevelopParams(sharpen = 100f, sharpenRadius = 2f)),
                Case("saturation", DevelopParams(saturation = 80f)),
                Case("vibrance", DevelopParams(vibrance = 100f)),
                Case("colorspace-prophoto", DevelopParams(colorSpace = ColorSpace.PROPHOTO)),
                Case("vignette", DevelopParams(vignette = -80f)),
                Case("grain", DevelopParams(grain = 100f)),
                Case("caRed", DevelopParams(caRed = 100f), full = true),
                Case("caBlue", DevelopParams(caBlue = 100f), full = true),
                Case("wb-auto", DevelopParams(wb = RawWb.AUTO)),
                Case("wb-temp", DevelopParams(wb = RawWb.CUSTOM, temp = 9000f)),
                Case("wb-tint", DevelopParams(wb = RawWb.CUSTOM, tint = 80f)),
                Case("hsl-green-hue", DevelopParams(hsl = greenHsl(hue = 100f))),
                Case("hsl-green-sat", DevelopParams(hsl = greenHsl(sat = -100f))),
                Case("hsl-green-lum", DevelopParams(hsl = greenHsl(lum = -80f))),
                Case("curve", DevelopParams(curve = sCurve)),
                Case("demosaic-vng", DevelopParams(demosaic = Demosaic.VNG), full = true),
                Case("fbdd-nr", DevelopParams(fbdd = 2), full = true),
                Case("wavelet-nr", DevelopParams(threshold = 900f), full = true),
            )

            val report = StringBuilder("\n==== DEVELOP PARAM DIFFS (mean 0..255 per channel) ====\n")
            for (c in cases) {
                val bmp = dev.render(c.p, half = !c.full)
                val d = if (bmp == null) -1.0 else meanDiff(if (c.full) baseFull else baseHalf, pixels(bmp))
                bmp?.recycle()
                val flag = when {
                    d < 0 -> "RENDER-FAILED"
                    d < 0.25 -> "NO-OP <<<<"
                    d < 1.0 -> "weak"
                    else -> "ok"
                }
                report.appendLine("%-22s %8.3f  %s%s".format(c.name, d, flag, if (c.full) "  (full-res)" else ""))
            }
            Log.e(TAG, report.toString())
            println(report.toString())
        } finally {
            dev.close()
        }
    }
}
