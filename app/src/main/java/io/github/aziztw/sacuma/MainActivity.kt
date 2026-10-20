package io.github.aziztw.sacuma

import android.Manifest
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
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aziztw.sacuma.ui.SacumaScreen
import io.github.aziztw.sacuma.ui.UiActions
import io.github.aziztw.sacuma.ui.UiState

/**
 * The screen. It asks for permissions, starts/stops AdapterService, edits settings,
 * and shows the service's state; the adapter itself is handled by the service.
 */
class MainActivity : ComponentActivity() {

    private lateinit var usb: UsbManager

    // Compose redraws whatever reads these when they change.
    private var adapter by mutableStateOf(AdapterState.current)
    private var settings by mutableStateOf(Settings())
    private var message by mutableStateOf("")

    // Android answers our USB permission request with a broadcast; this receives it.
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                Log.i(TAG, "USB permission granted")
                startAdapterService()
            } else {
                Log.w(TAG, "USB permission denied")
                message = "USB permission denied"
            }
        }
    }

    private val stateListener: (AdapterState.Snapshot) -> Unit = { adapter = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // draw behind the status/navigation bars; the screen pads itself
        super.onCreate(savedInstanceState)

        usb = getSystemService(UsbManager::class.java)
        settings = Settings.load(this)
        VirtualPads.apply(settings)

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

        val actions = UiActions(
            onDriverToggle = { on -> if (on) connect(fromAttach = false) else stopAdapterService() },
            onSettings = { s ->
                settings = s
                s.save(this)
                VirtualPads.apply(s)
            },
            onRecalibrate = { VirtualPads.recalibrate() },
        )
        setContent {
            SacumaScreen(UiState(adapter, settings, message), actions)
        }

        connect(fromAttach = intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED)
    }

    // singleTop: a second launch (e.g. adapter plugged in while open) lands here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        connect(fromAttach = intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED)
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

    // Our own virtual pads would otherwise drive this screen (e.g. GC A = Android "Back"
    // in the Nintendo layout). While Sacuma is in front it acts as a tester, so swallow
    // their input; the device's built-in controls still work normally.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        isOurPad(event.device) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        isOurPad(event.device) || super.dispatchGenericMotionEvent(event)

    // Matches our pads and copies of them (AYN's mapper republishes them with the same name).
    private fun isOurPad(device: InputDevice?): Boolean =
        device?.name?.startsWith(VirtualPads.DEVICE_NAME_PREFIX) == true

    private fun connect(fromAttach: Boolean) {
        val device = findAdapter(usb)
        when {
            device == null -> message = "Plug in the adapter"
            !usb.hasPermission(device) -> {
                message = "Waiting for USB permission"
                requestPermission(device)
            }
            else -> {
                startAdapterService()
                // Opened by plugging the adapter in: start in the background and get
                // out of the way so the user is back where they were.
                if (fromAttach) finish()
            }
        }
    }

    private fun startAdapterService() {
        message = ""
        startForegroundService(Intent(this, AdapterService::class.java))
    }

    private fun stopAdapterService() {
        startService(Intent(this, AdapterService::class.java).setAction(AdapterService.ACTION_STOP))
    }

    private fun requestPermission(device: UsbDevice) {
        // The intent must name our package (explicit), and be mutable so the
        // system can add the device and the granted flag to it.
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        usb.requestPermission(device, PendingIntent.getBroadcast(this, 0, intent, flags))
    }

    companion object {
        private const val TAG = "Sacuma"
        private const val ACTION_USB_PERMISSION = "io.github.aziztw.sacuma.USB_PERMISSION"
    }
}
