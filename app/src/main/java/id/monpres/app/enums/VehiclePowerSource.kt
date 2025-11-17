package id.monpres.app.enums

import android.content.Context
import androidx.annotation.StringRes
import id.monpres.app.R

enum class VehiclePowerSource(@field:StringRes val label: Int) {
    HYBRID(R.string.hybrid), // PETROL AND ELECTRIC
    GASOLINE(R.string.gasoline),
    SOLAR(R.string.solar),
    ELECTRIC(R.string.electric);

    companion object {
        fun toListString(context: Context): List<String> {
            return entries.map { context.getString(it.label) }

        }
        fun fromLabel(context: Context, label: String): VehiclePowerSource? =
            entries.find { context.getString(it.label) == label }
    }
}