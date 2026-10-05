package com.example.tvcontroller.network

import com.example.tvcontroller.data.RemoteCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Manages TCP and UDP Socket communication with the Android TV Server
 * using Kotlin Coroutines for asynchronous, non-blocking network operations.
 */
class SocketManager {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var tcpSocket: Socket? = null
    private var tcpOutputStream: OutputStream? = null
    private var udpSocket: DatagramSocket? = null
    private var serverInetAddress: InetAddress? = null

    private var targetIp: String = ""
    private var targetPort: Int = 8080
    private var connectionJob: Job? = null

    /**
     * Connect to the Android TV server asynchronously.
     */
    fun connect(ip: String, port: Int = 8080, scope: CoroutineScope) {
        if (_connectionState.value is ConnectionState.Connecting || _connectionState.value is ConnectionState.Connected) {
            disconnect()
        }

        targetIp = ip
        targetPort = port

        connectionJob = scope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting
            try {
                serverInetAddress = InetAddress.getByName(ip)

                // Initialize TCP Socket with 5 second timeout
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 5000)
                tcpSocket = socket
                tcpOutputStream = socket.getOutputStream()

                // Initialize UDP Socket for high-frequency cursor coordinates
                udpSocket = DatagramSocket()

                _connectionState.value = ConnectionState.Connected(ip, port)

                // Maintain TCP connection & monitor state
                monitorConnection()
            } catch (e: Exception) {
                closeSockets()
                _connectionState.value = ConnectionState.Error(e.localizedMessage ?: "Connection failed")
            }
        }
    }

    /**
     * Disconnects and cleans up all active sockets and jobs.
     */
    fun disconnect() {
        connectionJob?.cancel()
        closeSockets()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Sends a command payload over TCP or UDP depending on preferUdp flag.
     */
    fun sendCommand(command: RemoteCommand, preferUdp: Boolean = false, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val payload = command.toPayload()
            try {
                if (preferUdp && udpSocket != null && serverInetAddress != null) {
                    sendUdpPayload(payload)
                } else if (tcpOutputStream != null) {
                    sendTcpPayload(payload)
                }
            } catch (e: Exception) {
                // Socket error handling
                if (_connectionState.value is ConnectionState.Connected) {
                    _connectionState.value = ConnectionState.Error("Transmission error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun sendTcpPayload(payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        tcpOutputStream?.apply {
            write(bytes)
            flush()
        }
    }

    private fun sendUdpPayload(payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val packet = DatagramPacket(bytes, bytes.size, serverInetAddress, targetPort)
        udpSocket?.send(packet)
    }

    private fun monitorConnection() {
        try {
            val input = tcpSocket?.getInputStream()
            val buffer = ByteArray(1024)
            while (tcpSocket != null && tcpSocket!!.isConnected && !tcpSocket!!.isClosed) {
                val bytesRead = input?.read(buffer) ?: -1
                if (bytesRead == -1) {
                    // Server closed connection
                    break
                }
            }
        } catch (e: Exception) {
            // Connection interrupted or lost
        } finally {
            closeSockets()
            if (_connectionState.value !is ConnectionState.Disconnected) {
                _connectionState.value = ConnectionState.Error("Connection lost")
            }
        }
    }

    private fun closeSockets() {
        try {
            tcpOutputStream?.close()
            tcpOutputStream = null
            tcpSocket?.close()
            tcpSocket = null
            udpSocket?.close()
            udpSocket = null
        } catch (e: Exception) {
            // Ignore socket closure exceptions
        }
    }
}
