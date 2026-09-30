package castbridge.receiver

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import castbridge.core.device.DeviceClient
import castbridge.core.device.DeviceFacts
import castbridge.core.device.DeviceReport
import castbridge.core.device.DeviceStore
import java.util.UUID

/**
 * Registers the TV with the CastBridge server (device heartbeat) and reports, among the technical facts, its Bluetooth MAC
 * address, so the admin can see and store the Bluetooth addresses of every controlled TV. Unreachable server = silent retry.
 */
object DeviceHub {
    private const val DEFAULT_URL = "https://bridge.sti-cm.com"
    private const val SALT = "castbridge-tv"
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("castbridge_device", Context.MODE_PRIVATE)
    }

    private fun baseUrl() = prefs.getString("device_server_url", null) ?: DEFAULT_URL

    private fun installId(): String = prefs.getString("install_id", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("install_id", it).apply() }

    /** The Bluetooth MAC (adapter address). Android 6+ may hide it for other apps; the TV's own adapter usually still answers. */
    @Suppress("DEPRECATION")
    private fun bluetoothMac(ctx: Context): String? = runCatching {
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.address
            ?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }
    }.getOrNull()

    fun heartbeat(ctx: Context) {
        val app = ctx.applicationContext
        val facts = object : DeviceFacts {
            override val app = "tv"
            override val installId = installId()
            override val versionCode = runCatching {
                val pi = app.packageManager.getPackageInfo(app.packageName, 0)
                if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
            }.getOrDefault(0)
            override val versionName = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
            override val manufacturer = Build.MANUFACTURER
            override val model = Build.MODEL
            override val supportedAbis = Build.SUPPORTED_ABIS.toList()
            override val sdkInt = Build.VERSION.SDK_INT
            override val buildDisplay = Build.DISPLAY
            override val fingerprint = Build.FINGERPRINT
            override val hasLeanback = app.packageManager.hasSystemFeature("android.software.leanback")
            override val hasTouchscreen = app.packageManager.hasSystemFeature("android.hardware.touchscreen")
            override val btAddress = bluetoothMac(app)
        }
        val client = DeviceClient(baseUrl(), object : DeviceStore {
            override var deviceId: String?
                get() = prefs.getString("device_id", null)
                set(v) { prefs.edit().putString("device_id", v).apply() }
            override var deviceToken: String?
                get() = prefs.getString("device_token", null)
                set(v) { prefs.edit().putString("device_token", v).apply() }
        })
        try {
            client.heartbeat(DeviceReport.collect(facts, SALT))
        } catch (e: Exception) {
            // server unreachable (no network, maintenance): retried on the next tick
        }
    }
}
