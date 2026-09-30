package castbridge.core.device

import castbridge.core.net.JsonLite
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Raw facts the app reads from Android (Build.*, PackageManager features, DisplayMetrics, StatFs…) and hands to
 * [DeviceReport.collect]. Everything is optional: a value the app cannot read stays null. No personal content.
 */
interface DeviceFacts {
    val app: String                       // "tv" | "phone"
    val installId: String                 // random UUID drawn at the first launch and kept ([DeviceIdentity.newInstallId])
    val androidId: String? get() = null   // Settings.Secure.ANDROID_ID, hashed with the app salt before leaving the device
    val versionCode: Int
    val versionName: String? get() = null
    val channel: String get() = "stable"
    val supportedAbis: List<String> get() = emptyList()   // Build.SUPPORTED_ABIS, preferred first
    val sdkInt: Int? get() = null
    val manufacturer: String? get() = null                // Build.MANUFACTURER
    val model: String? get() = null                       // Build.MODEL
    val deviceName: String? get() = null                  // user-visible name (Settings.Global.DEVICE_NAME / Bluetooth name)
    val buildDisplay: String? get() = null                // Build.DISPLAY
    val fingerprint: String? get() = null                 // Build.FINGERPRINT
    val hasLeanback: Boolean? get() = null                // FEATURE_LEANBACK
    val hasGoogleTv: Boolean? get() = null                // "com.google.android.feature.GOOGLE_EXPERIENCE" + leanback, or Google TV launcher present
    val isTelevisionUi: Boolean? get() = null             // UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION
    val hasTouchscreen: Boolean? get() = null             // FEATURE_TOUCHSCREEN
    val hasTelephony: Boolean? get() = null               // FEATURE_TELEPHONY
    val smallestWidthDp: Int? get() = null                // Configuration.smallestScreenWidthDp
    val screenWidthPx: Int? get() = null
    val screenHeightPx: Int? get() = null
    val densityDpi: Int? get() = null
    val ramTotalBytes: Long? get() = null                 // ActivityManager.MemoryInfo.totalMem
    val storageFreeBytes: Long? get() = null
    val storageTotalBytes: Long? get() = null
    val usbPresent: Boolean? get() = null
    val usbFreeBytes: Long? get() = null
    val btGatewayActive: Boolean? get() = null
    val sshEnabled: Boolean? get() = null
    val wifiDirectActive: Boolean? get() = null
    val videoCount: Int? get() = null
    val lastError: String? get() = null
    /** Choice of the user on the information screen (null = not asked yet: treated as essential only). */
    val consent: castbridge.core.telemetry.Consent? get() = null
    /** Version of the information text shown (e.g. "2026-10"). */
    val consentVersion: String? get() = null
}

/** Stable identifiers of an installation. */
object DeviceIdentity {
    fun newInstallId(): String = UUID.randomUUID().toString()

    /** SHA-256(salt + ":" + ANDROID_ID) in hex: the raw ANDROID_ID never leaves the device. [salt] is a constant of the app. */
    fun hashAndroidId(androidId: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$androidId".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Kind of device, detected from generic Android signals (any brand of TV or box, not one in particular). */
enum class Platform(val key: String) {
    ANDROID_TV("android-tv"), GOOGLE_TV("google-tv"), FIRE_OS("fire-os"), ANDROID_BOX("android-box"),
    PHONE("phone"), TABLET("tablet"), OTHER("other");

    companion object {
        fun detect(f: DeviceFacts): Platform {
            val maker = f.manufacturer?.lowercase(Locale.ROOT).orEmpty()
            return when {
                maker == "amazon" || f.model?.startsWith("AFT") == true -> FIRE_OS
                f.hasGoogleTv == true -> GOOGLE_TV
                f.hasLeanback == true -> ANDROID_TV
                f.isTelevisionUi == true -> ANDROID_BOX
                f.hasTouchscreen == false -> ANDROID_BOX          // no touch screen, no leanback: a generic box
                f.hasTelephony == true -> PHONE
                (f.smallestWidthDp ?: 0) >= 600 -> TABLET
                f.app == "phone" -> PHONE
                else -> OTHER
            }
        }

        /** Readable OS name from generic signals; known TV skins are recognized from Build.DISPLAY / FINGERPRINT. */
        fun osName(f: DeviceFacts, platform: Platform): String {
            val text = listOfNotNull(f.buildDisplay, f.fingerprint).joinToString(" ").lowercase(Locale.ROOT)
            return when {
                platform == FIRE_OS -> "Fire OS"
                "gaia" in text -> "GaiaOS"
                "vidaa" in text -> "VIDAA"
                "whaleos" in text -> "WhaleOS"
                "titanos" in text -> "TitanOS"
                platform == GOOGLE_TV -> "Google TV"
                platform == ANDROID_TV -> "Android TV"
                else -> "Android"
            }
        }
    }
}

/** What is sent to POST /api/v1/devices/register and /heartbeat (same field names as the server's DeviceReport). */
data class DeviceReport(
    val installId: String,
    val androidIdHash: String?,
    val app: String,
    val versionCode: Int,
    val versionName: String?,
    val channel: String,
    val abi: String?,
    val supportedAbis: List<String>,
    val sdk: Int?,
    val platform: String,
    val manufacturer: String?,
    val model: String?,
    val deviceName: String?,
    val osName: String,
    val osBuild: String?,
    val fingerprint: String?,
    val screen: String?,
    val densityDpi: Int?,
    val ramTotalMb: Int?,
    val storageFreeMb: Int?,
    val storageTotalMb: Int?,
    val usbPresent: Boolean?,
    val usbFreeMb: Int?,
    val btGateway: Boolean?,
    val sshEnabled: Boolean?,
    val wifiDirect: Boolean?,
    val videoCount: Int?,
    val lastError: String?,
    val consent: String,
    val consentVersion: String?,
) {
    fun toJson(): String = JsonLite.write(linkedMapOf(
        "installId" to installId, "androidIdHash" to androidIdHash, "app" to app, "versionCode" to versionCode,
        "versionName" to versionName, "channel" to channel, "abi" to abi, "supportedAbis" to supportedAbis, "sdk" to sdk,
        "platform" to platform, "manufacturer" to manufacturer, "model" to model, "deviceName" to deviceName, "osName" to osName,
        "osBuild" to osBuild, "fingerprint" to fingerprint, "screen" to screen, "densityDpi" to densityDpi,
        "ramTotalMb" to ramTotalMb, "storageFreeMb" to storageFreeMb, "storageTotalMb" to storageTotalMb,
        "usbPresent" to usbPresent, "usbFreeMb" to usbFreeMb, "btGateway" to btGateway, "sshEnabled" to sshEnabled,
        "wifiDirect" to wifiDirect, "videoCount" to videoCount, "lastError" to lastError, "consent" to consent,
        "consentVersion" to consentVersion,
    ))

    companion object {
        /** @param salt constant of the app used to hash ANDROID_ID (never send the raw value) */
        fun collect(f: DeviceFacts, salt: String): DeviceReport {
            val platform = Platform.detect(f)
            val w = f.screenWidthPx
            val h = f.screenHeightPx
            return DeviceReport(
                installId = f.installId,
                androidIdHash = f.androidId?.takeIf { it.isNotBlank() }?.let { DeviceIdentity.hashAndroidId(it, salt) },
                app = f.app,
                versionCode = f.versionCode,
                versionName = f.versionName,
                channel = f.channel,
                abi = f.supportedAbis.firstOrNull(),
                supportedAbis = f.supportedAbis.take(8),
                sdk = f.sdkInt,
                platform = platform.key,
                manufacturer = f.manufacturer?.take(64),
                model = f.model?.take(64),
                deviceName = f.deviceName?.take(80),
                osName = Platform.osName(f, platform),
                osBuild = f.buildDisplay?.take(160),
                fingerprint = f.fingerprint?.take(200),
                screen = if (w != null && h != null) "${maxOf(w, h)}x${minOf(w, h)}" else null,
                densityDpi = f.densityDpi,
                ramTotalMb = f.ramTotalBytes?.let { mb(it) },
                storageFreeMb = f.storageFreeBytes?.let { mb(it) },
                storageTotalMb = f.storageTotalBytes?.let { mb(it) },
                usbPresent = f.usbPresent,
                usbFreeMb = if (f.usbPresent == true) f.usbFreeBytes?.let { mb(it) } else null,
                btGateway = f.btGatewayActive,
                sshEnabled = f.sshEnabled,
                wifiDirect = f.wifiDirectActive,
                videoCount = f.videoCount,
                lastError = f.lastError?.take(500),
                consent = if (f.consent == castbridge.core.telemetry.Consent.USAGE) "usage" else "essential",
                consentVersion = f.consentVersion?.take(16),
            )
        }

        private fun mb(bytes: Long): Int = (bytes / (1024 * 1024)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }
}
