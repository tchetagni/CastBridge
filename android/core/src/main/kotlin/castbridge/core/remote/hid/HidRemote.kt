package castbridge.core.remote.hid

import castbridge.core.remote.RemoteKey

/** One HID input report: [id] and the [data] bytes (without the id, as BluetoothHidDevice.sendReport wants them). */
class HidReport(val id: Int, val data: ByteArray) {
    override fun equals(other: Any?) = other is HidReport && id == other.id && data.contentEquals(other.data)
    override fun hashCode() = id * 31 + data.contentHashCode()
    override fun toString() = "HidReport($id, ${data.joinToString("") { "%02x".format(it) }})"
}

/**
 * The phone as a Bluetooth keyboard + consumer-control remote (BluetoothHidDevice, Android 9+): descriptor, key → usage
 * table and exact report bytes. Usage values: USB HID Usage Tables (public standard): keyboard page 0x07, consumer page 0x0C.
 */
object HidRemote {
    const val REPORT_KEYBOARD = 1
    const val REPORT_CONSUMER = 2
    const val SUBCLASS_REMOTE = 0xC0 // BluetoothHidDevice subclass "combo keyboard/pointing"; hosts accept it as keyboard

    /** Keyboard (report 1: modifiers, reserved, 6 keys) + Consumer Control (report 2: one 16-bit usage). */
    val DESCRIPTOR: ByteArray = intArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, REPORT_KEYBOARD,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
        0xC0,
        0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, REPORT_CONSUMER,
        0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00, 0x2A, 0xFF, 0x03, 0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
        0xC0,
    ).map { it.toByte() }.toByteArray()

    /** A key's HID code: on the keyboard page or on the consumer page. */
    sealed class Usage { data class Keyboard(val code: Int) : Usage(); data class Consumer(val code: Int) : Usage() }

    private val TABLE: Map<RemoteKey, Usage> = buildMap {
        put(RemoteKey.DPAD_RIGHT, Usage.Keyboard(0x4F)); put(RemoteKey.DPAD_LEFT, Usage.Keyboard(0x50))
        put(RemoteKey.DPAD_DOWN, Usage.Keyboard(0x51)); put(RemoteKey.DPAD_UP, Usage.Keyboard(0x52))
        put(RemoteKey.DPAD_CENTER, Usage.Keyboard(0x28)); put(RemoteKey.ENTER, Usage.Keyboard(0x28))
        put(RemoteKey.BACK, Usage.Keyboard(0x29)) // Escape: what a TV reads as Back
        put(RemoteKey.DEL, Usage.Keyboard(0x2A))
        put(RemoteKey.NUM_1, Usage.Keyboard(0x1E)); put(RemoteKey.NUM_2, Usage.Keyboard(0x1F)); put(RemoteKey.NUM_3, Usage.Keyboard(0x20))
        put(RemoteKey.NUM_4, Usage.Keyboard(0x21)); put(RemoteKey.NUM_5, Usage.Keyboard(0x22)); put(RemoteKey.NUM_6, Usage.Keyboard(0x23))
        put(RemoteKey.NUM_7, Usage.Keyboard(0x24)); put(RemoteKey.NUM_8, Usage.Keyboard(0x25)); put(RemoteKey.NUM_9, Usage.Keyboard(0x26))
        put(RemoteKey.NUM_0, Usage.Keyboard(0x27))
        put(RemoteKey.HOME, Usage.Consumer(0x0223)) // AC Home
        put(RemoteKey.MENU, Usage.Consumer(0x0040))
        put(RemoteKey.PLAY_PAUSE, Usage.Consumer(0xCD)); put(RemoteKey.PLAY, Usage.Consumer(0xB0)); put(RemoteKey.PAUSE, Usage.Consumer(0xB1))
        put(RemoteKey.STOP, Usage.Consumer(0xB7)); put(RemoteKey.NEXT, Usage.Consumer(0xB5)); put(RemoteKey.PREVIOUS, Usage.Consumer(0xB6))
        put(RemoteKey.REWIND, Usage.Consumer(0xB4)); put(RemoteKey.FAST_FORWARD, Usage.Consumer(0xB3))
        put(RemoteKey.VOLUME_UP, Usage.Consumer(0xE9)); put(RemoteKey.VOLUME_DOWN, Usage.Consumer(0xEA)); put(RemoteKey.VOLUME_MUTE, Usage.Consumer(0xE2))
        put(RemoteKey.CHANNEL_UP, Usage.Consumer(0x9C)); put(RemoteKey.CHANNEL_DOWN, Usage.Consumer(0x9D))
        put(RemoteKey.CAPTIONS, Usage.Consumer(0x61)); put(RemoteKey.GUIDE, Usage.Consumer(0x8D))
    }
    /** TAB (keyboard 0x2B) has no [RemoteKey]; kept for the screen's own use. */
    const val KEYBOARD_TAB = 0x2B

    fun usage(k: RemoteKey): Usage? = TABLE[k]
    fun supports(k: RemoteKey) = k in TABLE
    val supportedKeys: List<RemoteKey> get() = RemoteKey.values().filter { it in TABLE }

    fun press(k: RemoteKey): HidReport? = when (val u = TABLE[k]) {
        is Usage.Keyboard -> HidReport(REPORT_KEYBOARD, byteArrayOf(0, 0, u.code.toByte(), 0, 0, 0, 0, 0))
        is Usage.Consumer -> HidReport(REPORT_CONSUMER, byteArrayOf((u.code and 0xff).toByte(), (u.code ushr 8).toByte()))
        null -> null
    }

    /** The "all keys up" report of the same kind as [k]. */
    fun release(k: RemoteKey): HidReport? = when (TABLE[k]) {
        is Usage.Keyboard -> HidReport(REPORT_KEYBOARD, ByteArray(8))
        is Usage.Consumer -> HidReport(REPORT_CONSUMER, ByteArray(2))
        null -> null
    }
}

/** Sends reports to the connected host (BluetoothHidDevice on Android; a fake in tests). */
interface HidTransport {
    val connected: Boolean
    fun send(report: HidReport): Boolean
}

/**
 * Press/release bookkeeping on top of a [HidTransport]: a second press of a held key and a release of a key that is not held
 * send nothing (idempotent), and only one key is held at a time per report kind.
 */
class HidKeySender(private val transport: HidTransport) {
    private val held = HashMap<Int, RemoteKey>()

    @Synchronized fun down(k: RemoteKey): Boolean {
        val r = HidRemote.press(k) ?: return false
        if (!transport.connected) return false
        if (held[r.id] == k) return true
        held[r.id]?.let { HidRemote.release(it)?.let(transport::send) }
        held[r.id] = k
        return transport.send(r)
    }

    @Synchronized fun up(k: RemoteKey): Boolean {
        val r = HidRemote.release(k) ?: return false
        if (held[r.id] != k) return true
        held.remove(r.id)
        return transport.connected && transport.send(r)
    }

    /** A full tap: down then up. */
    fun tap(k: RemoteKey): Boolean = down(k) && up(k)

    @Synchronized fun releaseAll() { held.values.toList().forEach { HidRemote.release(it)?.let(transport::send) }; held.clear() }
}
