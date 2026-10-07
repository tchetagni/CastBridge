package castbridge.core.owner

import castbridge.core.btact.BtActAd
import castbridge.core.btact.BtActClient
import castbridge.core.btact.BtActWire
import castbridge.core.owner.ActivationRoutePlan.Cause

/**
 * The decisions of the route « Bluetooth sans appairage » of « Activer la TV » (act-bt, `docs/BT-PLUG-AND-PLAY.md`), pure and tested; `sender/BtActivationClient` only scans, connects and relays what
 * these rules decide. The phone typed the code; it scans BLE for the service `…0008` ([BtActAd.SERVICE_UUID]), keeps the advertisements whose TAG is the one of that code ([BtActAd.matches]), tries those TVs
 * strongest first, and turns what each TV said into the cause of the route:
 *  - **who is tried**: only a TV whose tag matches (a typo or the TV of a neighbour is not even approached, so it costs no wrong code on anyone), the strongest signal first, [MAX_CANDIDATES] at most
 *    (two TVs of the same tag are a 1 in 65 536 coincidence);
 *  - **when the scan stops**: [SCAN_MS] at most, or [SETTLE_MS] after the first match (a second TV of the same tag would have been heard by then); the TV is connected AFTER the scan stops
 *    (Android connects slowly while it scans);
 *  - **what the verdict is** when no TV let the phone in ([verdict]): the most useful word the TVs said, else « aucune TV ne s'annonce avec ce code » (the same words for a TV that is off, out of range,
 *    too old, and a wrong code: the tag filter hides which, on purpose), else « la liaison n'a pas abouti ».
 * No code, no key and no request is in any text or `toString` here.
 */
object BleSearch {
    /** The scan window: a TV that advertises is heard within 1 to 2 s; ten seconds cover a TV a little far. */
    const val SCAN_MS = 10_000L
    /** After the first matching advertisement, wait this long for another TV of the same tag before connecting. */
    const val SETTLE_MS = 800L
    const val MAX_CANDIDATES = 3
    /** The whole attempt on ONE TV (connection, handshake, the request read): the link is closed after it. */
    const val ATTEMPT_MS = 12_000L

    /** One advertisement heard: [id] = what Android calls the device (an address, which may change), [payload] = the manufacturer record of company [BtActAd.COMPANY_ID] when the packet carried it (the advertisement, or in the fallback layout its scan response). */
    class Sighting(val id: String, val rssi: Int, val payload: ByteArray?) { override fun toString() = "Sighting($rssi dBm)" }

    class Candidate(val id: String, val rssi: Int, val psm: Int) { override fun toString() = "Candidate($rssi dBm, psm=$psm)" }

    /** Keeps the matching advertisements of one scan: one per device (its strongest), strongest first. */
    class Collector(private val code: String) {
        private val byId = LinkedHashMap<String, Candidate>()
        /** When the first match was heard (the caller's clock), null before. */
        var firstAtMs: Long? = null; private set

        /** True when [s] is a TV the typed code may be tried on (its tag matches): a new one, or a stronger sighting of one already known. */
        fun add(s: Sighting, nowMs: Long): Boolean {
            val parsed = BtActAd.parse(s.payload)
            if (!BtActAd.matches(parsed, code)) return false
            val c = Candidate(s.id, s.rssi, parsed!!.psm)
            val old = byId[s.id]
            if (old == null || c.rssi > old.rssi) byId[s.id] = c
            if (firstAtMs == null) firstAtMs = nowMs
            return true
        }

        /** The TVs to try, strongest signal first, at most [MAX_CANDIDATES]. */
        fun candidates(): List<Candidate> = byId.values.sortedByDescending { it.rssi }.take(MAX_CANDIDATES)

        /** Stop scanning: the window is over, or a match was heard [SETTLE_MS] ago. */
        fun scanDone(startedAtMs: Long, nowMs: Long): Boolean = nowMs - startedAtMs >= SCAN_MS || (firstAtMs?.let { nowMs - it >= SETTLE_MS } == true)
    }

    /** The channels a phone may open to a TV. */
    enum class Channel { L2CAP, RFCOMM }

    /** `createInsecureL2capChannel` exists from Android 10 (API 29). */
    const val L2CAP_MIN_API = 29

    /**
     * The channels to try on a TV, in order. L2CAP first (Android 10 and over, and only when the advertisement gave a PSM): it runs over the BLE link the phone has just seen, whose address is NOT the TV's
     * classic Bluetooth address; then the « insecure » RFCOMM of the service `…0008`, which only opens when the address of the advertisement is the classic one (a box that advertises with its public address).
     */
    fun channels(api: Int, psm: Int): List<Channel> = if (api >= L2CAP_MIN_API && psm != 0) listOf(Channel.L2CAP, Channel.RFCOMM) else listOf(Channel.RFCOMM)

    /** What happened with ONE candidate. */
    sealed class Attempt {
        /** The TV proved the code and said its name; [requestText] = its device request when it was read, else null. */
        class Reached(val tvName: String, val tvVersion: String, val requestText: String?) : Attempt()
        /** The TV said no, with its reason (and [seconds] to wait when it is locked). */
        class Refused(val err: BtActWire.Err, val seconds: Long = 0) : Attempt() { override fun toString() = "Refused($err, $seconds)" }
        /** Connected (over [channel] when known), but the TV did not prove the code, or the link broke or spoke another protocol. */
        class Lost(val channel: Channel? = null) : Attempt() { override fun toString() = "Lost" }
        /** No channel could be opened to that TV (neither L2CAP nor RFCOMM): [tried] says which were tried, for the person who tests on a real box. */
        class NoConnection(val tried: List<Channel> = emptyList()) : Attempt() { override fun toString() = "NoConnection" }
    }

    /** The name of a channel as the screen says it. */
    fun channelName(c: Channel): String = when (c) { Channel.L2CAP -> "L2CAP"; Channel.RFCOMM -> "RFCOMM" }

    /** The attempt of [BtActClient.connect] as the route sees it (before the request is read); [channel] = the channel it ran on. */
    fun attemptOf(c: BtActClient.Connect, channel: Channel? = null): Attempt? = when (c) {
        is BtActClient.Connect.Ready -> null
        is BtActClient.Connect.Refused -> Attempt.Refused(c.err, c.seconds)
        BtActClient.Connect.NotProved, BtActClient.Connect.LinkLost -> Attempt.Lost(channel)
    }

    /** What a TV that said no means for the route: the HTTP route's words (« relisez les 6 chiffres », « la TV attend N s »…). [name] = the TV's name when known (never the case before the proof). */
    fun causeOf(a: Attempt): Cause = when (a) {
        is Attempt.Refused -> when (a.err) {
            BtActWire.Err.BAD_CODE -> Cause(Cause.Kind.CODE_REFUSED)
            BtActWire.Err.LOCKED -> Cause(Cause.Kind.LOCKED_OUT, a.seconds.toString())
            BtActWire.Err.CLOSED -> Cause(Cause.Kind.CLOSED)
            BtActWire.Err.TERMS -> Cause(Cause.Kind.TERMS)
            BtActWire.Err.VERSION -> Cause(Cause.Kind.NEEDS_UPDATE)
            BtActWire.Err.UNAVAILABLE, BtActWire.Err.BAD_MESSAGE -> Cause(Cause.Kind.BLE_CONNECT)
        }
        is Attempt.Reached -> Cause(Cause.Kind.BLE_CONNECT)
        // which channel connected or was tried goes on the screen: the one thing a person testing on a real box needs to know when a TV is heard and still not joined
        is Attempt.Lost -> Cause(Cause.Kind.BLE_CONNECT, a.channel?.let { "canal : ${channelName(it)}" })
        is Attempt.NoConnection -> Cause(Cause.Kind.BLE_CONNECT, a.tried.takeIf { it.isNotEmpty() }?.let { l -> "essayé : " + l.joinToString(", ") { channelName(it) } })
    }

    // the most useful word first: a lock or a closed TV says how long to wait, the terms say what to do on the TV, a wrong code says what to read again
    private val PRIORITY = listOf(Cause.Kind.LOCKED_OUT, Cause.Kind.CLOSED, Cause.Kind.TERMS, Cause.Kind.NEEDS_UPDATE, Cause.Kind.CODE_REFUSED, Cause.Kind.BLE_CONNECT)

    /**
     * The cause of the route when the scan is over and no TV let the phone in: [attempts] = what each candidate said (empty = none was tried), [heard] = how many matching TVs were heard.
     * No candidate heard: « aucune TV ne s'annonce avec ce code » ([Cause.Kind.BLE_NOT_FOUND]).
     */
    fun verdict(attempts: List<Attempt>, heard: Int): Cause {
        if (heard == 0 || attempts.isEmpty()) return Cause(Cause.Kind.BLE_NOT_FOUND)
        val causes = attempts.map(::causeOf)
        return PRIORITY.firstNotNullOfOrNull { k -> causes.firstOrNull { it.kind == k } } ?: Cause(Cause.Kind.BLE_CONNECT)
    }

    /** What the key's installation over this route came to, for [KeyAcquisition]: the same results as the HTTP and the paired Bluetooth routes. */
    sealed class Install {
        class Done(val result: BtActClient.Session.Installed) : Install()
        /** The handshake did not get to the key. */
        class Blocked(val attempt: Attempt) : Install()
        /** The TV is not heard any more (it left its activation screen, or is out of range). */
        object NotFound : Install()
    }

    /** The result [KeyAcquisition] expects. A key refused keeps the TV's own reason; a wrong code says so (« Changer le code »); a link cut or a TV not heard is « injoignable » (the key stays ready). */
    fun installResult(i: Install, now: Long): KeyAcquisition.Event.Result = when (i) {
        is Install.Done -> when (val r = i.result) {
            is BtActClient.Session.Installed.Accepted -> KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.OK, r.label.takeIf { it.isNotBlank() }?.let { "Clé : $it." }.orEmpty(), now)
            is BtActClient.Session.Installed.Rejected -> KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.REFUSED, r.message, now)
            BtActClient.Session.Installed.Limit -> KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.REFUSED, "La TV a reçu trop d'essais de ce téléphone : réessayez dans 10 minutes.", now)
            BtActClient.Session.Installed.Lost -> KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.UNREACHABLE, "", now)
        }
        is Install.Blocked -> when (val a = i.attempt) {
            is Attempt.Refused -> when (a.err) {
                BtActWire.Err.BAD_CODE -> KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.CODE_REFUSED, "", now)
                else -> KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.REFUSED, causeOf(a).text(ActivationRoutePlan.Route.BLE, ""), now)
            }
            else -> KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.UNREACHABLE, "", now)
        }
        Install.NotFound -> KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.UNREACHABLE, "", now)
    }
}
