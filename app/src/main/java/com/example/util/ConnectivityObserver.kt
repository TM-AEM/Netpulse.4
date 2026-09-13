package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.model.NetworkType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class ConnectionStatus(
    val isConnected: Boolean,
    val networkType: NetworkType?,
    val isMetered: Boolean,
    val labelAr: String,
    val labelEn: String
) {
    companion object {
        val Disconnected = ConnectionStatus(
            isConnected = false,
            networkType = null,
            isMetered = false,
            labelAr = "غير متصل",
            labelEn = "Disconnected"
        )
    }
}

open class ConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    open fun observe(): Flow<ConnectionStatus> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(ConnectionStatus.Disconnected)
            close()
            return@callbackFlow
        }

        fun currentStatus(): ConnectionStatus {
            val activeNetwork = cm.activeNetwork ?: return ConnectionStatus.Disconnected
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return ConnectionStatus.Disconnected
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (!hasInternet) return ConnectionStatus.Disconnected

            val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionStatus(
                    isConnected = true,
                    networkType = NetworkType.WIFI,
                    isMetered = isMetered,
                    labelAr = "واي فاي",
                    labelEn = "Wi-Fi"
                )
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionStatus(
                    isConnected = true,
                    networkType = NetworkType.MOBILE,
                    isMetered = isMetered,
                    labelAr = "بيانات الجوال",
                    labelEn = "Mobile Data"
                )
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionStatus(
                    isConnected = true,
                    networkType = NetworkType.WIFI,
                    isMetered = isMetered,
                    labelAr = "إيثرنت",
                    labelEn = "Ethernet"
                )
                else -> ConnectionStatus(
                    isConnected = true,
                    networkType = NetworkType.TOTAL,
                    isMetered = isMetered,
                    labelAr = "متصل",
                    labelEn = "Connected"
                )
            }
        }

        trySend(currentStatus())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentStatus())
            }

            override fun onLost(network: Network) {
                trySend(currentStatus())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(currentStatus())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            AppLogger.w("ConnectivityObserver", "Failed to register network callback", e)
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Ignore unregister exceptions
            }
        }
    }.distinctUntilChanged()
}
