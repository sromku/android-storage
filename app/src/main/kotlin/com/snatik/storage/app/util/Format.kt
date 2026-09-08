package com.snatik.storage.app.util

import android.content.Context
import android.text.format.DateUtils
import com.snatik.storage.toReadableSize

fun Long.readableSize(): String = toReadableSize()

/** "5 min. ago", "Yesterday", "Mar 3" style relative timestamp. Future timestamps (clock skew) show the absolute date. */
fun Long.relativeTime(context: Context): String {
    val now = System.currentTimeMillis()
    return if (this > now + DateUtils.MINUTE_IN_MILLIS) {
        DateUtils.formatDateTime(context, this, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
    } else {
        DateUtils.getRelativeTimeSpanString(this, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}

fun Long.fullDateTime(context: Context): String =
    DateUtils.formatDateTime(context, this, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR)
