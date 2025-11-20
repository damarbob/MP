package id.monpres.app.enums

import android.content.Context
import androidx.annotation.StringRes
import id.monpres.app.R

enum class Language(val code: String, @field:StringRes val label: Int) {
    SYSTEM("system", R.string.system_default),
    BAHASA("id", R.string.bahasa_indonesia),
    ENGLISH("en", R.string.english);
    companion object {
        fun toListString(context: Context): List<String> {
            return entries.map { context.getString(it.label) }
        }
        fun fromCode(code: String): Language? {
            return entries.find { code.contains(it.code) }
        }
    }
}