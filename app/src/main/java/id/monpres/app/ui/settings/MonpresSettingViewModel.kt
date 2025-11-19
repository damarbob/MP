package id.monpres.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.monpres.app.enums.Language
import id.monpres.app.enums.ThemeMode
import id.monpres.app.repository.AppPreferences
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonpresSettingViewModel @Inject constructor(private val appPreferences: AppPreferences) : ViewModel() {
    val dynamicColorEnabled = appPreferences.isDynamicColorEnabled
//        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val themeMode = appPreferences.theme
//        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM.name)

    val appLanguage = appPreferences.language
//        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM.name)

    fun setDynamicColorEnabled(enabled: Boolean) {
        Log.d("MonpresSettingViewModel", "setDynamicColorEnabled: $enabled")
        viewModelScope.launch {
            appPreferences.setDynamicColorEnabled(enabled)
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        Log.d("MonpresSettingViewModel", "setThemeMode: $themeMode")
        viewModelScope.launch {
            appPreferences.setTheme(themeMode)
        }
    }

    fun setAppLanguage(language: Language) {
        Log.d("MonpresSettingViewModel", "setAppLanguage: $language")
        viewModelScope.launch {
            appPreferences.setLanguage(language)
        }
    }
}