package castbridge.core.owner

import castbridge.core.lots.Right
import castbridge.core.lots.LotId
import castbridge.core.update.Ed25519
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** Signs bytes with ONE key. Each tool (desk, owner phone, server) has its own [Signer]: the keys are never shared (docs/ACTIVATION-FORMAT.md § Clés et portées). */
interface Signer {
    val keyId: String
    fun sign(message: ByteArray): ByteArray
}

/** Ed25519 from a 32-byte seed with the JDK (Java 17+, deterministic RFC 8032 signatures: the same input always gives the same bytes, which the test vectors rely on). */
class Ed25519Signer(seed: ByteArray) : Signer {
    private val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(PKCS8_PREFIX + seed))
    val publicKey: ByteArray = Ed25519.publicKey(seed)
    val publicKeyBase64: String = Base64.getEncoder().encodeToString(publicKey)
    override val keyId: String = KeyRing.idOf(publicKeyBase64)
    override fun sign(message: ByteArray): ByteArray = Signature.getInstance("Ed25519").run { initSign(privateKey); update(message); sign() }
    fun trusted(scopes: Set<KeyScope> = KeyScope.ALL) = TrustedKey(keyId, publicKeyBase64, scopes)

    companion object {
        private val PKCS8_PREFIX = byteArrayOf(0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20)
    }
}

/** An input the issuer refuses (malformed device code, duration out of bounds, unknown right, scope the key does not have…). The message is in French for the screen. */
class IssueException(message: String) : IllegalArgumentException(message)

/** Seat identifiers: a seat is "this licence on this hardware". */
object SeatIds {
    /** First 8 bytes (16 hex) of SHA-256("castbridge-seat|<licence>|<hex of the factor-set hash>"). A new seat only; an existing seat keeps its id (registry). */
    fun of(license: String, fp: Fingerprints): String =
        MessageDigest.getInstance("SHA-256").digest("castbridge-seat|$license|${fp.setHash().joinToString("") { "%02x".format(it) }}".toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
}

/**
 * The ONE issuing library of the three tools (desk application, owner phone, server): same inputs, same bytes (docs/ACTIVATION-FORMAT.md, tools/activation/test-vectors.json).
 * It refuses any invalid input and any right the key's [scopes] do not allow, so a tool cannot produce a token the device would refuse for a reason known in advance.
 */
class ActivationIssuer(private val signer: Signer, private val scopes: Set<KeyScope> = KeyScope.ALL, private val random: SecureRandom = SecureRandom()) {
    companion object {
        /** An activation can be installed during 48 h from its creation (docs/ACTIVATION-FORMAT.md); only a key with ISSUE_UNLIMITED may issue an unlimited one. */
        const val MAX_WINDOW_HOURS = ActivationPolicy.CODE_VALIDITY_HOURS
        const val MAX_OPEN_ALL_DAYS = 30
        const val MAX_GRACE_DAYS = 30
        private const val DAY = 24L * 3600 * 1000
        private const val HOUR = ActivationPolicy.HOUR_MS
    }

    data class Request(
        val kind: ActivationKind, val deviceCode: String, val factors: Fingerprints, val issuedAt: Long,
        val subject: Subject = Subject.TV, val rights: List<Right> = emptyList(), val license: String = Activation.TRIAL_LICENSE, val seat: String? = null,
        /** Start of the install window (default [issuedAt]) and its length in hours (1 to 48, default 48), or [unlimited] (needs the ISSUE_UNLIMITED scope). */
        val notBefore: Long = issuedAt, val windowHours: Int = MAX_WINDOW_HOURS, val unlimited: Boolean = false, val nonce: String? = null, val k: Int? = null,
        /** The key's sequence number (a persistent counter per tool is best); defaults to [issuedAt]. */
        val seq: Long? = null,
    )

    /** An issued activation with every encoding the three channels need. */
    class Issued(val activation: Activation) {
        val token: String = activation.encode()
        /** The content of the `activation` file for Download/CastBridge on the USB drive (one line + newline). */
        val fileName: String get() = Activation.FILE_NAME
        val fileContent: String get() = token + "\n"
        /** The payload of the owner Bluetooth channel's ACTIVATION frame. */
        val bluetoothFrame: ByteArray get() = OwnerFrames.encode(OwnerFrames.ACTIVATION, token)
        /** The same token as groups of 5 (4 data + 1 check character): checked group by group, very long (see [GroupedText]); the compact key is the practical manual path. */
        val groupedText: String get() = GroupedText.encode(token.toByteArray(Charsets.US_ASCII))
    }

    private fun need(ok: Boolean, why: String) { if (!ok) throw IssueException(why) }

    fun issue(r: Request): Issued {
        val code = DeviceCode.parse(r.deviceCode) ?: throw IssueException("Code d'appareil mal formé")
        need(code == DeviceCode.of(r.factors), "Le code d'appareil ne correspond pas aux empreintes de facteurs fournies")
        need(r.factors.n >= 1, "Aucun facteur d'identité")
        if (r.unlimited) need(KeyScope.ISSUE_UNLIMITED in scopes, "Cette clé n'a pas le droit de délivrer une activation illimitée")
        else need(r.windowHours in 1..MAX_WINDOW_HOURS, "Fenêtre d'installation hors bornes (1 à $MAX_WINDOW_HOURS heures)")
        need(r.issuedAt > 0 && r.notBefore > 0, "Date invalide")
        need(if (r.kind == ActivationKind.TRIAL) KeyScope.ISSUE_TRIAL in scopes else (KeyScope.ISSUE_PRODUCTION in scopes || KeyScope.REACTIVATE in scopes), "Cette clé n'a pas le droit de délivrer ce type d'activation")
        if (r.kind == ActivationKind.TRIAL) {
            need(r.rights.isEmpty(), "Une clé d'essai ne porte aucun droit")
            need(r.license == Activation.TRIAL_LICENSE, "Une clé d'essai porte la licence « trial »")
        } else {
            need(Activation.ID.matches(r.license) && r.license != Activation.TRIAL_LICENSE, "Identifiant de licence invalide")
            need(r.rights.isNotEmpty(), "Une activation de production porte au moins un droit")
        }
        r.rights.forEach(::checkRight)
        val nonce = r.nonce ?: ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        need(Activation.HEX.matches(nonce), "Nonce invalide")
        val k = r.k ?: DeviceIdentity.kFor(r.factors.n)
        need(k in 1..r.factors.n, "k hors bornes")
        val seat = r.seat ?: SeatIds.of(r.license, r.factors)
        need(Activation.HEX.matches(seat), "Identifiant de poste invalide")
        val notAfter = if (r.unlimited) ActivationPolicy.UNLIMITED_NOT_AFTER else r.notBefore + r.windowHours * HOUR
        val unsigned = Activation(r.kind, r.subject, signer.keyId, r.seq ?: r.issuedAt, nonce, r.issuedAt, r.notBefore, notAfter, r.license, seat, k, r.factors.byKind, r.rights, "")
        val sig = Base64.getEncoder().encodeToString(signer.sign(unsigned.canonicalPayload().toByteArray(Charsets.UTF_8)))
        return Issued(unsigned.copy(signature = sig))
    }

    private fun checkRight(r: Right) {
        need(Activation.ID.matches(r.productId), "Identifiant de produit invalide : ${r.productId}")
        need(r.bundleIds.isNotEmpty() && r.bundleIds.all { Activation.ID.matches(it) }, "Bouquet invalide dans le droit ${r.productId}")
        when (r) {
            is Right.Purchase -> need(r.grantedAt > 0, "Date d'achat invalide")
            is Right.Subscription -> {
                need(r.endsAt > r.startsAt, "Abonnement : fin avant le début")
                need(r.graceMs in 0..MAX_GRACE_DAYS * DAY, "Abonnement : tolérance hors bornes")
            }
            is Right.OpenAll -> {
                need(KeyScope.COMMAND_OPEN_ALL in scopes, "Cette clé ne peut pas délivrer « tout ouvert »")
                need(r.endsAt > r.startsAt && r.endsAt - r.startsAt <= MAX_OPEN_ALL_DAYS * DAY, "« Tout ouvert » : 30 jours au plus")
            }
        }
    }

    /** The compact key for manual typing (trial or production product set), strictly bound to the device code. */
    fun issueCompact(kind: ActivationKind, deviceCode: String, notBeforeHour: Int, windowHours: Int = MAX_WINDOW_HOURS, setId: Int = 0, unlimited: Boolean = false): String {
        val code = DeviceCode.parse(deviceCode) ?: throw IssueException("Code d'appareil mal formé")
        need(notBeforeHour in 0..0xfffe && setId in 0..0xffff && (unlimited || windowHours in 1..MAX_WINDOW_HOURS), "Champ hors bornes (la date de départ tient sur 16 bits d'heures : jusqu'en 2033)")
        if (unlimited) need(KeyScope.ISSUE_UNLIMITED in scopes, "Cette clé n'a pas le droit de délivrer une clé illimitée")
        need(if (kind == ActivationKind.TRIAL) KeyScope.ISSUE_TRIAL in scopes else KeyScope.ISSUE_PRODUCTION in scopes, "Cette clé n'a pas ce droit")
        need(kind == ActivationKind.PRODUCTION || setId == 0, "La clé d'essai porte l'ensemble 0")
        val h = CompactActivation.Header(kind, CompactActivation.keyTag(signer.keyId), notBeforeHour, if (unlimited) ActivationPolicy.UNLIMITED_UNITS else windowHours, setId, CompactActivation.bindOf(code))
        return CompactActivation.encode(h, signer.sign(CompactActivation.signedText(h.bytes()).toByteArray(Charsets.UTF_8)))
    }

    /** An owner command for one TV; [challenge] is the one the TV issued over Bluetooth a moment ago. */
    fun issueCommand(power: Power, factors: Fingerprints, challenge: String, issuedAt: Long, days: Int = 0, action: String = "", bundleIds: List<String> = emptyList(), lots: List<LotId> = emptyList(), k: Int? = null, seq: Long = issuedAt): String {
        need(KeyScope.of(power) in scopes, "Cette clé n'a pas ce pouvoir")
        need(Regex("^[0-9a-f]{16,64}$").matches(challenge), "Défi invalide")
        need(factors.n >= 1, "Aucun facteur d'identité")
        when (power) {
            Power.SUPPORT -> need(action in OwnerCommand.SUPPORT_ACTIONS && days == 0, "Action de support invalide")
            Power.UNLOCK -> need(days in 1..power.maxDays && (bundleIds.isNotEmpty() || lots.isNotEmpty()) && bundleIds.all { Activation.ID.matches(it) }, "Déblocage invalide (1 à ${power.maxDays} jours, contenu obligatoire)")
            Power.OPEN_ALL -> need(days in 1..power.maxDays, "« Tout ouvert » : 1 à ${power.maxDays} jours")
        }
        val kk = k ?: DeviceIdentity.kFor(factors.n)
        need(issuedAt > 0, "Date invalide")
        val payload = OwnerCommand.payload(signer.keyId, seq, issuedAt, power, action, challenge, kk, factors.byKind, bundleIds, lots, days)
        val sig = Base64.getEncoder().encodeToString(signer.sign(payload.toByteArray(Charsets.UTF_8)))
        return OwnerCommand(signer.keyId, seq, issuedAt, power, action, challenge, kk, factors.byKind, bundleIds, lots, days, sig).encode()
    }
}

/**
 * Any bytes as groups of 5 Crockford Base32 characters (4 data + 1 check each, the check also depends on the group's position), written with dashes. A 2-byte length
 * prefix tells where the data stops. The same group check as [CompactActivation]; the TV (or the tool) names the first mistyped group.
 */
object GroupedText {
    fun encode(bytes: ByteArray): String {
        require(bytes.size <= 0xffff)
        val raw = Base32C.encode(byteArrayOf((bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes)
        val padded = raw.padEnd((raw.length + 3) / 4 * 4, '0')
        return padded.chunked(4).mapIndexed { i, g -> g + Base32C.check(g, salt = i + 1) }.joinToString("-")
    }

    sealed class Decoded {
        data class Ok(val bytes: ByteArray) : Decoded()
        data class BadGroup(val group: Int) : Decoded()
        object Malformed : Decoded()
    }

    fun decode(text: String): Decoded {
        val chars = text.filter { it != '-' && !it.isWhitespace() }
        if (chars.isEmpty() || chars.length % 5 != 0) return Decoded.Malformed
        val data = StringBuilder()
        for ((i, g) in chars.chunked(5).withIndex()) {
            val norm = g.map { c -> val v = Base32C.value(c); if (v < 0) return Decoded.BadGroup(i + 1) else Base32C.ALPHABET[v] }.joinToString("")
            if (Base32C.check(norm.take(4), salt = i + 1) != norm[4]) return Decoded.BadGroup(i + 1)
            data.append(norm, 0, 4)
        }
        val head = Base32C.decode(data.toString(), 2) ?: return Decoded.Malformed
        val len = ((head[0].toInt() and 0xff) shl 8) or (head[1].toInt() and 0xff)
        val all = Base32C.decode(data.toString(), 2 + len) ?: return Decoded.Malformed
        return Decoded.Ok(all.copyOfRange(2, 2 + len))
    }
}
