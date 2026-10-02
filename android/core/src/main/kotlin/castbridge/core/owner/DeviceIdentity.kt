package castbridge.core.owner

import java.security.MessageDigest

/**
 * Identity of a TV without any single point of failure (docs/TRIAL-EDITION.md § Identité de l'appareil). Some TVs have no Ethernet,
 * the reference TV has a replaceable USB Wi-Fi module: no factor is mandatory, each one is hashed on its own, and the activation is
 * accepted when at least k of its n factors match (a replaced module does not lock the owner out).
 */
enum class FactorKind(val bit: Int, val strong: Boolean) {
    /** Serial + CID of the internal flash (/sys/block/mmcblk0/device/serial, cid): soldered, the most stable. */
    FLASH(0, true),
    /** Ethernet MAC, only if the TV has one. */
    ETHERNET(1, true),
    /** Wi-Fi MAC, ONLY if the interface sits on a soldered bus (sdio, mmc, pci, platform): never over USB (replaceable module). */
    WIFI(2, false),
    /** ro.serialno. */
    SYSTEM_SERIAL(3, false),
    /** Bluetooth address when readable. */
    BLUETOOTH(4, false),
}

/** What the TV app could read; anything unreadable is null. Android collection lives in the receiver; this class is pure. */
data class RawFactors(
    val flashSerial: String? = null, val flashCid: String? = null, val ethernetMac: String? = null,
    val wifiMac: String? = null, val wifiSysfsPath: String? = null, val systemSerial: String? = null, val bluetoothAddress: String? = null,
)

/** The hashed factors of a TV: kind -> 32 hex chars (16 bytes of SHA-256 over a CastBridge salt, the kind and the value). */
class Fingerprints(factors: Map<FactorKind, String>) {
    val byKind: Map<FactorKind, String> = factors.toSortedMap()
    val n get() = byKind.size
    val mask: Int get() = byKind.keys.fold(0) { m, k -> m or (1 shl k.bit) }

    /** Stable hash of the whole set (the basis of the readable device code). */
    fun setHash(): ByteArray = MessageDigest.getInstance("SHA-256").digest(
        byKind.entries.joinToString("\n") { "${it.key.name}=${it.value}" }.toByteArray(Charsets.UTF_8))

    override fun equals(other: Any?) = other is Fingerprints && other.byKind == byKind
    override fun hashCode() = byKind.hashCode()
}

object DeviceIdentity {
    const val SALT = "castbridge-device-v1"
    private val PLACEHOLDER = Regex("^(0+|f+|x+|unknown|null|none|n/a|default string|not specified|123456789abcdef0?|0123456789abcdef)$")

    /** A Wi-Fi interface behind USB is a removable module (the reference TV): excluded; sdio/mmc/pci/platform = soldered: included. Unknown path: excluded. */
    fun wifiBusIsSoldered(sysfsPath: String?): Boolean {
        val p = sysfsPath?.lowercase() ?: return false
        if ("/usb" in p) return false
        return listOf("/sdio", "/mmc", "/pci", "/platform").any { it in p }
    }

    private fun clean(v: String?): String? {
        val s = v?.trim()?.lowercase()?.replace(":", "")?.replace("-", "") ?: return null
        if (s.isEmpty() || PLACEHOLDER.matches(s) || s == "020000000000") return null   // Android hides the MAC as 02:00:00:00:00:00
        return s
    }

    private fun hash(kind: FactorKind, value: String): String =
        MessageDigest.getInstance("SHA-256").digest("$SALT|${kind.name}|$value".toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }

    fun fingerprints(raw: RawFactors): Fingerprints {
        val m = LinkedHashMap<FactorKind, String>()
        val flash = listOfNotNull(clean(raw.flashSerial), clean(raw.flashCid)).joinToString("+")
        if (flash.isNotEmpty()) m[FactorKind.FLASH] = hash(FactorKind.FLASH, flash)
        clean(raw.ethernetMac)?.let { m[FactorKind.ETHERNET] = hash(FactorKind.ETHERNET, it) }
        if (wifiBusIsSoldered(raw.wifiSysfsPath)) clean(raw.wifiMac)?.let { m[FactorKind.WIFI] = hash(FactorKind.WIFI, it) }
        clean(raw.systemSerial)?.let { m[FactorKind.SYSTEM_SERIAL] = hash(FactorKind.SYSTEM_SERIAL, it) }
        clean(raw.bluetoothAddress)?.let { m[FactorKind.BLUETOOTH] = hash(FactorKind.BLUETOOTH, it) }
        return Fingerprints(m)
    }

    /**
     * k of n: with n >= 3 factors one may change (a module swapped, a flash serial unreadable after an update): k = n - 1; with 1 or 2 factors
     * every factor must match (k = n), because tolerating a mismatch there would leave a single factor to guess. Justification in docs/TRIAL-EDITION.md.
     */
    fun kFor(n: Int): Int = if (n >= 3) n - 1 else maxOf(n, 1)

    /** The weak-identity case: only the weak factors exist (no flash, no Ethernet). The console may still activate, but says so. */
    fun isWeak(fp: Fingerprints) = fp.byKind.keys.none { it.strong }

    /**
     * Does [device] satisfy an activation made for [factors] with threshold [k]? At least k equal factors, and, if the activation
     * names a strong (soldered) factor, at least one strong factor must be among the matches (two swapped weak parts are not the same TV).
     */
    fun matches(factors: Map<FactorKind, String>, k: Int, device: Fingerprints): Boolean {
        if (factors.isEmpty() || k < 1) return false
        val hit = factors.filter { (kind, fp) -> device.byKind[kind] == fp }.keys
        if (hit.size < k) return false
        return factors.keys.none { it.strong } || hit.any { it.strong }
    }
}

/** Crockford Base32 (no I, L, O, U): typed by hand, so O reads as 0 and I / L as 1. */
object Base32C {
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    fun value(c: Char): Int = when (val u = c.uppercaseChar()) { 'O' -> 0; 'I', 'L' -> 1; else -> ALPHABET.indexOf(u) }

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(); var buf = 0; var bits = 0
        for (b in bytes) { buf = (buf shl 8) or (b.toInt() and 0xff); bits += 8; while (bits >= 5) { bits -= 5; sb.append(ALPHABET[(buf shr bits) and 31]) } ; buf = buf and ((1 shl bits) - 1) }
        if (bits > 0) sb.append(ALPHABET[(buf shl (5 - bits)) and 31])
        return sb.toString()
    }

    /** [byteCount] bytes (the extra bits of the last char are ignored); null if a char is not Base32 or the text is too short. */
    fun decode(text: String, byteCount: Int): ByteArray? {
        val out = ByteArray(byteCount); var buf = 0; var bits = 0; var n = 0
        for (c in text) {
            val v = value(c); if (v < 0) return null
            buf = (buf shl 5) or v; bits += 5
            if (bits >= 8) { bits -= 8; if (n < byteCount) out[n++] = ((buf shr bits) and 0xff).toByte(); buf = buf and ((1 shl bits) - 1) }
        }
        return if (n == byteCount) out else null
    }

    /** Check character: weighted sum with odd weights (every single typo is caught; a swap is missed only for two chars exactly 16 apart). */
    fun check(chars: String, salt: Int = 0): Char = ALPHABET[(chars.withIndex().sumOf { (i, c) -> value(c) * (2 * i + 1) } + salt * 7) and 31]
}

/**
 * The readable device code "XXXX-XXXX-XXXX-XXXX" (16 chars): 1 char = which factors were used (a 5-bit mask), 14 chars = 70 bits of
 * the hash of the factor set, 1 check char. It names the TV for a human and for the console; the activation itself signs the full
 * set of fingerprints (see [DeviceIdentity.matches]).
 */
object DeviceCode {
    fun of(fp: Fingerprints): String {
        val hash = Base32C.encode(fp.setHash()).take(14)
        val body = Base32C.ALPHABET[fp.mask and 31].toString() + hash
        return format(body + Base32C.check(body))
    }

    private fun format(s: String) = s.chunked(4).joinToString("-")

    /**
     * Live formatting while a code is TYPED: dashes every 4 characters, upper case, spaces and typed dashes ignored, at most 16 characters. Text that is not a bare code is returned as it is
     * (a pasted device request has « = » or several lines, and an owner may type anything else): so the field accepts both the code and the full request.
     */
    fun typing(text: String): String {
        if (text.any { it == '=' || it == '\n' || it == '\r' }) return text
        val raw = text.filter { it != '-' && !it.isWhitespace() }
        if (raw.isEmpty() || !raw.all { it.isLetterOrDigit() && it.code < 128 }) return text
        return format(raw.uppercase().take(16))
    }

    /** Normalised "XXXX-XXXX-XXXX-XXXX" or null (wrong length, bad char, bad check). Accepts lower case, spaces and the O/0, I/1 confusions. */
    fun parse(text: String): String? {
        val s = text.filter { it != '-' && !it.isWhitespace() }.map { c -> val v = Base32C.value(c); if (v < 0) return null else Base32C.ALPHABET[v] }.joinToString("")
        if (s.length != 16 || Base32C.check(s.take(15)) != s[15]) return null
        return format(s)
    }

    /** The factors named by the code's first char. */
    fun kindsOf(code: String): Set<FactorKind> {
        val mask = parse(code)?.replace("-", "")?.let { Base32C.value(it[0]) } ?: return emptySet()
        return FactorKind.values().filter { mask and (1 shl it.bit) != 0 }.toSet()
    }
}
