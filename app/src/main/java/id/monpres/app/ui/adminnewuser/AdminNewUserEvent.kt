package id.monpres.app.ui.adminnewuser

// Defines one-time events the VM sends to the Fragment
sealed class AdminNewUserEvent {
    object ActionVerified : AdminNewUserEvent()
    object ActionRejected : AdminNewUserEvent()
    object ActionOther : AdminNewUserEvent() // No specific action, but the user is updated
    object ActionFailed : AdminNewUserEvent() // User update failed

    data class Error(val message: String?) : AdminNewUserEvent()

}
