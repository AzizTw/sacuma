package io.github.aziztw.sacuma

/**
 * Kotlin side of vpad.c: one uinput virtual gamepad per connected GameCube port.
 * Call processPacket/releaseAll from a single thread (the adapter reader);
 * the setters can be called from any thread.
 */
object VirtualPads {
    /** vpad.c names each pad "GameCube Controller P<n>". */
    const val DEVICE_NAME_PREFIX = "GameCube Controller"

    init {
        System.loadLibrary("sacuma")
    }

    /** Feeds one 37-byte packet. Returns a bitmask of ports with a live pad (bit 0 = port 1). */
    external fun processPacket(packet: ByteArray): Int

    /** Removes every virtual pad (adapter closed or app stopping). */
    external fun releaseAll()

    /** false = Nintendo positions (default), true = map by label like an Xbox pad. */
    external fun setXboxLayout(xbox: Boolean)

    /** Radial stick deadzone in % of full tilt (0–50). */
    external fun setStickDeadzone(percent: Int)

    /** Trigger deadzone in analog units above the resting value (0–100). */
    external fun setTriggerDeadzone(units: Int)

    /** Re-center sticks and triggers of every connected controller on the next packet. */
    external fun recalibrate()

    /** Fills [out] (24 ints) with each port's centers: sx sy cx cy lt rt; zeros if no pad.
     *  Call from the reader thread. */
    external fun getCalibration(out: IntArray)

    /** Pushes saved settings into the native side. */
    fun apply(s: Settings) {
        setXboxLayout(s.xboxLayout)
        setStickDeadzone(s.stickDeadzone)
        setTriggerDeadzone(s.triggerDeadzone)
    }
}
