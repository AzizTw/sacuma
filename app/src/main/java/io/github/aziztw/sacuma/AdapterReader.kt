package io.github.aziztw.sacuma

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.os.SystemClock
import android.util.Log

/**
 * Owns the USB connection to the adapter and reads packets on a background thread.
 * bulkTransfer() blocks, so this must never run on the UI thread.
 */
class AdapterReader(
    private val usb: UsbManager,
    private val device: UsbDevice,
    /** Called on the reader thread with the port states and the active-virtual-pad bitmask. */
    private val onUpdate: (pads: List<PadState>, activePads: Int) -> Unit,
    /** Called on the reader thread once it has stopped, for whatever reason. */
    private val onClosed: () -> Unit,
) {
    val deviceName: String get() = device.deviceName

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread(::run, "gc-adapter-reader").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(1500)
        thread = null
    }

    private fun run() {
        try {
            readLoop()
        } finally {
            onClosed()
        }
    }

    private fun readLoop() {
        val conn = usb.openDevice(device)
        if (conn == null) {
            Log.e(TAG, "openDevice failed")
            return
        }
        val intf = device.getInterface(0)
        // force = true detaches the kernel's usbhid driver, which drops the
        // adapter's oversized packets (see "Dead ends" in AGENTS.md).
        if (!conn.claimInterface(intf, true)) {
            Log.e(TAG, "claimInterface failed")
            conn.close()
            return
        }

        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep else epOut = ep
        }
        if (epIn == null || epOut == null) {
            Log.e(TAG, "endpoints not found (count=${intf.endpointCount})")
            conn.releaseInterface(intf)
            conn.close()
            return
        }

        // One byte 0x13 tells the adapter to start streaming.
        val sent = conn.bulkTransfer(epOut, byteArrayOf(0x13), 1, 1000)
        Log.i(TAG, "adapter open: in=0x%02x out=0x%02x, init sent=%d".format(
            epIn.address, epOut.address, sent))

        val buf = ByteArray(PACKET_LEN)
        var last = List(4) { PadState.EMPTY }
        var packets = 0
        var windowPackets = 0
        var errors = 0
        var windowStart = SystemClock.elapsedRealtime()
        var lastActive = 0
        var shownActive = 0

        try {
            while (running) {
                val n = conn.bulkTransfer(epIn, buf, buf.size, 1000)
                if (n < 0) {
                    if (++errors > 50) {
                        Log.w(TAG, "adapter lost (50 failed reads in a row)")
                        break
                    }
                    continue
                }
                errors = 0
                if (n != PACKET_LEN || buf[0] != PACKET_HEADER) {
                    Log.w(TAG, "unexpected packet: len=$n first=0x%02x".format(buf[0]))
                    continue
                }

                packets++
                windowPackets++
                if (packets == 1) {
                    Log.i(TAG, "first packet: " + buf.joinToString(" ") { "%02x".format(it) })
                }

                // Virtual gamepads (C, via JNI) get every packet; logging below only on change.
                val active = VirtualPads.processPacket(buf)
                if (active != lastActive) {
                    Log.i(TAG, "virtual pads active: " +
                        (0 until 4).filter { active and (1 shl it) != 0 }.map { it + 1 })
                    lastActive = active
                }

                val pads = parsePacket(buf)
                var changed = lastActive != shownActive
                shownActive = lastActive
                for (i in 0 until 4) {
                    if (pads[i].differsFrom(last[i])) {
                        if (pads[i].digitalDiffers(last[i])) Log.i(TAG, "port ${i + 1}: ${pads[i]}")
                        changed = true
                    }
                }
                if (changed) {
                    last = pads
                    onUpdate(pads, active)
                }

                val now = SystemClock.elapsedRealtime()
                if (now - windowStart >= 5000) {
                    Log.i(TAG, "rate: %.1f packets/s (total %d)".format(
                        windowPackets * 1000.0 / (now - windowStart), packets))
                    windowStart = now
                    windowPackets = 0
                }
            }
        } finally {
            VirtualPads.releaseAll()
            conn.releaseInterface(intf)
            conn.close()
            Log.i(TAG, "adapter closed after $packets packets")
        }
    }

    companion object {
        private const val TAG = "Sacuma"
    }
}
