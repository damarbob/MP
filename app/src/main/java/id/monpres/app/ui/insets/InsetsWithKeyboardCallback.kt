package id.monpres.app.ui.insets

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.*

class InsetsWithKeyboardCallback(window: Window) : OnApplyWindowInsetsListener,
    WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {

    constructor(window: Window, topPadding: Int? = null, bottomPadding: Int? = null) : this(window) {
        this.topPadding = topPadding
        this.bottomPadding = bottomPadding
    }

    private var deferredInsets = false
    private var view: View? = null
    private var lastWindowInsets: WindowInsetsCompat? = null

    private var topPadding: Int? = null
    private var bottomPadding: Int? = null

    init {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // For better support for devices API 29 and lower
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        view = v
        lastWindowInsets = insets
        val types = when {
            // When the deferred flag is enabled, we only use the systemBars() insets
            deferredInsets -> WindowInsetsCompat.Type.statusBars()
            // When the deferred flag is disabled, we use combination of the the systemBars() and ime() insets
            else -> WindowInsetsCompat.Type.statusBars() + WindowInsetsCompat.Type.ime()
        }

        val typeInsets = insets.getInsets(types)
        v.setPadding(
            typeInsets.left,
            topPadding ?: typeInsets.top,
            typeInsets.right,
            bottomPadding ?: typeInsets.bottom
        )
        return WindowInsetsCompat.CONSUMED
    }

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
            // When the IME is not visible, we defer the WindowInsetsCompat.Type.ime() insets
            deferredInsets = true
        }
    }

    override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnimations: MutableList<WindowInsetsAnimationCompat>
    ): WindowInsetsCompat {
        return insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        if (deferredInsets && (animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
            // When the IME animation has finished and the IME inset has been deferred, we reset the flag
            deferredInsets = false

            // We dispatch insets manually because if we let the normal dispatch cycle handle it, this will happen too late and cause a visual flicker
            // So we dispatch the latest WindowInsets to the view
            if (lastWindowInsets != null && view != null) {
                ViewCompat.dispatchApplyWindowInsets(view!!, lastWindowInsets!!)
            }
        }
    }

}