package com.snatik.storage.app.feature.monitor

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.snatik.storage.app.MainActivity
import com.snatik.storage.app.R

/**
 * Posts a deliberately maximal notification to exercise the monitor's parsing: large icon, a big
 * picture attachment, sub-text, a long description with expanded big text, a summary, action buttons
 * and a progress bar. Everything is generated in-process so it needs no assets.
 */
object TestNotification {

    const val CHANNEL = "test_rich"

    // notify() is wrapped in runCatching and the app requests POST_NOTIFICATIONS; a denied
    // permission simply means the test notification isn't posted, never a crash.
    @SuppressLint("MissingPermission")
    fun send(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_test_channel), NotificationManager.IMPORTANCE_HIGH),
        )

        val largeIcon = roundedGradient(256, 256, 0xFF6D5DF6.toInt(), 0xFF00B4D8.toInt(), "N")
        val bigPicture = bannerGradient(1024, 512, 0xFF7C4DFF.toInt(), 0xFFFF6584.toInt(), context.getString(R.string.notif_test_picture_caption))

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_text))
            .setSubText(context.getString(R.string.notif_test_subtext))
            .setLargeIcon(largeIcon)
            .setColor(0xFF6D5DF6.toInt())
            .setColorized(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setProgress(100, 65, false)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_view, context.getString(R.string.notif_test_action_open), open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.notif_test_action_dismiss), open)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bigPicture)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(context.getString(R.string.notif_test_summary)),
            )

        runCatching { NotificationManagerCompat.from(context).notify(TEST_ID, builder.build()) }
    }

    private const val TEST_ID = 918

    /** A rounded-square gradient tile with a centered glyph — used as the large icon. */
    private fun roundedGradient(w: Int, h: Int, from: Int, to: Int, glyph: String): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), from, to, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), w * 0.28f, h * 0.28f, paint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = h * 0.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cy = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(glyph, w / 2f, cy, textPaint)
        return bmp
    }

    /** A wide gradient banner with a caption — used as the big-picture attachment. */
    private fun bannerGradient(w: Int, h: Int, from: Int, to: Int, caption: String): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), from, to, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        // Decorative circles for texture.
        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255) }
        canvas.drawCircle(w * 0.8f, h * 0.3f, h * 0.35f, circle)
        canvas.drawCircle(w * 0.2f, h * 0.75f, h * 0.22f, circle)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = h * 0.16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cy = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(caption, w / 2f, cy, textPaint)
        return bmp
    }
}
