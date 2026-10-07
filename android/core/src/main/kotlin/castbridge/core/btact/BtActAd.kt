package castbridge.core.btact

import castbridge.core.tv.BtProtocol
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What the TV advertises over Bluetooth Low Energy while its activation screen is open, and what the phone looks for (« activer par Bluetooth sans appairage », `docs/BT-PLUG-AND-PLAY.md`).
 * ONE legacy advertising packet, 30 bytes of the 31 that any BLE controller able to advertise can carry:
 *  - flags (3) + the complete 128-bit service UUID list `…0008` ([SERVICE_UUID], 18) = [ADVERTISEMENT_BYTES] 21 bytes: what the phone's scan filters on;
 *  - one manufacturer-specific record ([RECORD_BYTES] 9 bytes): company id [COMPANY_ID] (0xFFFF, the value the Bluetooth SIG reserves for tests and internal use: no company is impersonated) and a 5-byte
 *    payload `version u8 | tag[2] | psm u16 big endian`. The PSM is the dynamic port of the TV's insecure L2CAP channel (0 = none: RFCOMM only).
 * The record rides in the SAME packet as the UUID on purpose: a scan filter on a service UUID that the controller applies in hardware matches the packet that carries the UUID, and a record that came in a
 * separate scan response can be dropped by that filter. A controller that refuses 30 bytes (`ADVERTISE_FAILED_DATA_TOO_LARGE`) gets the fallback layout, the record in the scan response
 * ([SCAN_RESPONSE_BYTES]); the phone reads the record from the merged scan record either way and ignores an advertisement that does not carry it (yet).
 *
 * The TAG ([tag]) is the first [TAG_BYTES] bytes of an HMAC of the connection code: it lets a phone that typed a code skip the TVs of the neighbours (a shop with ten locked TVs) instead of trying
 * the code on each, which would count a wrong code on every one of them. It is public: a listener can compute the tag of any of the 10^6 codes. So it is NOT the code and it opens nothing (a
 * connection still needs the PAKE), but it is not free either: **16 bits of a ~20-bit code leak, which leaves about 15 candidates**; the TV's gate (5 wrong codes lock a peer, 20 in 10 minutes close
 * the way in) is what bounds a guess between those 15. One byte instead of two would leave ~3 900 candidates and one false match in 256 neighbours; the length is one constant ([TAG_BYTES]).
 * A tag that never changes during the screen leaks no more than that one time; a rotating tag would leak more with every packet heard, which is why it does not rotate.
 */
object BtActAd {
    /** The BLE service UUID the TV advertises: the table's service `…0008` (never a literal UUID elsewhere, `BtServicesTest`). */
    const val SERVICE_UUID = BtProtocol.ACTIVATION_SERVICE_UUID
    /** 0xFFFF: reserved by the Bluetooth SIG for tests and internal use. */
    const val COMPANY_ID = 0xFFFF
    const val TAG_BYTES = 2
    const val AD_VERSION = 1
    const val PAYLOAD_BYTES = 1 + TAG_BYTES + 2
    private const val KEY = "castbridge-bt-activation-v1/advert"

    /** The first [TAG_BYTES] bytes of HMAC-SHA256(key = a public label, message = the 6-digit [code]). */
    fun tag(code: String): ByteArray {
        require(castbridge.core.tv.WdCode.isValid(code)) { "code de connexion : 6 chiffres attendus" }
        return Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(KEY.toByteArray(Charsets.UTF_8), "HmacSHA256")); doFinal(code.toByteArray(Charsets.US_ASCII)) }.copyOf(TAG_BYTES)
    }

    /** The payload of the manufacturer record: `version | tag | psm` ([psm] 0 = no L2CAP channel). */
    fun payload(code: String, psm: Int): ByteArray {
        require(psm in 0..0xFFFF) { "PSM hors limites" }
        return byteArrayOf(AD_VERSION.toByte()) + tag(code) + byteArrayOf((psm shr 8).toByte(), psm.toByte())
    }

    class Parsed(val version: Int, val tag: ByteArray, val psm: Int) {
        override fun toString() = "Parsed(v=$version, psm=$psm)"
    }

    /** What a manufacturer record holds, or null when it is not ours (wrong length, wrong version): never a guess. */
    fun parse(payload: ByteArray?): Parsed? {
        if (payload == null || payload.size != PAYLOAD_BYTES) return null
        val version = payload[0].toInt() and 0xFF
        if (version != AD_VERSION) return null
        return Parsed(version, payload.copyOfRange(1, 1 + TAG_BYTES), ((payload[1 + TAG_BYTES].toInt() and 0xFF) shl 8) or (payload[2 + TAG_BYTES].toInt() and 0xFF))
    }

    /** Is this advertisement one a phone that typed [code] may try? Only when the tag is the one of that code. */
    fun matches(parsed: Parsed?, code: String): Boolean = parsed != null && castbridge.core.btact.BtActKeys.same(parsed.tag, tag(code))

    // ---- sizes, for the 31-byte limit of legacy advertising (checked by the tests; Android adds the 3 bytes of flags itself) ----

    /** Flags (3) + one complete 128-bit service UUID list (2 + 16). */
    const val ADVERTISEMENT_BYTES = 3 + 2 + 16
    /** One manufacturer-specific record: length, type, company id (2), payload. */
    const val RECORD_BYTES = 1 + 1 + 2 + PAYLOAD_BYTES
    /** The packet the TV sends: the UUID and the record together (30 of 31 bytes). */
    const val SINGLE_PACKET_BYTES = ADVERTISEMENT_BYTES + RECORD_BYTES
    /** The fallback layout puts the record alone in the scan response. */
    const val SCAN_RESPONSE_BYTES = RECORD_BYTES
}
