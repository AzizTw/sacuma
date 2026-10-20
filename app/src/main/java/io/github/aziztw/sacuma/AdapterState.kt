package io.github.aziztw.sacuma

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Latest adapter state, shared between AdapterService (writes) and the UI (reads).
 * Both live in the same app process, so a plain singleton is enough.
 * Listeners are always called on the main (UI) thread.
 */
object AdapterState {
    data class Snapshot(
        val running: Boolean = false,
        val status: String = "Not running",
        val pads: List<PadState> = List(4) { PadState.EMPTY },
        val activePads: Int = 0,
        val rateHz: Double = 0.0,
        /** Per port: sx sy cx cy lt rt centers (6 ints each), zeros when no pad. */
        val calibration: IntArray = IntArray(24),
    )

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(Snapshot) -> Unit>()

    @Volatile
    var current = Snapshot()
        private set

    @Synchronized
    fun update(change: (Snapshot) -> Snapshot) {
        val next = change(current)
        current = next
        main.post { listeners.forEach { it(next) } }
    }

    fun addListener(listener: (Snapshot) -> Unit) {
        listeners += listener
        listener(current)
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners -= listener
    }
}
