package id.monpres.app.enums

import android.content.Context
import androidx.annotation.StringRes
import id.monpres.app.R

enum class PartnerCategory(@field:StringRes val label: Int) {
    MACHINERY(R.string.machinery),
    TIRE_REPAIRS(R.string.tire_repairs),
    BATTERY(R.string.battery),
    WELDING(R.string.welding);

    companion object {
        fun toListString(context: Context): List<String> = entries.map { context.getString(it.label) }
        fun fromLabel(context: Context, label: String): PartnerCategory? =
            entries.find { context.getString(it.label) == label }
        fun fromName(name: String): PartnerCategory? = entries.find { it.name == name }
    }
}