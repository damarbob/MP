package id.monpres.app.utils

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.text.buildSpannedString
import androidx.core.text.color
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