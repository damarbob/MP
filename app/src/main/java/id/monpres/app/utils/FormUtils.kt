package id.monpres.app.utils

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.isNotEmpty
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputLayout

fun TextInputLayout.markRequiredInRed() {
    hint = buildSpannedString {
        append(hint)
        color(Color.RED) { append(" *") } // Mind the space prefix.
    }
}

fun Button.markRequiredInRed() {
    text = buildSpannedString {
        append(text)
        color(Color.RED) { append(" *") } // Mind the space prefix.
    }
}

fun CheckBox.markRequiredInRed() {
    text = buildSpannedString {
        append(text)
        color(Color.RED) { append(" *") } // Mind the space prefix.
    }
}

fun View.hideKeyboard(context: Context) {
    val imm = getSystemService(context, InputMethodManager::class.java)
    imm?.hideSoftInputFromWindow(this.windowToken, 0)
}

/**
 * A generic, reusable function to populate a RadioGroup from any enum list.
 */
fun <T : Enum<T>> setupRadioGroupSetting(
    context: Context,
    radioGroup: RadioGroup,
    entries: List<T>,
    getLabel: (T) -> String,
    onSelectionChanged: (T) -> Unit
) {
    // Prevent re-adding views on configuration change
    if (radioGroup.isNotEmpty()) return

    entries.forEachIndexed { index, item ->
        val radio = MaterialRadioButton(context).apply {
            id = View.generateViewId() // Generate a unique ID for each radio button
            text = getLabel(item)
            tag = item // Store the enum object itself in the tag for easy retrieval
        }
        radioGroup.addView(radio)
    }

    radioGroup.setOnCheckedChangeListener { group, checkedId ->
        val selectedRadioButton = group.findViewById<MaterialRadioButton>(checkedId)
        val selectedItem = selectedRadioButton?.tag as? T
        selectedItem?.let {
            onSelectionChanged(it)
        }
    }
}