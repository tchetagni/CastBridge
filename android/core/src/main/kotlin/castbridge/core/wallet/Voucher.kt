package castbridge.core.wallet

import castbridge.core.owner.DeviceCode
import castbridge.core.update.Ed25519
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Base64

/** Anneau des clés de BONS (hors ligne, propriétaire) : [kidByIndex] donne le `kid` de l'anneau pour l'« index de clé » du bon (1 octet). Compilé dans l'APK, jamais lu du réseau. */
class VoucherKeys(val ring: castbridge.core.owner.KeyRing, val kidByIndex: Map<Int, String>)

/**
 * Bon hors ligne `cbv1` : 27 octets signés (domaine `castbridge-wallet-voucher-v1`) + signature Ed25519 de 64 octets = 91 octets (conception W22 § 3.4).
 *
 * ```
 * 0      version (1)
 * 1      index de clé (1..255)
 * 2      monnaie (1 = NDEM, 2 = MBOKO)
 * 3..6   montant, u32 gros-boutiste, ≥ 1
 * 7..16  nonce (10 octets)
 * 17..18 échéance, u16 gros-boutiste : jours depuis 2026-01-01 UTC ; valable jusqu'à la fin de ce jour (inclus)
 * 19..26 cible : 0 = toute TV, sinon SHA-256(code d'appareil normalisé « XXXX-XXXX-XXXX-XXXX », UTF-8)[0..8]
 * 27..90 signature sur  "castbridge-wallet-voucher-v1\n" (ASCII) ‖ les 27 octets ci-dessus
 * ```
 * Un bon MBOKO exige une cible (jamais « toute TV »). Un bon vérifié n'est PAS un solde : la TV le garde « en attente » ([WalletCache]) jusqu'à confirmation par le serveur.
 */
class Voucher(val version: Int, val keyIndex: Int, val currency: WalletCurrency, val amount: Long, val nonceHex: String, val expiryDay: Int, val targetHex: String, val signature: ByteArray) {
    init {
        require(version in 0..255 && keyIndex in 0..255 && amount in 0..0xffff_ffffL && expiryDay in 0..0xffff)
        require(nonceHex.length == 20 && HEX.matches(nonceHex) && targetHex.length == 16 && HEX.matches(targetHex) && signature.size == 64)
    }

    val isAnyTv: Boolean get() = targetHex == ANY_TARGET

    /** Les 27 octets signés. */
    fun body(): ByteArray = bodyOf(version, keyIndex, currency, amount, nonceHex, expiryDay, targetHex)

    /** Les 91 octets. */
    fun encode(): ByteArray = body() + signature

    /** Instant (ms, UTC) à partir duquel le bon est échu : minuit qui suit le jour d'échéance. */
    val expiresAtMs: Long get() = (EPOCH_DAY + expiryDay + 1L) * DAY_MS

    /** Signature valide d'une clé de [keys] pour cet index (aucun autre contrôle : ni échéance, ni cible). */
    fun signatureValid(keys: VoucherKeys): Boolean {
        val kid = keys.kidByIndex[keyIndex] ?: return false
        val key = keys.ring.find(kid) ?: return false
        if (keys.ring.isRevoked(kid)) return false
        val pub = runCatching { Base64.getDecoder().decode(key.publicKeyBase64.trim()) }.getOrNull() ?: return false
        return Ed25519.verify(pub, signedMessage(body()), signature)
    }

    companion object {
        const val DOMAIN = "castbridge-wallet-voucher-v1"
        const val BODY_SIZE = 27
        const val SIZE = 91
        const val ANY_TARGET = "0000000000000000"
        private const val DAY_MS = 86_400_000L
        /** Jour 0 de l'échéance : 2026-01-01 (jours depuis 1970). */
        val EPOCH_DAY: Long = LocalDate.of(2026, 1, 1).toEpochDay()
        private val HEX = Regex("^[0-9a-f]+$")

        fun signedMessage(body: ByteArray): ByteArray = "$DOMAIN\n".toByteArray(Charsets.US_ASCII) + body

        fun bodyOf(version: Int, keyIndex: Int, currency: WalletCurrency, amount: Long, nonceHex: String, expiryDay: Int, targetHex: String): ByteArray {
            val b = ByteArray(BODY_SIZE)
            b[0] = version.toByte(); b[1] = keyIndex.toByte(); b[2] = (currency.ordinal + 1).toByte()
            for (i in 0 until 4) b[3 + i] = (amount shr (8 * (3 - i))).toByte()
            hex(nonceHex).copyInto(b, 7)
            b[17] = (expiryDay shr 8).toByte(); b[18] = expiryDay.toByte()
            hex(targetHex).copyInto(b, 19)
            return b
        }

        /** Lecture STRICTE de 91 octets exactement ; null si la longueur ou la monnaie est invalide (la signature n'est pas contrôlée ici : voir [verify]). */
        fun decode(bytes: ByteArray): Voucher? {
            if (bytes.size != SIZE) return null
            val cur = WalletCurrency.values().getOrNull((bytes[2].toInt() and 0xff) - 1) ?: return null
            var amount = 0L
            for (i in 0 until 4) amount = (amount shl 8) or (bytes[3 + i].toLong() and 0xff)
            return Voucher(bytes[0].toInt() and 0xff, bytes[1].toInt() and 0xff, cur, amount, hexOf(bytes, 7, 10),
                ((bytes[17].toInt() and 0xff) shl 8) or (bytes[18].toInt() and 0xff), hexOf(bytes, 19, 8), bytes.copyOfRange(27, 91))
        }

        /** Cible d'un bon pour la TV de code [deviceCode] : 8 premiers octets (16 hex) de SHA-256 du code normalisé ; null si le code n'est pas un code d'appareil valide. */
        fun targetOf(deviceCode: String): String? {
            val norm = DeviceCode.parse(deviceCode) ?: return null
            return hexOf(MessageDigest.getInstance("SHA-256").digest(norm.toByteArray(Charsets.UTF_8)), 0, 8)
        }

        /**
         * Contrôles dans cet ordre : version, horloge ([nowMs] = temps de la TV, `TvClock.now` choisi par l'appelant ; ≤ 0 ⇒ [WalletRefusal.CLOCK]), clé et signature, MBOKO « toute TV »,
         * échéance, cible. Tout refus de forme ou de signature est [WalletRefusal.VOUCHER_BAD] (aucun détail à un tricheur).
         */
        fun verify(voucher: Voucher, keys: VoucherKeys, deviceCode: String, nowMs: Long): Verdict<Voucher> {
            if (voucher.version != 1) return Verdict.Rejected(WalletRefusal.VOUCHER_BAD)
            if (nowMs <= 0) return Verdict.Rejected(WalletRefusal.CLOCK)
            if (voucher.amount < 1 || !voucher.signatureValid(keys)) return Verdict.Rejected(WalletRefusal.VOUCHER_BAD)
            if (voucher.currency == WalletCurrency.MBOKO && voucher.isAnyTv) return Verdict.Rejected(WalletRefusal.VOUCHER_BAD)
            if (nowMs >= voucher.expiresAtMs) return Verdict.Rejected(WalletRefusal.VOUCHER_EXPIRED)
            if (!voucher.isAnyTv && voucher.targetHex != targetOf(deviceCode)) return Verdict.Rejected(WalletRefusal.VOUCHER_OTHER_TV)
            return Verdict.Accepted(voucher)
        }

        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
        private fun hexOf(b: ByteArray, off: Int, len: Int) = (off until off + len).joinToString("") { "%02x".format(b[it].toInt() and 0xff) }
    }
}
