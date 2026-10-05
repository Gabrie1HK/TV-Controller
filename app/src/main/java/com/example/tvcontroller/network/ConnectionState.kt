package com.example.tvcontroller.network

/**
 * Connection states for TCP/UDP network sockets.
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val ip: String, val port: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
