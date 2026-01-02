package id.monpres.app.state

sealed class NavigationGraphState {
    object Loading : NavigationGraphState()
    data class Partner(val graph: Int) : NavigationGraphState()
    data class Customer(val graph: Int) : NavigationGraphState()
    data class Admin(val graph: Int) : NavigationGraphState()
}
