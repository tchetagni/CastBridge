package castbridge.core.tv

import java.io.File

/** One USB device as the kernel describes it (/sys/bus/usb/devices/<name>). */
data class UsbNode(
    val name: String, val vendorId: String, val productId: String, val manufacturer: String?, val product: String?,
    val serial: String?, val speedMbps: Int, val deviceClass: String?, val controller: String?,
    val usbVersion: String? = null, val maxPowerMa: String? = null,
)

/**
 * What the TV can say about the drive that holds the heavy files: who made it, the speed the USB bus negotiated, and whether it
 * shares its bus with the TV's own Wi-Fi chip (many TVs have a USB Wi-Fi module: a copy over Wi-Fi then crosses the same bus twice).
 * The serial number is given in full to the owner (screens behind the PIN); [serialMasked] is for anything that gets shared.
 */
data class UsbKeyInfo(
    val vendorId: String, val productId: String, val manufacturer: String?, val product: String?, val serial: String?,
    val speedMbps: Int, val controller: String?, val sharesBusWith: List<String>, val sharesBusWithWifi: Boolean,
    val usbVersion: String? = null, val maxPowerMa: String? = null,
) {
    val serialMasked: String? get() = UsbHardware.maskSerial(serial)
    val vidPid: String get() = "$vendorId:$productId"
    val speedLabel: String get() = when {
        speedMbps >= 10_000 -> "USB 3.2 ($speedMbps Mbit/s)"
        speedMbps >= 5_000 -> "USB 3 (5 Gbit/s)"
        speedMbps >= 480 -> "USB 2.0 (480 Mbit/s)"
        speedMbps >= 12 -> "USB 1.1 ($speedMbps Mbit/s)"
        speedMbps > 0 -> "USB 1.0 ($speedMbps Mbit/s)"
        else -> "vitesse inconnue"
    }
    val displayName: String get() = listOfNotNull(manufacturer, product).joinToString(" ").ifBlank { "clé USB" }

    /** One line for the storage screens: « Kingston DataTraveler 3.0 · 0951:1666 · USB 2.0 (480 Mbit/s) ». */
    fun info(): String = "$displayName · $vidPid · $speedLabel" + (serial?.let { " · n° série $it" } ?: "")

    /** Everything the kernel says about the drive, as label to value, for the settings screens. */
    fun details(): List<Pair<String, String>> = listOfNotNull(
        manufacturer?.let { "Marque (constructeur)" to it }, product?.let { "Modèle" to it },
        "Identifiants USB (vendeur : produit)" to vidPid, serial?.let { "Numéro de série" to it },
        "Vitesse négociée" to speedLabel, usbVersion?.let { "Version USB déclarée" to it }, maxPowerMa?.let { "Courant demandé" to it },
        controller?.let { "Contrôleur de la TV" to it },
        if (sharesBusWith.isNotEmpty()) "Autres appareils sur le même bus" to sharesBusWith.joinToString(", ") else null,
    )

    /** Honest, actionable remarks (empty when there is nothing to say). */
    fun warnings(): List<String> = buildList {
        if (sharesBusWithWifi) add(
            "La clé et le Wi-Fi de la TV (${sharesBusWith.firstOrNull() ?: "module Wi-Fi"}) partagent le même bus USB ($speedLabel) : une copie par Wi-Fi " +
                "vers cette clé est limitée (environ la moitié du bus, de l'ordre de 10 Mo/s) et peut gêner le Wi-Fi. " +
                "Essayez l'autre prise USB de la TV : elle peut dépendre d'un autre contrôleur.")
        if (speedMbps in 1..4999 && Regex("""3\.[01]|SuperSpeed|USB ?3""", RegexOption.IGNORE_CASE).containsMatchIn(product.orEmpty()))
            add("La clé est annoncée USB 3 mais la TV la voit en $speedLabel : elle écrira moins vite que sur un ordinateur.")
    }
}

object UsbHardware {
    private val WIFI_TEXT = Regex("""wlan|wi-?fi|wireless|802\.11""", RegexOption.IGNORE_CASE)
    private val WIFI_VENDORS = setOf("a69c", "148f", "0cf3")          // aicsemi, Ralink/MediaTek, Qualcomm Atheros

    fun isRootHub(n: UsbNode) = n.vendorId.equals("1d6b", true)
    fun isWifi(n: UsbNode) = !isRootHub(n) &&
        (WIFI_TEXT.containsMatchIn(n.product.orEmpty()) || WIFI_TEXT.containsMatchIn(n.manufacturer.orEmpty()) || n.vendorId.lowercase() in WIFI_VENDORS)

    fun maskSerial(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.length <= 8) "…" + it.takeLast(2) else it.take(2) + "…" + it.takeLast(4) }

    /** The bus a device sits on: « 1-1 » and « 1-2.3 » are on bus 1. */
    private fun bus(name: String) = name.substringBefore('-')

    /** [keyName] is the kernel name of the drive's device (« 1-1 »). */
    fun analyze(nodes: List<UsbNode>, keyName: String): UsbKeyInfo? {
        val key = nodes.firstOrNull { it.name == keyName } ?: return null
        val sameBus = nodes.filter { it.name != key.name && !isRootHub(it) && bus(it.name) == bus(key.name) && it.deviceClass != "09" }
        val wifi = sameBus.filter(::isWifi)
        return UsbKeyInfo(key.vendorId, key.productId, key.manufacturer, key.product, key.serial, key.speedMbps, key.controller,
            sharesBusWith = sameBus.map { it.product ?: it.manufacturer ?: it.vidPid() }, sharesBusWithWifi = wifi.isNotEmpty(),
            usbVersion = key.usbVersion, maxPowerMa = key.maxPowerMa)
            .let { if (wifi.isNotEmpty()) it.copy(sharesBusWith = wifi.map { w -> w.product ?: w.manufacturer ?: w.vidPid() } + (it.sharesBusWith - wifi.map { w -> w.product ?: w.manufacturer ?: w.vidPid() }.toSet())) else it }
    }

    private fun UsbNode.vidPid() = "$vendorId:$productId"
}

/** Reads /sys (root injectable for tests). Never throws: an unreadable or unusual system just gives null. */
class UsbSysfs(private val root: File = File("/sys")) {
    private fun File.text(name: String): String? = runCatching { File(this, name).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun nodes(): List<UsbNode> = runCatching {
        val dir = File(root, "bus/usb/devices")
        val all = dir.listFiles().orEmpty().filter { it.text("idVendor") != null }
        all.map { d ->
            val busNum = d.name.removePrefix("usb").substringBefore('-').takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
            val hub = busNum?.let { File(dir, "usb$it") }
            UsbNode(d.name, d.text("idVendor")!!, d.text("idProduct").orEmpty(), d.text("manufacturer"), d.text("product"), d.text("serial"),
                d.text("speed")?.toDoubleOrNull()?.toInt() ?: 0, d.text("bDeviceClass"), hub?.text("serial"),
                d.text("version"), d.text("bMaxPower"))
        }
    }.getOrDefault(emptyList())

    /** The USB device name (« 1-1 ») behind the block device [major]:[minor], or null when it is not on USB. */
    fun usbNameOfBlock(major: Int, minor: Int): String? = runCatching {
        var blk = File(root, "dev/block/$major:$minor").canonicalFile
        if (File(blk, "partition").exists()) blk = blk.parentFile
        val path = File(blk, "device").canonicalPath
        Regex("""/usb\d+/(\d+-[\d.]+)""").find(path)?.groupValues?.get(1)
    }.getOrNull()

    fun infoForBlock(major: Int, minor: Int): UsbKeyInfo? = usbNameOfBlock(major, minor)?.let { UsbHardware.analyze(nodes(), it) }

    companion object {
        /** « /dev/block/vold/public:8,1 » -> 8 to 1. */
        fun majorMinor(device: String): Pair<Int, Int>? =
            Regex("""(\d+),(\d+)$""").find(device)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
    }
}
