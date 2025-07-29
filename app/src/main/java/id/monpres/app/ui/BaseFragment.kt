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

open class BaseFragment : Fragment() {

    /**
     * Observes a [Flow] of [UiState] and executes the provided callbacks based on the state.
     * This function uses `repeatOnLifecycle` to ensure that the flow collection is active
     * only when the Fragment's view lifecycle is in the specified [lifecycleState].
     *
     * @param T The type of data wrapped in [UiState.Success].
     * @param flow The [Flow] of [UiState] to observe.
     * @param lifecycleState The [Lifecycle.State] at which the flow collection should start and stop.
     *                       Defaults to [Lifecycle.State.STARTED].
     * @param onLoading Optional callback to be executed when the [UiState] is [UiState.Loading].
     *                  Defaults to calling [showLoading] with `true`.
     * @param onError Optional callback to be executed when the [UiState] is [UiState.Error].
     *                It receives the error message. Defaults to showing a [Toast] with the error message.
     * @param onFinished Optional callback to be executed after either [onSuccess] or [onError] is called,
     *                   or when a new state is emitted (before processing the new state).
     *                   This is useful for hiding loading indicators or performing cleanup.
     *                   Defaults to calling [showLoading] with `false`.
     * @param onSuccess Callback to be executed when the [UiState] is [UiState.Success].
     *                  It receives the data of type [T].
     */
    fun <T> observeUiState(
        flow: Flow<UiState<T>>,
        lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
        onLoading: () -> Unit = { showLoading(true) }, // Default actions
        onError: (message: String) -> Unit = { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        },
        onFinished: () -> Unit = { showLoading(false) }, // Called after success or error
        onSuccess: (data: T) -> Unit,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(lifecycleState) { // Or pass lifecycle state as param
                flow.collect { state ->
                    onFinished() // Hide loading from previous state first
                    when (state) {
                        is UiState.Loading -> onLoading()
                        is UiState.Success -> onSuccess(state.data)
                        is UiState.Error -> {
                            val message = when (state.exception) {
                                is NullPointerException -> getString(
                                    R.string.x_not_found,  "Data"
                                )
                                else -> state.exception?.localizedMessage ?: getString(R.string.unknown_error)
                            }
                            onError(message)
                        }
                    }
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
     * @param onLoading Optional callback to be executed when the [UiState] is [UiState.Loading].
     *                  Defaults to an empty lambda.
     * @param onError Optional callback to be executed when the [UiState] is [UiState.Error].
     *                It receives the error message. Defaults to showing a [Toast] with the error message.
     * @param onFinished Optional callback to be executed after either [onSuccess] or [onError] is called.
     *                   This is useful for hiding loading indicators or performing cleanup.
     *                   Defaults to an empty lambda.
     * @param onSuccess Callback to be executed when the [UiState] is [UiState.Success].
     *                  It receives the data of type [T].
     */
    fun <T> observeUiStateOneShot(
        flow: Flow<UiState<T>>,
        onLoading: () -> Unit = { showLoading(true) }, // Default actions
        onError: (message: String) -> Unit = { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        },
        onFinished: () -> Unit = { showLoading(false) }, // Called after success or error
        onSuccess: (data: T) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            flow.collect { state ->
                onFinished() // Hide loading from previous state first
                when (state) {
                    is UiState.Loading -> onLoading()
                    is UiState.Success -> onSuccess(state.data)
                    is UiState.Error -> {
                        val message = when (state.exception) {
                            is NullPointerException -> getString(
                                R.string.x_not_found, "Data"
                            )
                            else -> state.exception?.localizedMessage ?: getString(R.string.unknown_error)
                        }
                        onError(message)
                    }
                }
            }
        }
    }

    open fun showLoading(isLoading: Boolean) {

    }
}

