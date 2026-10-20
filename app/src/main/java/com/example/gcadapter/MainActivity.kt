package com.example.gcadapter

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class MainActivity : Activity() {

    // `external` = implemented in C (native-lib.c), resolved by JNI at runtime.
    private external fun nativeHello(): String

    private lateinit var usb: UsbManager
    private lateinit var text: TextView
    private var header = ""
    private var reader: AdapterReader? = null

    // Android answers our permission request with a broadcast; this receives it.
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                Log.i(TAG, "USB permission granted")
                connectAdapter()
            } else {
                Log.w(TAG, "USB permission denied")
                show("USB permission denied")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        header = nativeHello()
        Log.i(TAG, header)

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

        connectAdapter()
    }

    // Called instead of onCreate when the app is already open and the adapter is
    // plugged in (launchMode="singleTop" + the USB_DEVICE_ATTACHED filter).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        connectAdapter()
    }

    override fun onDestroy() {
        reader?.stop()
        unregisterReceiver(permissionReceiver)
        super.onDestroy()
    }

    private fun connectAdapter() {
        if (reader != null) return
        val device = usb.deviceList.values.firstOrNull {
            it.vendorId == GC_VENDOR_ID && it.productId == GC_PRODUCT_ID
        }
        if (device == null) {
            show("GameCube adapter not found. Plug it in.")
            return
        }
        if (!usb.hasPermission(device)) {
            show("Asking for USB permission…")
            requestPermission(device)
            return
        }
        show("Adapter connected, waiting for packets…")
        reader = AdapterReader(usb, device) { pads, active ->
            runOnUiThread {
                show(pads.mapIndexed { i, p ->
                    val pad = if (active and (1 shl i) != 0) "  → virtual pad" else ""
                    "Port ${i + 1}: $p$pad"
                }.joinToString("\n"))
            }
        }.also { it.start() }
    }

    private fun requestPermission(device: UsbDevice) {
        // The intent must name our package (explicit), and be mutable so the
        // system can add the device and the granted flag to it.
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        usb.requestPermission(device, PendingIntent.getBroadcast(this, 0, intent, flags))
    }

    private fun show(status: String) {
        text.text = "$header\n\n$status"
    }

    companion object {
        private const val TAG = "GcAdapter"
        private const val ACTION_USB_PERMISSION = "com.example.gcadapter.USB_PERMISSION"
        private const val GC_VENDOR_ID = 0x057e
        private const val GC_PRODUCT_ID = 0x0337

        init {
            // Loads libgcadapter.so, which CMake builds from src/main/cpp.
            System.loadLibrary("gcadapter")
        }
    }
}
