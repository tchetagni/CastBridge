package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.HelloInfo
import castbridge.core.tv.Link
import castbridge.core.tv.LinkPlanner
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder

/** Why Bluetooth cannot be used right now (the phone app shows a matching help, never a stack trace). */
class BtUnavailable(val reason: Reason) : IOException(reason.name) {
    enum class Reason { NO_ADAPTER, OFF, NO_PERMISSION }
}

/** The secure (authenticated, encrypted) RFCOMM link to the CBT1 service of a TV. "Insecure" sockets must never be used. */
interface BtTransport {
    /** Throws [BtUnavailable] or [IOException] (TV off, out of range, pairing refused). */
    fun connect(address: String): Link
}

/** A TV this phone was paired with and approved on. */
data class SavedTv(
    val address: String, val name: String, val mdns: String? = null,
    val lastIps: List<String> = emptyList(), val port: Int = 8765, val addedAt: Long = 0,
    /** Install id of the TV as last seen in a HELLO (see [castbridge.core.tv.HelloInfo.installId]); null = never seen / older TV. */
    val installId: String? = null,
)

/** Everything a screen needs once a TV answered: where to talk to it, with which credential, until when. */
data class LinkSession(val tv: SavedTv, val route: LinkPlanner.Route, val credential: String, val expiresAt: Long, val info: HelloInfo) {
    /** Base URL of the TV's HTTP API when a Wi-Fi route exists, else null (Bluetooth only). */
    val base: String? get() = when (val r = route) { is LinkPlanner.Route.Lan -> r.base; is LinkPlanner.Route.Direct -> r.base; else -> null }
}

/**
 * Phone side of the plug-and-play link: HELLO over Bluetooth, then pick the fastest way to the TV ([LinkPlanner]).
 * Pure logic on top of two small interfaces ([BtTransport], a reachability probe), so it is tested with a fake TV.
 */
class PhoneLink(
    private val transport: BtTransport,
    /** GET /api/hello on this base URL answers like a CastBridge TV. */
    private val reachable: (String) -> Boolean,
    private val canJoinWifiDirect: () -> Boolean = { false },
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed class Result {
        class Connected(val session: LinkSession) : Result()
        /** Bluetooth itself is unusable: off, no adapter, permission refused. */
        class BluetoothProblem(val reason: BtUnavailable.Reason) : Result()
        /** The TV did not answer (off, out of range, Bluetooth off on the TV...): try again later, silently. */
        class TvAbsent(val why: String) : Result()
        /** The TV answered "not you": [BtProtocol.ERR_UNTRUSTED] = forgotten or never added, [BtProtocol.ERR_DENIED] = refused by the owner... */
        class Refused(val code: Int, val hint: Int = BtProtocol.HINT_NONE) : Result() {
            val needsPairing get() = code == BtProtocol.ERR_UNTRUSTED
            /** The TV is another installation than the one this phone knew: it was reset or reinstalled. */
            val tvWasReset get() = code == BtProtocol.ERR_UNTRUSTED && hint == BtProtocol.HINT_OTHER_INSTALL
            val message get() = when (code) {
                BtProtocol.ERR_UNTRUSTED -> LinkText.untrusted(hint)
                BtProtocol.ERR_DENIED -> "La TV a refusé ce téléphone."
                BtProtocol.ERR_TIMEOUT -> "Personne n'a répondu sur la TV."
                BtProtocol.ERR_NOT_OPEN -> "Sur la TV, ouvrez « Ajouter un téléphone » puis réessayez."
                BtProtocol.ERR_BUSY -> "La TV attend déjà la réponse pour un autre téléphone."
                BtProtocol.ERR_MAGIC -> "Cette TV n'a pas la dernière version de CastBridge TV."
                else -> BtProtocol.describe(code)
            }
        }
    }

    /** One HELLO and the choice of route. [requestTrust] only from the « Ajouter ma TV » flow. */
    fun connect(tv: SavedTv, requestTrust: Boolean = false): Result {
        val info = try {
            transport.connect(tv.address).use { l -> BtProtocol.hello(l.input, l.output, requestTrust, tv.installId) }
        } catch (e: BtUnavailable) { return Result.BluetoothProblem(e.reason)
        } catch (e: BtProtocol.Refused) { return Result.Refused(e.code, e.hint)
        } catch (e: IOException) { return Result.TvAbsent(e.message ?: e.javaClass.simpleName) }
        val route = LinkPlanner.plan(info.link, reachable, canJoinWifiDirect()).first()
        val updated = tv.copy(name = info.tvName, mdns = info.mdns ?: tv.mdns, lastIps = info.link.ips.ifEmpty { tv.lastIps }, port = info.link.port, installId = info.installId ?: tv.installId)
        return Result.Connected(LinkSession(updated, route, info.token, now() + info.ttlSec * 1000, info))
    }
}

/** When to try again: after a failure (growing delay), and before the token expires (nobody ever sees it happen). */
object ReconnectPolicy {
    private val STEPS = longArrayOf(2_000, 4_000, 8_000, 15_000, 30_000, 60_000)
    fun retryDelayMs(consecutiveFailures: Int): Long = STEPS[(consecutiveFailures - 1).coerceIn(0, STEPS.size - 1)]

    /** Renew at half of the token's life (the old token stays valid on the TV until it expires, so nothing is cut). */
    fun renewAt(issuedAt: Long, expiresAt: Long): Long = issuedAt + (expiresAt - issuedAt) / 2

    fun needsRenewal(s: LinkSession, now: Long) = now >= renewAt(s.expiresAt - s.info.ttlSec * 1000, s.expiresAt)
}

/** The TVs a phone knows, which one is the default, and what to do when a TV changed its name or Bluetooth address. */
class SavedTvs(private val persistence: TrustPersistence) {
    private val tvs = LinkedHashMap<String, SavedTv>()
    private var defaultAddress: String? = null

    init { load() }

    @Synchronized fun list(): List<SavedTv> = tvs.values.toList()
    @Synchronized fun get(address: String) = tvs[TrustRegistry.norm(address)]

    /** The TV to connect to at startup: the one the user chose, else the only one. Null when there are several and none was chosen. */
    @Synchronized fun default(): SavedTv? = tvs[defaultAddress] ?: tvs.values.singleOrNull()

    @Synchronized fun upsert(tv: SavedTv, makeDefault: Boolean = false) {
        val a = TrustRegistry.norm(tv.address)
        tvs[a] = tv.copy(address = a, name = PhoneName.sanitize(tv.name, 60))
        if (makeDefault || defaultAddress == null) defaultAddress = a
        save()
    }

    @Synchronized fun setDefault(address: String): Boolean {
        val a = TrustRegistry.norm(address)
        if (!tvs.containsKey(a)) return false
        defaultAddress = a; save(); return true
    }

    @Synchronized fun remove(address: String) {
        val a = TrustRegistry.norm(address)
        if (tvs.remove(a) != null) { if (defaultAddress == a) defaultAddress = tvs.keys.firstOrNull(); save() }
    }

    /** A saved TV whose name matches a paired device now seen at a different address (the TV's Bluetooth identity changed). */
    data class AddressChange(val old: SavedTv, val newAddress: String)

    @Synchronized fun addressChanges(bondedCbt: List<TvCandidate>): List<AddressChange> = tvs.values.mapNotNull { s ->
        if (bondedCbt.any { TrustRegistry.norm(it.address) == s.address }) null
        else bondedCbt.firstOrNull { it.name == s.name && TrustRegistry.norm(it.address) !in tvs }?.let { AddressChange(s, TrustRegistry.norm(it.address)) }
    }

    @Synchronized fun moveAddress(old: String, newAddress: String) {
        val o = tvs.remove(TrustRegistry.norm(old)) ?: return
        val n = TrustRegistry.norm(newAddress)
        tvs[n] = o.copy(address = n)
        if (defaultAddress == TrustRegistry.norm(old)) defaultAddress = n
        save()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun save() {
        val sb = StringBuilder()
        defaultAddress?.let { sb.append("D\t").append(it).append('\n') }
        tvs.values.forEach { sb.append("S\t").append(it.address).append('\t').append(enc(it.name)).append('\t').append(enc(it.mdns ?: "")).append('\t')
            .append(it.lastIps.joinToString(",")).append('\t').append(it.port).append('\t').append(it.addedAt).append('\t').append(it.installId.orEmpty()).append('\n') }
        runCatching { persistence.save(sb.toString()) }
    }

    private fun load() {
        val text = runCatching { persistence.load() }.getOrNull() ?: return
        for (line in text.lineSequence()) runCatching {
            val f = line.split('\t')
            when (f[0]) {
                "D" -> defaultAddress = TrustRegistry.norm(f[1])
                "S" -> {
                    val a = TrustRegistry.norm(f[1]); if (!TrustRegistry.isAddress(a)) return@runCatching
                    tvs[a] = SavedTv(a, PhoneName.sanitize(URLDecoder.decode(f[2], "UTF-8"), 60), URLDecoder.decode(f[3], "UTF-8").ifEmpty { null },
                        f[4].split(',').filter { it.isNotEmpty() }, f[5].toInt(), f[6].toLong(), f.getOrNull(7)?.takeIf { it.matches(Regex("^[0-9a-f]{8,64}$")) })
                }
            }
        }
    }
}

/** A Bluetooth device seen while looking for the TV. [hasCbt1]: SDP says it offers the CastBridge TV service (null = not known yet). */
data class TvCandidate(val address: String, val name: String, val bonded: Boolean, val hasCbt1: Boolean?)

/** The list shown in « Ajouter ma TV »: confirmed TVs first (paired before unpaired), unconfirmed devices only on request. */
object Candidates {
    fun merge(found: Collection<TvCandidate>): List<TvCandidate> {
        val byAddr = LinkedHashMap<String, TvCandidate>()
        for (c in found) {
            val a = TrustRegistry.norm(c.address)
            val old = byAddr[a]
            byAddr[a] = if (old == null) c.copy(address = a) else c.copy(address = a, bonded = old.bonded || c.bonded,
                hasCbt1 = when { old.hasCbt1 == true || c.hasCbt1 == true -> true; old.hasCbt1 == false || c.hasCbt1 == false -> false; else -> null },
                name = c.name.ifBlank { old.name })
        }
        return byAddr.values.sortedWith(compareBy({ it.hasCbt1 != true }, { !it.bonded }, { it.name.lowercase() }))
    }

    fun tvs(all: Collection<TvCandidate>) = merge(all).filter { it.hasCbt1 == true }
    fun others(all: Collection<TvCandidate>) = merge(all).filter { it.hasCbt1 != true }
}
