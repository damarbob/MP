package id.monpres.app.enums

import android.content.Context
import androidx.annotation.StringRes
import id.monpres.app.R

enum class UserRole(@field:StringRes val label: Int) {
    CUSTOMER(R.string.customer),
    PARTNER(R.string.partner),
    ADMIN(R.string.admin),
    ;

    // Instance method to convert THIS specific role to a UI string
    fun asString(context: Context): String {
        return context.getString(this.label)
    }

    companion object {
        fun toListString(context: Context): List<String> =
            entries.map { context.getString(it.label) }

        // Tolerant to formatting (case-insensitive)
        fun fromLabel(context: Context, label: String): UserRole? =
            entries.find { context.getString(it.label).equals(label, ignoreCase = true) }

        // Tolerant to manual DB edits (case-insensitive)
        fun fromName(name: String?): UserRole? =
            entries.find { it.name.equals(name, ignoreCase = true) }
    }
}