package id.monpres.app.utils

import android.util.Log

inline fun <reified T : Enum<T>> enumByNameIgnoreCaseOrNull(name: String, default: T? = null): T? {
    val enum = enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) }
    val result = enum ?: default
    Log.d("EnumUtils", "enumByNameIgnoreCaseOrNull: $name -> $enum -> $result")
    return result
}
