package com.example.gcadapter

/**
 * Kotlin side of vpad.c: one uinput virtual gamepad per connected GameCube port.
 * Call processPacket/releaseAll from a single thread (the adapter reader).
 */
object VirtualPads {
    init {
        System.loadLibrary("gcadapter")
    }

    /** Feeds one 37-byte packet. Returns a bitmask of ports with a live pad (bit 0 = port 1). */
    external fun processPacket(packet: ByteArray): Int

    /** Removes every virtual pad (adapter closed or app stopping). */
    external fun releaseAll()

    /** false = Nintendo positions (default), true = map by label like an Xbox pad. */
    external fun setXboxLayout(xbox: Boolean)
}
