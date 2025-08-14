package id.monpres.app.ui

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import id.monpres.app.R
import id.monpres.app.utils.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

open class BaseFragment : Fragment() {

    // Counter for active loading operations
    private val activeLoaders = AtomicInteger(0)

    /**
     * Shows or hides the loading indicator based on the activeLoaders count.
     * This should be the ONLY place that directly calls your actual showLoading(Boolean) implementation.
     */
    private fun updateGlobalLoadingState() {
        if (activeLoaders.get() > 0) {
            // If you have a specific showLoading function in BaseFragment that subclasses override:
            showLoading(true)
        } else {
            showLoading(false)
        }
    }

    /**
     * Observes a [Flow] of [UiState] and executes the provided callbacks based on the state.
     * This function uses `repeatOnLifecycle` to ensure that the flow collection is active
     * only when the Fragment's view lifecycle is in the specified [lifecycleState].
     *
     * @param T The type of data wrapped in [UiState.Success].
     * @param flow The [Flow] of [UiState] to observe.
     * @param lifecycleState The [Lifecycle.State] at which the flow collection should start and stop.
     *                       Defaults to [Lifecycle.State.STARTED].
     * @param onError Optional callback to be executed when the [UiState] is [UiState.Error].
     *                It receives the error message. Defaults to showing a [Toast] with the error message.
     * @param onSuccess Callback to be executed when the [UiState] is [UiState.Success].
     *                  It receives the data of type [T].
     */
    fun <T> observeUiState(
        flow: Flow<UiState<T>>,
        lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
        onError: (message: String) -> Unit = { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        },
        onSuccess: (data: T) -> Unit,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Increment loader count when this observation starts
            // and it's expected to potentially go into a loading state.
            // This is tricky if the flow doesn't immediately emit Loading.
            // A better place to increment is right before the flow collection starts
            // if the flow is guaranteed to start with Loading or if you always want
            // to assume a potential load.

            // Alternative: Increment when UiState.Loading is first received.
            // Decrement when UiState.Success or UiState.Error is received for THIS flow.

            var thisFlowIsLoading =
                false // Track if this specific flow instance caused a loading increment

            repeatOnLifecycle(lifecycleState) {
                flow.collect { state ->
                    // --- Handle Finish for the PREVIOUS state of THIS flow ---
                    if (thisFlowIsLoading) {
                        if (state !is UiState.Loading) {
                            activeLoaders.decrementAndGet()
                            thisFlowIsLoading = false
                        }
                    }
                    // --- Current State Processing ---
                    when (state) {
                        is UiState.Loading -> {
                            if (!thisFlowIsLoading) { // Only increment if it wasn't already loading
                                activeLoaders.incrementAndGet()
                                thisFlowIsLoading = true
                            }
                        }

                        is UiState.Success -> {
                            // Decrement was handled above if it was previously loading
                            onSuccess(state.data)
                        }

                        is UiState.Error -> {
                            // Decrement was handled above if it was previously loading
                            val message = when (state.exception) {
                                is NullPointerException -> getString(
                                    R.string.x_not_found, "Data"
                                )
                                // Add NoSuchElementException for "not found"
                                is NoSuchElementException -> state.exception.localizedMessage
                                    ?: getString(R.string.x_not_found, "Item")

                                else -> state.exception?.localizedMessage
                                    ?: getString(R.string.unknown_error)
                            }
                            onError(message)
                        }
                    }
                    // Update the global loading UI based on the overall count
                    updateGlobalLoadingState()
                }
            }
        }
    }

    /**
     * Observes a [Flow] of [UiState] and executes the provided callbacks based on the state.
     * This function collects the flow only once and does not repeat on lifecycle changes,
     * making it suitable for one-shot operations.
     *
     * @param T The type of data wrapped in [UiState.Success].
     * @param flow The [Flow] of [UiState] to observe.
     * @param onError Optional callback to be executed when the [UiState] is [UiState.Error].
     *                It receives the error message. Defaults to showing a [Toast] with the error message.
     * @param onSuccess Callback to be executed when the [UiState] is [UiState.Success].
     *                  It receives the data of type [T].
     */
    fun <T> observeUiStateOneShot(
        flow: Flow<UiState<T>>,
        onError: (message: String) -> Unit = { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        },
        onSuccess: (data: T) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Increment loader count when this observation starts
            // and it's expected to potentially go into a loading state.
            // This is tricky if the flow doesn't immediately emit Loading.
            // A better place to increment is right before the flow collection starts
            // if the flow is guaranteed to start with Loading or if you always want
            // to assume a potential load.

            // Alternative: Increment when UiState.Loading is first received.
            // Decrement when UiState.Success or UiState.Error is received for THIS flow.

            var thisFlowIsLoading =
                false // Track if this specific flow instance caused a loading increment

            flow.collect { state ->
                // --- Handle Finish for the PREVIOUS state of THIS flow ---
                if (thisFlowIsLoading) {
                    if (state !is UiState.Loading) {
                        activeLoaders.decrementAndGet()
                        thisFlowIsLoading = false
                    }
                }
                // --- Current State Processing ---
                when (state) {
                    is UiState.Loading -> {
                        if (!thisFlowIsLoading) { // Only increment if it wasn't already loading
                            activeLoaders.incrementAndGet()
                            thisFlowIsLoading = true
                        }
                    }

                    is UiState.Success -> {
                        // Decrement was handled above if it was previously loading
                        onSuccess(state.data)
                    }

                    is UiState.Error -> {
                        // Decrement was handled above if it was previously loading
                        val message = when (state.exception) {
                            is NullPointerException -> getString(
                                R.string.x_not_found, "Data"
                            )
                            // Add NoSuchElementException for "not found"
                            is NoSuchElementException -> state.exception.localizedMessage
                                ?: getString(R.string.x_not_found, "Item")

                            else -> state.exception?.localizedMessage
                                ?: getString(R.string.unknown_error)
                        }
                        onError(message)
                    }
                }
                // Update the global loading UI based on the overall count
                updateGlobalLoadingState()
            }

        }
    }

    open fun showLoading(isLoading: Boolean) {

    }
}

