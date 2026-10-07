package castbridge.core.gateway

import java.io.IOException

/**
 * Which RFCOMM service carries the Internet gateway (R-28, inventory I-1).
 *
 * Until R-28 the gateway shared the UUID `…0002` with the SSH tunnel: the TV listened on both (the gateway at every start, the SSH while it was on) and a phone that asked for
 * one could reach the other. The gateway now has its own UUID ([Gw.SERVICE_UUID], `…0007`). Compatibility:
 *  - TV ([tvListens], marker `R-28-LEGACY-TV`, to delete after TWO published versions of CastBridge-TV): always the new UUID; the old one ([Gw.LEGACY_SERVICE_UUID]) only while the TV's
 *    SSH over Bluetooth is OFF, so that the old UUID never has two owners. While the SSH is on, an OLD phone that asks for the old UUID reaches the SSH tunnel and fails: it has to
 *    wait for the SSH to end (or update).
 *  - Phone ([PhoneChoice], marker `R-28-OLD-TV`, to keep as long as old TVs are in the field, TVs being updated by hand): asks for the new UUID first; falls back on the old one when the
 *    TV is (possibly) old, and remembers « old TV » for [LEGACY_RECHECK_MS] ONLY once the gateway HELLO was answered on that connection ([PhoneChoice.helloAnswered], R-36 / audit I-8:
 *    an updated TV with its SSH over Bluetooth on also ANSWERS a connection on the old UUID, with its SSH tunnel, which is not the gateway). A TV that announces the new UUID is never
 *    asked for the old one: on an updated TV that UUID may be the SSH tunnel's.
 */
object GatewayService {
    /** How long a phone keeps « this TV only has the old service » before asking for the new one again (an updated TV may have appeared). Same as the API v2 tunnel (LinkPool). */
    const val LEGACY_RECHECK_MS = 10 * 60_000L
    /** Pause between two connection attempts to the same TV: the Bluetooth stack may still hold the failed one (« already at opened state », docs/BT-PLUG-AND-PLAY.md). */
    const val GAP_MS = 1_500L

    /** TV side: the UUIDs on which the gateway listens while the TV's SSH over Bluetooth is [sshBluetoothOn]. Never the SSH tunnel's UUID while that tunnel is on. */
    fun tvListens(sshBluetoothOn: Boolean): List<String> =
        if (sshBluetoothOn) listOf(Gw.SERVICE_UUID) else listOf(Gw.SERVICE_UUID, Gw.LEGACY_SERVICE_UUID)   // R-28-LEGACY-TV

    /** Phone side, one per gateway run. [now] is injectable (tests). */
    class PhoneChoice(private val now: () -> Long = System::currentTimeMillis, private val legacyRecheckMs: Long = LEGACY_RECHECK_MS) {
        @Volatile private var legacyUntil = 0L
        /** The UUID of the last connection [connect] handed out, until the gateway HELLO on it is answered ([helloAnswered]) or another connection replaces it. */
        @Volatile private var dialed: String? = null

        /**
         * The UUIDs to try for one connection, in this order. [advertised] = the service UUIDs Android knows the TV offers (its cached SDP answer; null = not known): a TV that
         * lists the new UUID is updated for sure, so only that one is asked for; a list without it proves nothing (the cache may predate an update of the TV).
         */
        fun order(advertised: Collection<String>? = null): List<String> = when {
            advertised?.any { it.equals(Gw.SERVICE_UUID, ignoreCase = true) } == true -> listOf(Gw.SERVICE_UUID)
            now() < legacyUntil -> listOf(Gw.LEGACY_SERVICE_UUID)                         // R-28-OLD-TV: an old TV met a moment ago: straight to its only service
            else -> listOf(Gw.SERVICE_UUID, Gw.LEGACY_SERVICE_UUID)                       // R-28-OLD-TV: unknown TV: the new service first, the old one if it does not exist
        }

        /**
         * The gateway of the TV ANSWERED on [uuid] (its HELLO was answered, or the new service accepted a connection): the TV is old if it was the old UUID (remembered for a while),
         * updated if it was the new one. A mere connection to the old UUID proves nothing: see [helloAnswered].
         */
        fun connected(uuid: String) { legacyUntil = if (uuid.equals(Gw.LEGACY_SERVICE_UUID, ignoreCase = true)) now() + legacyRecheckMs else 0L }

        /**
         * The gateway HELLO of the connection [connect] returned was answered OK (R-36, audit I-8): only now is a connection to the old UUID known to be the TV's gateway, hence the TV an
         * old one. Until then (a refused HELLO, an SSH banner, a link closed by the TV) nothing is remembered and the next attempt asks for the new UUID first again.
         */
        fun helloAnswered() { dialed?.let { connected(it) }; dialed = null }

        /**
         * One connection to the gateway of a TV: [dial] opens ONE connection to the service [uuid] and throws [IOException] when the TV has no such service or cannot be reached
         * (the caller closes what it opened on a failure). Tries [order] with a pause of [GAP_MS] between two attempts, remembers what worked, and rethrows the LAST failure
         * when every attempt failed (a TV that is simply away fails twice, as one failure does for a TV with a single service). [stopping] ends the attempts early.
         */
        fun <T> connect(advertised: Collection<String>?, stopping: () -> Boolean = { false }, sleep: (Long) -> Unit = Thread::sleep, log: (String) -> Unit = {}, dial: (uuid: String) -> T): T {
            var last: IOException? = null
            for ((i, uuid) in order(advertised).withIndex()) {
                if (stopping()) break
                if (i > 0) sleep(GAP_MS)
                try {
                    val c = dial(uuid)
                    dialed = uuid
                    // the new service exists only on an updated TV: a connection to it is proof enough ; the old one is also the SSH tunnel's (R-28): proof only after the HELLO ([helloAnswered])
                    if (!uuid.equals(Gw.LEGACY_SERVICE_UUID, ignoreCase = true)) connected(uuid)
                    else log("old TV (to be confirmed by the HELLO): the gateway is tried on the shared service …${uuid.takeLast(4)}")
                    return c
                } catch (e: IOException) { last = e; log("gateway service …${uuid.takeLast(4)}: ${e.message}") }
            }
            throw last ?: IOException("arrêt demandé")
        }
    }
}
