package id.monpres.app.state

sealed class ConnectionState {
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
}

sealed class ConnectionType {
    object WiFi : ConnectionType()
    object Cellular : ConnectionType()
    object Ethernet : ConnectionType()
    object Other : ConnectionType()
    object Unknown : ConnectionType()
}

data class NetworkStatus(
    val state: ConnectionState,
    val type: ConnectionType = ConnectionType.Unknown
)
