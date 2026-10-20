package com.example.gcadapter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * Foreground service that owns the adapter connection and the virtual pads, so they
 * keep working while a game is in front. Started by MainActivity once USB
 * permission is granted; stops itself when the adapter is unplugged or on "Stop".
 */
class AdapterService : Service() {

    private lateinit var usb: UsbManager
    private var reader: AdapterReader? = null
    private var notifiedPads = -1
    private val main = Handler(Looper.getMainLooper())

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = usbDeviceExtra(intent) ?: return
            if (device.deviceName == reader?.deviceName) {
                Log.i(TAG, "adapter unplugged")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        usb = getSystemService(UsbManager::class.java)
        createChannel()
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(detachReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(detachReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "stop requested")
            stopSelf()
            return START_NOT_STICKY
        }

        // Must be called soon after startForegroundService(), or Android kills the app.
        val notification = buildNotification("Connecting…")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        connect()
        // If Android ever kills us, recreate the service and reconnect.
        return START_STICKY
    }

    override fun onDestroy() {
        reader?.stop()
        reader = null
        unregisterReceiver(detachReceiver)
        AdapterState.update { AdapterState.Snapshot(status = "Stopped") }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connect() {
        if (reader != null) return
        val device = findAdapter(usb)
        if (device == null || !usb.hasPermission(device)) {
            Log.w(TAG, "service started without an adapter or USB permission")
            stopSelf()
            return
        }

        AdapterState.update { it.copy(running = true, status = "Connected") }
        var self: AdapterReader? = null
        self = AdapterReader(
            usb, device,
            onUpdate = { pads, active ->
                AdapterState.update { it.copy(pads = pads, activePads = active) }
                if (active != notifiedPads) {
                    notifiedPads = active
                    notify(padSummary(active))
                }
            },
            onClosed = {
                // Reader died (adapter lost or stopped): nothing left to do.
                main.post {
                    if (reader === self) {
                        reader = null
                        stopSelf()
                    }
                }
            },
        )
        reader = self
        self.start()
    }

    private fun padSummary(active: Int): String {
        val ports = (0 until 4).filter { active and (1 shl it) != 0 }.map { "P${it + 1}" }
        return if (ports.isEmpty()) "No controllers plugged in" else "Gamepads: " + ports.joinToString(" ")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Adapter status", NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shown while the GameCube adapter is active" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AdapterService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gamepad)
            .setContentTitle("GameCube adapter active")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        private const val TAG = "GcAdapter"
        private const val CHANNEL_ID = "adapter"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.example.gcadapter.STOP"
    }
}

const val GC_VENDOR_ID = 0x057e
const val GC_PRODUCT_ID = 0x0337

fun findAdapter(usb: UsbManager): UsbDevice? = usb.deviceList.values.firstOrNull {
    it.vendorId == GC_VENDOR_ID && it.productId == GC_PRODUCT_ID
}

@Suppress("DEPRECATION")
fun usbDeviceExtra(intent: Intent): UsbDevice? =
    if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
