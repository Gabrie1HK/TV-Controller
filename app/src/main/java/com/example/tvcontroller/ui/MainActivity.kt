package com.example.tvcontroller.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.tvcontroller.R
import com.example.tvcontroller.data.CommandCodes
import com.example.tvcontroller.data.RemoteCommand
import com.example.tvcontroller.databinding.ActivityMainBinding
import com.example.tvcontroller.network.ConnectionState
import com.example.tvcontroller.network.SocketManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val socketManager = SocketManager()
    private lateinit var prefs: SharedPreferences

    private var lastX = 0f
    private var lastY = 0f
    private var touchDownTime = 0L
    private var totalMovedDistance = 0f

    companion object {
        private const val PREFS_NAME = "tv_controller_prefs"
        private const val KEY_IP = "last_ip"
        private const val KEY_PORT = "last_port"
        private const val DEFAULT_PORT = 8080
        private const val TAP_TIMEOUT_MS = 200L
        private const val TAP_DISTANCE_THRESHOLD = 20f
        private const val SENSITIVITY_MULTIPLIER = 1.2f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        restoreSavedConnectionInfo()
        setupListeners()
        observeConnectionState()
    }

    private fun restoreSavedConnectionInfo() {
        val savedIp = prefs.getString(KEY_IP, "")
        val savedPort = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        if (!savedIp.isNullOrEmpty()) {
            binding.etIpAddress.setText(savedIp)
        }
        binding.etPort.setText(savedPort.toString())
    }

    private fun saveConnectionInfo(ip: String, port: Int) {
        prefs.edit()
            .putString(KEY_IP, ip)
            .putInt(KEY_PORT, port)
            .apply()
    }

    private fun setupListeners() {
        // Connect / Disconnect button
        binding.btnConnect.setOnClickListener {
            val currentState = socketManager.connectionState.value
            if (currentState is ConnectionState.Connected || currentState is ConnectionState.Connecting) {
                socketManager.disconnect()
            } else {
                val ip = binding.etIpAddress.text.toString().trim()
                val portStr = binding.etPort.text.toString().trim()

                if (ip.isEmpty()) {
                    Toast.makeText(this, getString(R.string.msg_ip_required), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val port = portStr.toIntOrNull() ?: DEFAULT_PORT
                saveConnectionInfo(ip, port)
                socketManager.connect(ip, port, lifecycleScope)
            }
        }

        // D-Pad Directional Controls
        binding.btnUp.setOnClickListener { sendKey(CommandCodes.UP) }
        binding.btnDown.setOnClickListener { sendKey(CommandCodes.DOWN) }
        binding.btnLeft.setOnClickListener { sendKey(CommandCodes.LEFT) }
        binding.btnRight.setOnClickListener { sendKey(CommandCodes.RIGHT) }
        binding.btnOk.setOnClickListener { sendKey(CommandCodes.OK) }

        // System Control Buttons
        binding.btnBack.setOnClickListener { sendKey(CommandCodes.BACK) }
        binding.btnHome.setOnClickListener { sendKey(CommandCodes.HOME) }
        binding.btnPower.setOnClickListener { sendKey(CommandCodes.POWER) }
        binding.btnVolUp.setOnClickListener { sendKey(CommandCodes.VOL_UP) }
        binding.btnVolDown.setOnClickListener { sendKey(CommandCodes.VOL_DOWN) }
        binding.btnMute.setOnClickListener { sendKey(CommandCodes.MUTE) }

        // Trackpad Motion & Tap Listener
        setupTrackpad()
    }

    private fun setupTrackpad() {
        binding.trackpadArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    touchDownTime = System.currentTimeMillis()
                    totalMovedDistance = 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - lastX) * SENSITIVITY_MULTIPLIER
                    val dy = (event.y - lastY) * SENSITIVITY_MULTIPLIER

                    totalMovedDistance += abs(dx) + abs(dy)

                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                        // Send cursor movement payload via UDP for minimal latency
                        socketManager.sendCommand(RemoteCommand.Move(dx, dy), preferUdp = true, scope = lifecycleScope)
                        lastX = event.x
                        lastY = event.y
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchDownTime
                    if (duration < TAP_TIMEOUT_MS && totalMovedDistance < TAP_DISTANCE_THRESHOLD) {
                        // Single tap detected -> Click
                        socketManager.sendCommand(RemoteCommand.Click, preferUdp = false, scope = lifecycleScope)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun sendKey(code: String) {
        socketManager.sendCommand(RemoteCommand.Key(code), preferUdp = false, scope = lifecycleScope)
    }

    private fun observeConnectionState() {
        lifecycleScope.launch {
            socketManager.connectionState.collectLatest { state ->
                when (state) {
                    is ConnectionState.Disconnected -> {
                        binding.tvStatus.text = getString(R.string.status_disconnected)
                        binding.btnConnect.text = getString(R.string.btn_connect)
                        setStatusDotColor(R.color.status_disconnected)
                        setInputsEnabled(true)
                    }
                    is ConnectionState.Connecting -> {
                        binding.tvStatus.text = getString(R.string.status_connecting)
                        binding.btnConnect.text = getString(R.string.btn_connect)
                        setStatusDotColor(R.color.status_connecting)
                        setInputsEnabled(false)
                    }
                    is ConnectionState.Connected -> {
                        binding.tvStatus.text = getString(R.string.status_connected, state.ip, state.port)
                        binding.btnConnect.text = getString(R.string.btn_disconnect)
                        setStatusDotColor(R.color.status_connected)
                        setInputsEnabled(false)
                        Toast.makeText(this@MainActivity, getString(R.string.msg_connected), Toast.LENGTH_SHORT).show()
                    }
                    is ConnectionState.Error -> {
                        binding.tvStatus.text = getString(R.string.status_error, state.message)
                        binding.btnConnect.text = getString(R.string.btn_connect)
                        setStatusDotColor(R.color.status_disconnected)
                        setInputsEnabled(true)
                    }
                }
            }
        }
    }

    private fun setStatusDotColor(colorRes: Int) {
        val drawable = binding.statusIndicator.background
        if (drawable is GradientDrawable) {
            drawable.setColor(ContextCompat.getColor(this, colorRes))
        } else {
            binding.statusIndicator.setBackgroundColor(ContextCompat.getColor(this, colorRes))
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        binding.etIpAddress.isEnabled = enabled
        binding.etPort.isEnabled = enabled
    }

    override fun onDestroy() {
        super.onDestroy()
        socketManager.disconnect()
    }
}
