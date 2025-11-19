package id.monpres.app.repository

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import id.monpres.app.enums.Language
import id.monpres.app.enums.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Create a DataStore instance at the top level of the file
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "monpres_settings")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    // Define keys for each preference
    private object PreferenceKeys {
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val PAYMENT_METHOD_ID = stringPreferencesKey("payment_method_id")
    }

    companion object {
        fun decideThemeMode(theme: ThemeMode): Int {
            return when (theme) {
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }

        fun decideLanguage(language: Language?): LocaleListCompat {
            return when (language) {
                Language.SYSTEM, null -> {
                    LocaleListCompat.getEmptyLocaleList()
                }
//                null -> {
//                    AppCompatDelegate.getApplicationLocales()
//                }
                else -> {
                    LocaleListCompat.forLanguageTags(language.code)
                }
            }
        }
    }

    // --- Dynamic Color ---
    val isDynamicColorEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.DYNAMIC_COLOR] ?: true // Default to true
        }

    suspend fun setDynamicColorEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DYNAMIC_COLOR] = isEnabled
        }
    }

    // --- Theme Mode ---
    val theme: Flow<String> = context.dataStore.data
        .map { preferences ->
            val theme = preferences[PreferenceKeys.THEME_MODE]
            Log.d("AppPreferences", "Theme: $theme")
            theme ?: ThemeMode.SYSTEM.name // Default to system
        }

    suspend fun setTheme(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.THEME_MODE] = themeMode.name
        }
    }

    // --- Language ---
    val language: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.APP_LANGUAGE] ?: Language.SYSTEM.name
        }

    suspend fun setLanguage(language: Language) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.APP_LANGUAGE] = language.name
        }
    }

    // --- Payment Method ---
    val paymentMethodId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.PAYMENT_METHOD_ID] ?: "cash" // Default to cash
        }

    suspend fun setPaymentMethodId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.PAYMENT_METHOD_ID] = id
        }
    }
}
