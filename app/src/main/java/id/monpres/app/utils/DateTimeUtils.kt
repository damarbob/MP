package id.monpres.app.utils

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.Timestamp
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

fun Timestamp?.toDisplayString(locale: Locale = Locale.getDefault()): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        formatDateWithLocalizedStyle(
            LocalDateTime.ofInstant(
                this?.toInstant(),
                ZoneId.systemDefault()
            ), locale
        )
    } else {
        formatDateAndroidNougat(this?.toDate(), locale)
    }
}

fun Timestamp?.toDateTimeDisplayString(locale: Locale = Locale.getDefault(), dateStyle: Int = DateFormat.MEDIUM, timeStyle: Int = DateFormat.SHORT): String {
    return formatDateWithLocaleAndStyle(this?.toDate(), locale, dateStyle, timeStyle)
}

private fun formatDateAndroidNougat(date: Date?, locale: Locale): String {
    val formatter = SimpleDateFormat("dd MMMM yyyy", locale)
    return if (date == null) "" else formatter.format(date)
}

@RequiresApi(Build.VERSION_CODES.O)
private fun formatDateWithLocalizedStyle(date: LocalDateTime?, locale: Locale): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return if (date == null) "" else date.format(formatter)
}

fun formatDateWithLocaleAndStyle(date: Date?, locale: Locale, dateStyle: Int, timeStyle: Int): String {
    val formatter = DateFormat.getDateTimeInstance(dateStyle, timeStyle, locale)
    return if (date == null) "" else formatter.format(date)
}