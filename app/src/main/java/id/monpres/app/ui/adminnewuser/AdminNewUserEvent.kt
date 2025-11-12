package id.monpres.app.ui.adminnewuser

// Defines one-time events the VM sends to the Fragment
sealed class AdminNewUserEvent {
    /**
     * Signals a successful action (Accept, Reject, Delete).
     * The Fragment should show the message and then dismiss itself.
     */
    data class ActionSuccess(val message: String) : AdminNewUserEvent()

    /**
     * Signals a failure. The Fragment should show the toast but remain open.
     */
    data class ShowToast(val message: String) : AdminNewUserEvent()
}