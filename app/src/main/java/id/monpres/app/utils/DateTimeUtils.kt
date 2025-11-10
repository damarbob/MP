package id.monpres.app.utils

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.Timestamp
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/**
 * Formats a Firebase Timestamp into a localized display string for both date and time.
 *
 * @param dateStyle The style for the date part (e.g., DateFormat.MEDIUM).
 * @param timeStyle The style for the time part (e.g., DateFormat.SHORT).
 * @return A formatted string like "Oct 29, 2025, 10:30 AM".
 */
fun Timestamp?.toDateTimeDisplayString(
    dateStyle: Int = DateFormat.MEDIUM,
    timeStyle: Int = DateFormat.SHORT,
    locale: Locale = Locale.getDefault()
): String {
    // For Android O+, we can use the modern API which is more powerful.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return formatModern(this?.toInstant(), dateStyle, timeStyle, locale, includeDate = true, includeTime = true)
    }
    // For older versions, use the legacy DateFormat.
    return formatLegacy(this?.toDate(), dateStyle, timeStyle, locale, includeDate = true, includeTime = true)
}

/**
 * Formats a Firebase Timestamp into a localized display string for the date part only.
 *
 * @param dateStyle The style for the date part (e.g., DateFormat.MEDIUM).
 * @return A formatted string like "Oct 29, 2025".
 */
fun Timestamp?.toDateDisplayString(
    dateStyle: Int = DateFormat.MEDIUM,
    locale: Locale = Locale.getDefault()
): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return formatModern(this?.toInstant(), dateStyle, null, locale, includeDate = true, includeTime = false)
    }
    return formatLegacy(this?.toDate(), dateStyle, null, locale, includeDate = true, includeTime = false)
}

/**
 * Formats a Firebase Timestamp into a localized display string for the time part only.
 *
 * @param timeStyle The style for the time part (e.g., DateFormat.SHORT).
 * @return A formatted string like "10:30 AM".
 */
fun Timestamp?.toTimeDisplayString(
    timeStyle: Int = DateFormat.SHORT,
    locale: Locale = Locale.getDefault()
): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return formatModern(this?.toInstant(), null, timeStyle, locale, includeDate = false, includeTime = true)
    }
    return formatLegacy(this?.toDate(), null, timeStyle, locale, includeDate = false, includeTime = true)
}


// --- PRIVATE CORE FUNCTIONS ---

/**
 * Core formatter for modern Android (API 26+).
 * It can format date, time, or both.
 */
@RequiresApi(Build.VERSION_CODES.O)
private fun formatModern(
    instant: Instant?,
    dateStyle: Int?, // <-- Now accepts Int?
    timeStyle: Int?, // <-- Now accepts Int?
    locale: Locale,
    includeDate: Boolean,
    includeTime: Boolean
): String {
    if (instant == null) return ""
    val zdt = instant.atZone(ZoneId.systemDefault())

    // ***FIX:*** Use the mapper to convert from Int to FormatStyle.
    val modernDateStyle = dateStyle?.toFormatStyle()
    val modernTimeStyle = timeStyle?.toFormatStyle()

    val formatter = when {
        includeDate && includeTime -> DateTimeFormatter.ofLocalizedDateTime(modernDateStyle, modernTimeStyle)
        includeDate -> DateTimeFormatter.ofLocalizedDate(modernDateStyle)
        includeTime -> DateTimeFormatter.ofLocalizedTime(modernTimeStyle)
        else -> return "" // Should not happen
    }.withLocale(locale)

    return zdt.format(formatter)
}

/**
 * Core formatter for legacy Android (< API 26).
 * It can format date, time, or both.
 */
private fun formatLegacy(
    date: Date?,
    dateStyle: Int?,
    timeStyle: Int?,
    locale: Locale,
    includeDate: Boolean,
    includeTime: Boolean
): String {
    if (date == null) return ""

    val formatter = when {
        includeDate && includeTime -> DateFormat.getDateTimeInstance(dateStyle!!, timeStyle!!, locale)
        includeDate -> DateFormat.getDateInstance(dateStyle!!, locale)
        includeTime -> DateFormat.getTimeInstance(timeStyle!!, locale)
        else -> return "" // Should not happen
    }
    return formatter.format(date)
}

/**
 * ***NEW:*** A private mapper function to convert DateFormat Int constants
 * to the modern java.time.format.FormatStyle enum.
 */
@RequiresApi(Build.VERSION_CODES.O)
private fun Int.toFormatStyle(): FormatStyle {
    return when (this) {
        DateFormat.FULL -> FormatStyle.FULL
        DateFormat.LONG -> FormatStyle.LONG
        DateFormat.MEDIUM -> FormatStyle.MEDIUM
        DateFormat.SHORT -> FormatStyle.SHORT
        else -> FormatStyle.MEDIUM // Default fallback
    }
}