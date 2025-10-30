package id.monpres.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor() {
    private val _externalSignOutSignal = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val externalSignOutSignal: SharedFlow<Unit> = _externalSignOutSignal.asSharedFlow()

    fun triggerSignOut() {
        _externalSignOutSignal.tryEmit(Unit)
    }
}