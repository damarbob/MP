package id.monpres.app.ui

import android.animation.ObjectAnimator
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.LinearProgressIndicator
import id.monpres.app.state.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

abstract class BaseFragment : Fragment() {

    // Counter for active loading operations
    private val activeLoaders = AtomicInteger(0)

    abstract val progressIndicator: LinearProgressIndicator

    /**
     * Observes a [Flow] of [UiState] and executes the provided callbacks based on the state.
     * Error handling is now done in a separate observer in the implementing fragment.
     * This function uses `repeatOnLifecycle` for lifecycle-safe collection.
     *
     * @param T The type of data wrapped in [UiState.Success].
     * @param flow The [Flow] of [UiState] to observe.
     * @param lifecycleState The [Lifecycle.State] at which flow collection should start. Defaults to [Lifecycle.State.STARTED].
     * @param onEmpty Optional callback to be executed when the state is [UiState.Empty].
     * @param onSuccess Callback to be executed when the state is [UiState.Success].
     */
    protected fun <T> observeUiState(
        flow: Flow<UiState<T>>,
        lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
        onEmpty: () -> Unit = {},
        onSuccess: (data: T) -> Unit,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Tracks if this specific flow has incremented the loader count.
            var thisFlowIsLoading: Boolean? = null

            repeatOnLifecycle(lifecycleState) {
                flow.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            if (thisFlowIsLoading == null || thisFlowIsLoading == false) { // Only increment once per loading sequence
                                activeLoaders.incrementAndGet()
                            }
                            thisFlowIsLoading = true
                            showLoading(true)
                        }

                        is UiState.Success -> {
                            if (thisFlowIsLoading == true || (thisFlowIsLoading == null && activeLoaders.get() > 0)) {
                                activeLoaders.decrementAndGet()
                            }
                            thisFlowIsLoading = false
                            showLoading(false)
                            onSuccess(state.data)
                        }

                        is UiState.Empty -> {
                            if (thisFlowIsLoading == true) {
                                activeLoaders.decrementAndGet()
                            }
                            thisFlowIsLoading = false
                            showLoading(false)
                            onEmpty()
                        }
                    }
                }
            }
        }
    }

    /**
     * Observes a one-shot [Flow] of [UiState], suitable for operations like form submission.
     * Error handling is now done in a separate observer in the implementing fragment.
     *
     * @param T The type of data wrapped in [UiState.Success].
     * @param flow The [Flow] of [UiState] to observe.
     * @param onComplete Callback to be executed when the flow emits [UiState.Success].
     */
    protected fun <T> observeUiStateOneShot(
        flow: Flow<UiState<T>>,
        onEmpty: () -> Unit = {},
        onComplete: (data: T) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            activeLoaders.incrementAndGet()
            showLoading(true)

            // This will collect states until a terminal one (Success/Empty) is reached
            flow.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        // The loading indicator is already showing, do nothing extra.
                    }

                    is UiState.Success -> {
                        activeLoaders.decrementAndGet()
                        showLoading(false)
                        // Post the action to allow the hide animation to play.
                        view?.postDelayed({ onComplete(state.data) }, 300)
                        return@collect // Stop collecting after the first terminal state.
                    }

                    is UiState.Empty -> {
                        // Treat Empty as a terminal state for a one-shot operation
                        activeLoaders.decrementAndGet()
                        showLoading(false)
                        onEmpty()
                        // We don't call onComplete here as it's typically for success with data.
                        // You could add an onEmpty lambda if needed for one-shot operations.
                        return@collect // Stop collecting.
                    }
                }
            }
        }
    }

    /**
     * Manages the visibility of the progress indicator. This is the single source of truth for showing/hiding it.
     */
    private fun showLoading(isLoading: Boolean) {
        // Only change visibility if the state is different to prevent redundant animations
        val isCurrentlyVisible = progressIndicator.isVisible

        if (isLoading && !isCurrentlyVisible) {
            progressIndicator.show()
        } else if (!isLoading && isCurrentlyVisible) {
            // Only hide if there are absolutely no more active loaders.
            if (activeLoaders.get() <= 0) {
                progressIndicator.hide()
            }
        }
    }

    private fun slideUp(view: View) {
        ObjectAnimator.ofFloat(view, "translationY", 0f, 0f - view.height.toFloat()).apply {
            duration = 300
            start()
            doOnEnd {
                view.visibility = View.GONE
            }
        }
    }

    private fun slideDown(view: View) {
        ObjectAnimator.ofFloat(view, "translationY", 0f - view.height.toFloat(), 0f).apply {
            duration = 300
            doOnStart {
                view.visibility = View.VISIBLE
            }
            start()
        }
    }
}

