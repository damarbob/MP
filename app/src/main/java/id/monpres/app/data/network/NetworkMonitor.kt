package id.monpres.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import id.monpres.app.state.ConnectionState
import id.monpres.app.state.ConnectionType
import id.monpres.app.state.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Register network callback
    private var networkRequest: NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private val _networkStatus = MutableStateFlow(getCurrentNetworkStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            updateNetworkStatus()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            _networkStatus.value = NetworkStatus(ConnectionState.Disconnected)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            updateNetworkStatus(networkCapabilities)
        }
    }

    init {
        registerNetworkCallback()
    }

    private fun getCurrentNetworkStatus(): NetworkStatus {
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return if (capabilities != null) {
            val type = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WiFi
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.Cellular
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.Ethernet
                else -> ConnectionType.Other
            }
            NetworkStatus(ConnectionState.Connected, type)
        } else {
            NetworkStatus(ConnectionState.Disconnected)
        }
    }

    private fun updateNetworkStatus(capabilities: NetworkCapabilities? = null) {
        val currentCapabilities = capabilities ?: connectivityManager.getNetworkCapabilities(
            connectivityManager.activeNetwork
        )
        Log.d("NetworkMonitor", "getCurrentNetworkStatus: $currentCapabilities")

        if (currentCapabilities != null) {
            val type = when {
                currentCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WiFi
                currentCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.Cellular
                currentCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.Ethernet
                else -> ConnectionType.Other
            }
            _networkStatus.value = NetworkStatus(ConnectionState.Connected, type)
        } else {
            _networkStatus.value = NetworkStatus(ConnectionState.Disconnected)
        }
    }

    fun isConnected(): Boolean {
        return networkStatus.value.state is ConnectionState.Connected
    }

    fun registerNetworkCallback() {
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    fun cleanup() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
