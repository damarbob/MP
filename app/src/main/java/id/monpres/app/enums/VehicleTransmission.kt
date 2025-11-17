package id.monpres.app.enums

import android.content.Context
import androidx.annotation.StringRes
import id.monpres.app.R

enum class VehicleTransmission(@field:StringRes val label: Int) {
    AUTOMATIC(R.string.transmission_automatic),
    MANUAL(R.string.transmission_manual);

    fun fromString(value: String): VehicleTransmission {
        return when (value.uppercase()) {
            "AUTOMATIC" -> AUTOMATIC
            "MANUAL" -> MANUAL
            else -> throw IllegalArgumentException("Invalid value: $value")
        }
    }

    companion object {
        fun toListString(context: Context): List<String> {
            return entries.map { context.getString(it.label) }
        }
        fun fromLabel(context: Context, label: String): VehicleTransmission? {
            return entries.find { context.getString(it.label) == label }
        }
    }
}