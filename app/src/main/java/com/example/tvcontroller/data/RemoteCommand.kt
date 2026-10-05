package com.example.tvcontroller.data

/**
 * Commands sent to the Android TV Server via TCP/UDP sockets.
 */
sealed class RemoteCommand {
    data class Key(val code: String) : RemoteCommand()
    data class Move(val dx: Float, val dy: Float) : RemoteCommand()
    object Click : RemoteCommand()
    data class Scroll(val dy: Float) : RemoteCommand()

    fun toPayload(): String {
        return when (this) {
            is Key -> "{\"type\":\"key\",\"code\":\"$code\"}\n"
            is Move -> "{\"type\":\"move\",\"dx\":$dx,\"dy\":$dy}\n"
            is Click -> "{\"type\":\"click\"}\n"
            is Scroll -> "{\"type\":\"scroll\",\"dy\":$dy}\n"
        }
    }
}

object CommandCodes {
    const val UP = "DPAD_UP"
    const val DOWN = "DPAD_DOWN"
    const val LEFT = "DPAD_LEFT"
    const val RIGHT = "DPAD_RIGHT"
    const val OK = "DPAD_CENTER"
    const val BACK = "BACK"
    const val HOME = "HOME"
    const val VOL_UP = "VOLUME_UP"
    const val VOL_DOWN = "VOLUME_DOWN"
    const val MUTE = "VOLUME_MUTE"
    const val POWER = "POWER"
}
