package castbridge.core.remote.smart

import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteKey.*
import castbridge.core.remote.RemoteReply
import castbridge.core.remote.RemoteTransport
import castbridge.core.remote.RemoteWire
import java.io.IOException

// ------------------------------------------------------------------------------------------------------------------------
// CastBridge-TV itself (HTTP / Bluetooth + token), through the existing transports. Always the first strategy when detected.
// ------------------------------------------------------------------------------------------------------------------------
class NativeStrategy(tv: TvTarget, private val open: () -> RemoteTransport, log: (String) -> Unit = {}) : BaseStrategy(tv, log) {
    override val id = StrategyIds.CASTBRIDGE
    override val label = "CastBridge-TV"
    override val status = StrategyStatus.STABLE
    override val verifiesDelivery = true
    override val capabilities = Capabilities(RemoteKey.values().toSet(), text = true, touchpad = true, apps = false)
    override val limits = "Touches dans CastBridge-TV ; pour piloter toute la TV, activer le mode « toute la TV » (docs/REMOTE.md)."

    private var link: RemoteTransport? = null
    private val sid = castbridge.core.remote.RemoteQueue.newSid()
    private var seq = 0L

    override fun applicable(fp: TvFingerprint) = fp.vendor == Vendor.CASTBRIDGE || StrategyIds.CASTBRIDGE in fp.candidates

    @Synchronized override fun connect() {
        runCatching { link?.close() }; link = null
        state = StrategyState(StrategyState.Kind.CONNECTING)
        val l = try { open() } catch (e: IOException) { failed(e.message ?: "TV injoignable") }
        val r = try { l.send("GET", "state", "") } catch (e: IOException) { runCatching { l.close() }; failed(e.message ?: "TV injoignable") }
        if (r.status == 401) { runCatching { l.close() }; needsPairing("Code PIN refusé par la TV") }
        if (r.status !in 200..299) { runCatching { l.close() }; failed("réponse inattendue (${r.status})") }
        link = l; ready(l.name)
    }

    @Synchronized override fun send(key: RemoteKey) {
        val r = post("key", mapOf("code" to key.wire, "target" to "auto"))
        if (r.status !in 200..299) throw IOException(castbridge.core.remote.RemoteSession.message(r))
    }

    @Synchronized override fun sendText(text: String): Boolean = post("text", mapOf("value" to text, "mode" to "insert", "target" to "auto")).status in 200..299

    private fun post(route: String, p: Map<String, String>): RemoteReply {
        val l = link ?: throw IOException("non connectée")
        try { return l.send("POST", route, RemoteWire.query(p + mapOf("sid" to sid, "seq" to (++seq).toString()))) }
        catch (e: IOException) { state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); runCatching { l.close() }; link = null; throw e }
    }

    override fun close() { runCatching { link?.close() }; link = null; super.close() }
}

// ------------------------------------------------------------------------------------------------------------------------
// Bluetooth HID: the phone declares itself a keyboard + consumer-control device (BluetoothHidDevice, Android 9+).
// ------------------------------------------------------------------------------------------------------------------------
object HidReports {
    const val KEYBOARD_ID = 1
    const val CONSUMER_ID = 2

    /** Boot-style keyboard (report 1: modifiers, reserved, 6 keys) + consumer control (report 2: one 16-bit usage). USB HID usage tables 1.12. */
    val DESCRIPTOR: ByteArray = intArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, 0x01, 0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01, 0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00, 0xC0,
        0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, 0x02, 0x19, 0x00, 0x2A, 0xFF, 0x03, 0x15, 0x00, 0x26, 0xFF, 0x03, 0x75, 0x10, 0x95, 0x01, 0x81, 0x00, 0xC0,
    ).map { it.toByte() }.toByteArray()

    sealed class Usage { data class Keyboard(val usage: Int) : Usage(); data class Consumer(val usage: Int) : Usage() }

    val USAGES: Map<RemoteKey, Usage> = buildMap {
        put(DPAD_UP, Usage.Keyboard(0x52)); put(DPAD_DOWN, Usage.Keyboard(0x51)); put(DPAD_LEFT, Usage.Keyboard(0x50)); put(DPAD_RIGHT, Usage.Keyboard(0x4F))
        put(DPAD_CENTER, Usage.Keyboard(0x28)); put(ENTER, Usage.Keyboard(0x28)); put(BACK, Usage.Consumer(0x224)); put(HOME, Usage.Consumer(0x223)); put(MENU, Usage.Consumer(0x40))
        put(DEL, Usage.Keyboard(0x2A))
        put(VOLUME_UP, Usage.Consumer(0xE9)); put(VOLUME_DOWN, Usage.Consumer(0xEA)); put(VOLUME_MUTE, Usage.Consumer(0xE2))
        put(PLAY_PAUSE, Usage.Consumer(0xCD)); put(NEXT, Usage.Consumer(0xB5)); put(PREVIOUS, Usage.Consumer(0xB6)); put(STOP, Usage.Consumer(0xB7))
        put(FAST_FORWARD, Usage.Consumer(0xB3)); put(REWIND, Usage.Consumer(0xB4)); put(CHANNEL_UP, Usage.Consumer(0x9C)); put(CHANNEL_DOWN, Usage.Consumer(0x9D))
        listOf(NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9, NUM_0).forEachIndexed { i, k -> put(k, Usage.Keyboard(0x1E + i)) }
    }

    fun keyboardReport(usage: Int) = ByteArray(8).also { it[2] = usage.toByte() }          // 8 bytes, key 0
    fun keyboardRelease() = ByteArray(8)
    fun consumerReport(usage: Int) = byteArrayOf((usage and 0xFF).toByte(), (usage shr 8).toByte())
    fun consumerRelease() = ByteArray(2)
}

/** The platform's HID device (implemented on Android with BluetoothHidDevice). */
interface HidPort {
    val available: Boolean
    /** Connects to the paired host (the TV), or throws [IOException] / [StrategyException] when it must be paired first. */
    fun connect()
    fun sendReport(id: Int, data: ByteArray)
    fun close()
}

class BluetoothHidStrategy(tv: TvTarget, private val port: HidPort?, log: (String) -> Unit = {}) : BaseStrategy(tv, log) {
    override val id = StrategyIds.BT_HID
    override val label = "Bluetooth (clavier / télécommande)"
    override val status = StrategyStatus.EXPERIMENTAL
    override val capabilities = Capabilities(HidReports.USAGES.keys)
    override val limits = "La TV doit accepter un clavier Bluetooth (appairage depuis ses réglages Bluetooth). Fonctionne sans Wi-Fi ; pas de retour d'information."

    override fun applicable(fp: TvFingerprint) = port?.available == true
    override fun probe() = ProbeResult(port?.available == true, "Bluetooth HID")

    override fun connect() {
        val p = port?.takeIf { it.available } ?: failed("Bluetooth HID indisponible sur ce téléphone")
        state = StrategyState(StrategyState.Kind.CONNECTING)
        try { p.connect() } catch (e: StrategyException) { state = StrategyState(StrategyState.Kind.NEEDS_PAIRING, e.message); throw e } catch (e: IOException) { failed(e.message ?: "appairage Bluetooth requis") }
        ready()
    }

    override fun send(key: RemoteKey) {
        val u = HidReports.USAGES[key] ?: throw KeyUnsupported(key, label)
        val p = port ?: throw IOException("non connectée")
        try {
            when (u) {
                is HidReports.Usage.Keyboard -> { p.sendReport(HidReports.KEYBOARD_ID, HidReports.keyboardReport(u.usage)); p.sendReport(HidReports.KEYBOARD_ID, HidReports.keyboardRelease()) }
                is HidReports.Usage.Consumer -> { p.sendReport(HidReports.CONSUMER_ID, HidReports.consumerReport(u.usage)); p.sendReport(HidReports.CONSUMER_ID, HidReports.consumerRelease()) }
            }
        } catch (e: IOException) { state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); throw e }
    }

    override fun close() { runCatching { port?.close() }; super.close() }
}

// ------------------------------------------------------------------------------------------------------------------------
// Infrared (ConsumerIrManager). Patterns are generated from the public protocol definitions (NEC, Samsung32, Sony SIRC-12);
// the key lists come from public community code tables (LIRC remotes database, Arduino IRremote documentation): to validate on hardware.
// ------------------------------------------------------------------------------------------------------------------------
object IrCodes {
    class Signal(val carrierHz: Int, val pattern: IntArray)

    private fun bits(v: Int, n: Int, one: IntArray, zero: IntArray, out: MutableList<Int>) { for (i in 0 until n) out += if ((v shr i) and 1 == 1) one.toList() else zero.toList() }   // LSB first

    /** NEC: 9 ms + 4.5 ms header, 560 µs marks, space 560 (0) / 1690 (1); address, ~address, command, ~command. */
    fun nec(address: Int, command: Int): Signal {
        val p = mutableListOf(9000, 4500); val one = intArrayOf(560, 1690); val zero = intArrayOf(560, 560)
        bits(address, 8, one, zero, p); bits(address.inv(), 8, one, zero, p); bits(command, 8, one, zero, p); bits(command.inv(), 8, one, zero, p); p += 560
        return Signal(38_000, p.toIntArray())
    }

    /** Samsung32: 4.5 ms + 4.5 ms header, address sent twice, command, ~command. */
    fun samsung32(address: Int, command: Int): Signal {
        val p = mutableListOf(4500, 4500); val one = intArrayOf(560, 1690); val zero = intArrayOf(560, 560)
        bits(address, 8, one, zero, p); bits(address, 8, one, zero, p); bits(command, 8, one, zero, p); bits(command.inv(), 8, one, zero, p); p += 560
        return Signal(38_000, p.toIntArray())
    }

    /** Sony SIRC 12-bit: 40 kHz, 2400/600 header, 7 command bits then 5 address bits, mark 1200 (1) / 600 (0), space 600. */
    fun sirc12(address: Int, command: Int): Signal {
        val p = mutableListOf(2400, 600); val one = intArrayOf(1200, 600); val zero = intArrayOf(600, 600)
        bits(command, 7, one, zero, p); bits(address, 5, one, zero, p)
        return Signal(40_000, p.toIntArray())
    }

    private val SAMSUNG = mapOf(VOLUME_UP to 0x07, VOLUME_DOWN to 0x0B, VOLUME_MUTE to 0x0F, DPAD_UP to 0x60, DPAD_DOWN to 0x61, DPAD_LEFT to 0x65, DPAD_RIGHT to 0x62,
        DPAD_CENTER to 0x68, BACK to 0x58, MENU to 0x1A, HOME to 0x79)
    private val LG = mapOf(VOLUME_UP to 0x02, VOLUME_DOWN to 0x03, VOLUME_MUTE to 0x09, DPAD_UP to 0x40, DPAD_DOWN to 0x41, DPAD_LEFT to 0x07, DPAD_RIGHT to 0x06,
        DPAD_CENTER to 0x44, BACK to 0x28, MENU to 0x43, HOME to 0x7C)
    private val SONY = mapOf(VOLUME_UP to 0x12, VOLUME_DOWN to 0x13, VOLUME_MUTE to 0x14, DPAD_UP to 0x74, DPAD_DOWN to 0x75, DPAD_LEFT to 0x34, DPAD_RIGHT to 0x33, DPAD_CENTER to 0x65)

    fun supports(v: Vendor) = v == Vendor.SAMSUNG || v == Vendor.LG || v == Vendor.SONY

    fun keys(v: Vendor): Set<RemoteKey> = when (v) { Vendor.SAMSUNG -> SAMSUNG.keys; Vendor.LG -> LG.keys; Vendor.SONY -> SONY.keys; else -> emptySet() }

    fun signal(v: Vendor, key: RemoteKey): Signal? = when (v) {
        Vendor.SAMSUNG -> SAMSUNG[key]?.let { samsung32(0x07, it) }          // Samsung TV address 0x07
        Vendor.LG -> LG[key]?.let { nec(0x04, it) }                          // LG TV address 0x04
        Vendor.SONY -> SONY[key]?.let { sirc12(1, it) }                      // Sony TV device 1
        else -> null
    }
}

interface IrEmitter {
    val available: Boolean
    fun transmit(carrierHz: Int, pattern: IntArray)
}

class InfraredStrategy(tv: TvTarget, private val emitter: IrEmitter?, private val brand: Vendor, log: (String) -> Unit = {}) : BaseStrategy(tv, log) {
    override val id = StrategyIds.IR
    override val label = "Infrarouge (${brand.label})"
    override val status = StrategyStatus.EXPERIMENTAL
    override val capabilities = Capabilities(IrCodes.keys(brand))
    override val limits = "Le téléphone doit avoir un émetteur infrarouge et viser la TV (pas de retour d'information, pas de texte). Codes Samsung, LG et Sony seulement."

    override fun applicable(fp: TvFingerprint) = emitter?.available == true && IrCodes.supports(brand)
    override fun probe() = ProbeResult(emitter?.available == true, "émetteur IR")
    override fun connect() { if (emitter?.available != true) failed("pas d'émetteur infrarouge sur ce téléphone"); ready() }

    override fun send(key: RemoteKey) {
        val s = IrCodes.signal(brand, key) ?: throw KeyUnsupported(key, label)
        val e = emitter ?: throw IOException("non connectée")
        // SIRC repeats the frame (3 times, 45 ms apart) so that the TV accepts it; the other protocols send it once.
        repeat(if (brand == Vendor.SONY) 3 else 1) { e.transmit(s.carrierHz, s.pattern) }
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// Last resort: open the maker's own remote app installed on the phone.
// ------------------------------------------------------------------------------------------------------------------------
object VendorApps {
    /** Package names of the makers' remote apps (to verify on a real phone; the list is only used to look for an installed app). */
    val PACKAGES: Map<Vendor, List<String>> = mapOf(
        Vendor.SAMSUNG to listOf("com.samsung.android.oneconnect"),
        Vendor.LG to listOf("com.lgeha.nuts"),
        Vendor.ROKU to listOf("com.roku.remote"),
        Vendor.SONY to listOf("com.sony.tvsideview.phone"),
        Vendor.ANDROID_TV to listOf("com.google.android.apps.chromecast.app"),
    )
}

interface AppLauncher {
    fun isInstalled(pkg: String): Boolean
    /** Starts the app's launch intent; false when it cannot. */
    fun open(pkg: String): Boolean
}

class VendorAppStrategy(tv: TvTarget, private val launcher: AppLauncher?, private val vendor: Vendor, log: (String) -> Unit = {}) : BaseStrategy(tv, log) {
    override val id = StrategyIds.VENDOR_APP
    override val label = "Ouvrir l'app du fabricant"
    override val status = StrategyStatus.STABLE
    override val capabilities = Capabilities(emptySet(), apps = true)
    override val limits = "CastBridge n'envoie aucune touche : il ouvre l'application de télécommande du fabricant, si elle est installée sur ce téléphone."

    private fun installed() = VendorApps.PACKAGES[vendor].orEmpty().firstOrNull { launcher?.isInstalled(it) == true }
    override fun applicable(fp: TvFingerprint) = launcher != null && installed() != null
    override fun probe() = ProbeResult(installed() != null, installed())
    override fun connect() { if (installed() == null) failed("aucune app de télécommande de ${vendor.label} sur ce téléphone"); ready() }
    /** Opens the app (no key is sent). */
    fun open(): Boolean = installed()?.let { launcher?.open(it) } == true
    override fun send(key: RemoteKey) { throw KeyUnsupported(key, label) }
}
