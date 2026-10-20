package com.example.gcadapter

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView

/**
 * The screen. It only asks for permissions, starts AdapterService, and shows the
 * service's state; the adapter itself is handled entirely by the service.
 */
class MainActivity : Activity() {

    // `external` = implemented in C (native-lib.c), resolved by JNI at runtime.
    private external fun nativeHello(): String

    private lateinit var usb: UsbManager
    private lateinit var text: TextView
    private var header = ""
    private var message = ""

    // Android answers our USB permission request with a broadcast; this receives it.
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                Log.i(TAG, "USB permission granted")
                startAdapterService()
            } else {
                Log.w(TAG, "USB permission denied")
                setMessage("USB permission denied. Replug the adapter to try again.")
            }
        }
    }

    private val stateListener: (AdapterState.Snapshot) -> Unit = { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        header = nativeHello()
        text = TextView(this).apply {
            textSize = 18f
            setPadding(48, 48, 48, 48)
        }
        setContentView(text)

        usb = getSystemService(UsbManager::class.java)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(permissionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(permissionReceiver, filter)
        }

        // Android 13+: notifications (incl. our service's) need a runtime permission.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        handle(intent)
    }

    // singleTop: a second launch (e.g. adapter plugged in while open) lands here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        AdapterState.addListener(stateListener)
    }

    override fun onStop() {
        AdapterState.removeListener(stateListener)
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(permissionReceiver)
        super.onDestroy()
    }

    private fun handle(intent: Intent) {
        val device = findAdapter(usb)
        when {
            device == null -> setMessage("GameCube adapter not found. Plug it in.")
            !usb.hasPermission(device) -> {
                setMessage("Asking for USB permission…")
                requestPermission(device)
            }
            else -> {
                startAdapterService()
                // Opened by plugging the adapter in: start in the background and get
                // out of the way so the user is back where they were.
                if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) finish()
            }
        }
    }

    private fun startAdapterService() {
        startForegroundService(Intent(this, AdapterService::class.java))
        setMessage("")
    }

    private fun requestPermission(device: UsbDevice) {
        // The intent must name our package (explicit), and be mutable so the
        // system can add the device and the granted flag to it.
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        usb.requestPermission(device, PendingIntent.getBroadcast(this, 0, intent, flags))
    }

    private fun setMessage(m: String) {
        message = m
        render(AdapterState.current)
    }

    private fun render(s: AdapterState.Snapshot) {
        val body = if (s.running) {
            s.pads.mapIndexed { i, p ->
                val pad = if (s.activePads and (1 shl i) != 0) "  → virtual pad" else ""
                "Port ${i + 1}: $p$pad"
            }.joinToString("\n")
        } else {
            message.ifEmpty { s.status }
        }
        text.text = "$header\n\nService: ${if (s.running) "running" else "stopped"}\n\n$body"
    }

    companion object {
        private const val TAG = "GcAdapter"
        private const val ACTION_USB_PERMISSION = "com.example.gcadapter.USB_PERMISSION"

        init {
            // Loads libgcadapter.so, which CMake builds from src/main/cpp.
            System.loadLibrary("gcadapter")
        }
    }
}
