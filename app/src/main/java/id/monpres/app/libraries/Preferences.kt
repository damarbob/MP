package id.monpres.app.libraries

import android.app.Activity
import android.content.Context
import androidx.core.content.edit

class Preferences {
    companion object {

        fun readString(activity: Activity, key: String?): String? {
            return activity.getPreferences(Context.MODE_PRIVATE).getString(key, null)
        }

        fun readString(activity: Activity, key: String?, defaultValue: String?): String? {
            return activity.getPreferences(Context.MODE_PRIVATE).getString(key, defaultValue)
        }

        fun saveString(activity: Activity, key: String?, value: String?) {
            val sharedPref = activity.getPreferences(Context.MODE_PRIVATE)
            sharedPref.edit {
                putString(key, value)
            }
        }

        fun readBoolean(activity: Activity, key: String, defaultValue: Boolean?): Boolean {
            return activity.getPreferences(Context.MODE_PRIVATE).getBoolean(key,
                defaultValue ?: false
            )
        }

        fun saveBoolean(activity: Activity, key: String?, value: Boolean) {
            val sharedPref = activity.getPreferences(Context.MODE_PRIVATE)
            sharedPref.edit {
                putBoolean(key, value)
            }
        }

        fun readInt(activity: Activity, key: String, defaultValue: Int?): Int {
            return activity.getPreferences(Context.MODE_PRIVATE).getInt(key,
                defaultValue ?: 0
            )
        }

        fun saveInt(activity: Activity, key: String?, value: Int) {
            val sharedPref = activity.getPreferences(Context.MODE_PRIVATE)
            sharedPref.edit {
                putInt(key, value)
            }
        }

        fun readSharedInt(context: Context, sharedPreference: String, key: String, defaultValue: Int?): Int {
            return context.getSharedPreferences(sharedPreference, Context.MODE_PRIVATE).getInt(key, defaultValue ?: 0)
        }

        fun saveSharedInt(context: Context, sharedPreference: String, key: String, value: Int) {
            context.getSharedPreferences(sharedPreference, Context.MODE_PRIVATE).edit().apply{
                putInt(key, value)
                apply()
            }
        }

        fun readSharedLong(context: Context, sharedPreference: String, key: String, defaultValue: Long?): Long {
            return context.getSharedPreferences(sharedPreference, Context.MODE_PRIVATE).getLong(key, defaultValue ?: 0L)
        }

        fun saveSharedLong(context: Context, sharedPreference: String, key: String, value: Long) {
            context.getSharedPreferences(sharedPreference, Context.MODE_PRIVATE).edit().apply{
                putLong(key, value)
                apply()
            }
        }

    }
}