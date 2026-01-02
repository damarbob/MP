package id.monpres.app.ui.settings

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.androidbroadcast.vbpd.viewBinding
import id.monpres.app.R
import id.monpres.app.databinding.FragmentMonpresSettingBinding
import id.monpres.app.enums.Language
import id.monpres.app.enums.ThemeMode
import id.monpres.app.utils.enumByNameIgnoreCaseOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MonpresSettingFragment : Fragment(R.layout.fragment_monpres_setting) {

    companion object {
        fun newInstance() = MonpresSettingFragment()
    }

    private val viewModel: MonpresSettingViewModel by viewModels()
    private val binding by viewBinding(FragmentMonpresSettingBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward= */ false)

        setupDynamicColorSetting()
        createThemeRadioOptions()
        createLanguageRadioOptions()
        observeSettings()
    }

    private fun setupDynamicColorSetting() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            binding.monpresSettingCardViewDynamicColor.visibility = View.GONE
        }

        binding.monpresSettingSwitchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            Log.d("MonpresSettingFragment", "Dynamic color checked: $isChecked")
            viewModel.setDynamicColorEnabled(isChecked)
        }
    }

    private fun createThemeRadioOptions() {
        ThemeMode.entries.forEachIndexed { index, option ->
            val radioButton = createRadioButton(option.label)
            radioButton.id = View.generateViewId()

            radioButton.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    viewModel.setThemeMode(option)
                }
            }

            binding.monpresSettingRadioGroupThemeMode.addView(radioButton)
        }
    }

    private fun createLanguageRadioOptions() {
        Language.entries.forEachIndexed { index, option ->
            val radioButton = createRadioButton(option.label)
            radioButton.id = View.generateViewId()

            radioButton.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    viewModel.setAppLanguage(option)
                }
            }

            binding.monpresSettingRadioGroupLanguage.addView(radioButton)
        }
    }

    private fun createRadioButton(titleRes: Int): MaterialRadioButton {
        return MaterialRadioButton(requireContext()).apply {
            // Set layout parameters
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.MarginLayoutParams.MATCH_PARENT,
                ViewGroup.MarginLayoutParams.WRAP_CONTENT
            )

            // Create the text with title and description
            text = getString(titleRes)
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.dynamicColorEnabled.collectLatest { enabled ->
                Log.d("MonpresSettingFragment", "Observed dynamic color: $enabled")
                binding.monpresSettingSwitchDynamicColor.isChecked = enabled
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.themeMode.collectLatest { themeMode ->
                Log.d("MonpresSettingFragment", "Observed theme mode: $themeMode")
                updateThemeModeSelection(
                    enumByNameIgnoreCaseOrNull<ThemeMode>(
                        themeMode,
                        ThemeMode.SYSTEM
                    )!!
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.appLanguage.collectLatest { language ->
                updateLanguageSelection(
                    enumByNameIgnoreCaseOrNull<Language>(
                        language,
                        Language.SYSTEM
                    )!!
                )
            }
        }
    }

    private fun updateThemeModeSelection(themeMode: ThemeMode) {
        val index = ThemeMode.entries.indexOfFirst { it == themeMode }
        if (index != -1 && index < binding.monpresSettingRadioGroupThemeMode.childCount) {
            binding.monpresSettingRadioGroupThemeMode.check(
                binding.monpresSettingRadioGroupThemeMode.getChildAt(
                    index
                ).id
            )
        }
    }

    private fun updateLanguageSelection(language: Language) {
        val index = Language.entries.indexOfFirst { it == language }
        if (index != -1 && index < binding.monpresSettingRadioGroupLanguage.childCount) {
            binding.monpresSettingRadioGroupLanguage.check(
                binding.monpresSettingRadioGroupLanguage.getChildAt(
                    index
                ).id
            )
        }
    }
}
