package io.github.aziztw.sacuma

import kotlin.math.abs

/** One controller port's state, decoded from a 37-byte adapter packet. */
data class PadState(
    val type: Int,   // 0 = none, 1 = wired, 2 = wireless
    val b1: Int,     // A B X Y and d-pad bits
    val b2: Int,     // Start Z R L bits
    val sx: Int, val sy: Int,  // main stick
    val cx: Int, val cy: Int,  // C-stick
    val lt: Int, val rt: Int,  // analog triggers
) {
    val connected get() = type != 0

    fun buttonNames(): String = buildList {
        for ((bit, name) in B1_NAMES) if (b1 and bit != 0) add(name)
        for ((bit, name) in B2_NAMES) if (b2 and bit != 0) add(name)
    }.joinToString(" ")

    /** Connection or a button changed (worth a log line; analog moves would flood logcat). */
    fun digitalDiffers(o: PadState): Boolean = type != o.type || b1 != o.b1 || b2 != o.b2

    /** Changed enough to redraw? Analog values jitter by a few units at rest. */
    fun differsFrom(o: PadState): Boolean =
        type != o.type || b1 != o.b1 || b2 != o.b2 ||
            listOf(sx - o.sx, sy - o.sy, cx - o.cx, cy - o.cy, lt - o.lt, rt - o.rt)
                .any { abs(it) >= ANALOG_LOG_STEP }

    override fun toString(): String =
        if (!connected) "empty"
        else "${if (type == 2) "wireless" else "wired"} [${buttonNames()}] " +
            "stick=($sx,$sy) c=($cx,$cy) L=$lt R=$rt"

    companion object {
        const val ANALOG_LOG_STEP = 10
        val EMPTY = PadState(0, 0, 0, 0, 0, 0, 0, 0, 0)

        private val B1_NAMES = listOf(
            0x01 to "A", 0x02 to "B", 0x04 to "X", 0x08 to "Y",
            0x10 to "Left", 0x20 to "Right", 0x40 to "Down", 0x80 to "Up",
        )
        private val B2_NAMES = listOf(0x01 to "Start", 0x02 to "Z", 0x04 to "R", 0x08 to "L")
    }
}

const val PACKET_LEN = 37
const val PACKET_HEADER: Byte = 0x21

/** Packet layout: byte 0 = 0x21, then 4 ports x 9 bytes. */
fun parsePacket(pkt: ByteArray): List<PadState> = List(4) { port ->
    val o = 1 + 9 * port
    // Kotlin bytes are signed (-128..127); `and 0xFF` turns them into 0..255.
    fun u(i: Int) = pkt[o + i].toInt() and 0xFF
    PadState(u(0) shr 4, u(1), u(2), u(3), u(4), u(5), u(6), u(7), u(8))
}
