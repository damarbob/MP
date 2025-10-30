package id.monpres.app.state

sealed class UserEligibilityState {
    object Eligible : UserEligibilityState()
    object PartnerMissingLocation : UserEligibilityState()
    object CustomerMissingPhoneNumber : UserEligibilityState()
}