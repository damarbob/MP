package id.monpres.app.utils

import android.util.Log
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException


/**
 * Emits values from the original (upstream) flow until the [signalFlow] emits any value or completes.
 * Once the [signalFlow] emits or completes, the collection from the original flow is cancelled,
 * and this resulting flow also completes.
 *
 * @param signalFlow The flow that signals when to stop collecting from the original flow.
 */
fun <T> Flow<T>.takeUntilSignal(signalFlow: Flow<Any>): Flow<T> = flow {
    Log.d("takeUntilSignal", "Operator attached. Waiting for upstream or signal.")
    try {
        coroutineScope { // Create a new scope to manage jobs for upstream and signal
            // Launch a job to collect from the signalFlow
            val signalJob = launch {
                try {
                    signalFlow
                        .onCompletion { cause ->
                            if (cause == null) Log.d("takeUntilSignal", "SignalFlow completed normally.")
                            else if (cause !is CancellationException) Log.w("takeUntilSignal", "SignalFlow completed with error.", cause)
                        }
                        .collect { // We just care that it emits something or completes
                            Log.d("takeUntilSignal", "SignalFlow emitted/completed. Cancelling main collection scope.")
                            this@coroutineScope.cancel(CancellationException("SignalFlow triggered cancellation"))
                        }
                } catch (e: CancellationException) {
                    // This is expected if the main flow completes first and cancels this signalJob
                    Log.d("takeUntilSignal", "SignalFlow collection was cancelled.", e)
                    // Don't propagate this cancellation if the main scope is already cancelling.
                } catch (e: Throwable) {
                    Log.e("takeUntilSignal", "Error in signalFlow collection.", e)
                    // If signal flow itself errors, we might want to cancel the main scope too
                    this@coroutineScope.cancel(CancellationException("SignalFlow errored", e))
                }
            }

            // Collect from the main data flow (upstream)
            Log.d("takeUntilSignal", "Starting collection from upstream data flow.")
            this@takeUntilSignal.collect { value ->
                emit(value)
            }

            // If the main data flow completes normally before the signal fires,
            // we should ensure the signalJob is cleaned up.
            if (signalJob.isActive) {
                Log.d("takeUntilSignal", "Upstream data flow completed. Cancelling signalJob.")
                signalJob.cancelAndJoin() // Cancel and wait for it to complete
            }
        }
    } catch (e: CancellationException) {
        Log.d("takeUntilSignal", "takeUntilSignal itself was cancelled (likely by signal or downstream).", e)
        throw e // Re-throw cancellation to propagate it correctly
    }
    Log.d("takeUntilSignal", "takeUntilSignal operator finished.")
}.onCompletion { cause ->
    when (cause) {
        null -> Log.d("takeUntilSignal", "Resulting flow completed normally.")
        is CancellationException -> Log.d("takeUntilSignal", "Resulting flow cancelled.", cause)
        else -> Log.w("takeUntilSignal", "Resulting flow completed with error.", cause)
    }
}
