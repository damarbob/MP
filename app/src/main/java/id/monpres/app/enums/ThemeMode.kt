package id.monpres.app.enums

import android.content.Context
import androidx.annotation.StringRes
import id.monpres.app.R

enum class ThemeMode(val id: Int, @field:StringRes val label: Int) {
    SYSTEM(0, R.string.system_default),
    LIGHT(1, R.string.light_mode),
    DARK(2, R.string.dark_mode);

    companion object {
        fun toListString(context: Context): List<String> {
            return VehicleTransmission.entries.map { context.getString(it.label) }
        }
    }
}