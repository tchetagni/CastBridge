package castbridge.core.net

/** What the TV screen says about Internet: by which path it really works (never just « a link is up »). */
enum class NetState(val wire: String, val label: String) {
    CHECKING("checking", "Internet : vérification…"),
    INTERNET_WIFI("wifi", "Internet : Wi-Fi"),
    INTERNET_ETHERNET("ethernet", "Internet : Ethernet"),
    INTERNET_VIA_PHONE("phone", "Internet : via le téléphone"),
    NONE("none", "Pas d'Internet");

    val working get() = this == INTERNET_WIFI || this == INTERNET_ETHERNET || this == INTERNET_VIA_PHONE
}

/** How the TV itself is linked (what the system says; the probe says whether it really reaches Internet). */
enum class LinkKind { WIFI, ETHERNET, OTHER, NONE }

/**
 * Debounced Internet state from the existing 204 probes (one [update] per probe round, no probing here).
 * Per path: 1 success = up at once, [downAfter] consecutive failures = down (a flapping Wi-Fi does not blink the badge).
 * Direct network first if it works, else the phone's gateway if it works, else NONE. From CHECKING, NONE needs [downAfter] failed rounds too. A change between two
 * working paths, or to NONE, waits [minShowMs] after the last change; going from NONE/CHECKING to a working
 * path is never delayed. A gateway that is not connected is down at once (nothing to wait for).
 */
class NetStateTracker(private val downAfter: Int = 2, private val minShowMs: Long = 8_000) {
    var state = NetState.CHECKING; private set
    /** True when the phone's Internet works too (shown in the settings/network panel while the direct path is displayed). */
    var gatewayAlsoAvailable = false; private set
    /** Consecutive rounds in NONE/CHECKING, for [nextDelayMs]. */
    var badRounds = 0; private set

    private var directUp = false; private var directFails = 0
    private var gwUp = false; private var gwFails = 0
    private var directLabel = NetState.INTERNET_WIFI
    private var changedAt = Long.MIN_VALUE / 2

    fun update(link: LinkKind, directMs: Long?, gatewayConnected: Boolean, gatewayMs: Long?, now: Long): NetState {
        if (directMs != null) { directUp = true; directFails = 0; directLabel = if (link == LinkKind.ETHERNET) NetState.INTERNET_ETHERNET else NetState.INTERNET_WIFI }
        else if (++directFails >= downAfter) directUp = false
        if (!gatewayConnected) { gwUp = false; gwFails = 0 }
        else if (gatewayMs != null) { gwUp = true; gwFails = 0 }
        else if (++gwFails >= downAfter) gwUp = false
        gatewayAlsoAvailable = gwUp && gatewayConnected
        val want = when { directUp -> directLabel; gwUp -> NetState.INTERNET_VIA_PHONE; else -> NetState.NONE }
        if (want != state) {
            val immediate = (state == NetState.CHECKING && (want.working || badRounds + 1 >= downAfter)) || (state == NetState.NONE && want.working)
            val settled = state != NetState.CHECKING     // CHECKING waits for downAfter rounds, not for the display time
            if (immediate || (settled && now - changedAt >= minShowMs)) { state = want; changedAt = now }
        }
        badRounds = if (state.working) 0 else badRounds + 1
        return state
    }

    /** Delay before the next probe round: light (60 s) while Internet works, faster with a backoff while it does not. */
    fun nextDelayMs(): Long = if (state.working) STEADY_MS else FAST_STEPS_MS[minOf(maxOf(badRounds - 1, 0), FAST_STEPS_MS.size - 1)]

    companion object {
        const val STEADY_MS = 60_000L
        val FAST_STEPS_MS = longArrayOf(10_000, 10_000, 15_000, 15_000, 30_000)

        fun linkKind(ethernet: Boolean, wifi: Boolean, any: Boolean) = when { ethernet -> LinkKind.ETHERNET; wifi -> LinkKind.WIFI; any -> LinkKind.OTHER; else -> LinkKind.NONE }
    }
}

/** Body of GET /api/net (additive, read-only; no personal data). */
fun netJson(t: NetStateTracker, link: LinkKind, directMs: Long?, gatewayConnected: Boolean, gatewayMs: Long?, checkedAt: Long): String =
    """{"state":"${t.state.wire}","label":"${t.state.label}","working":${t.state.working},""" +
        """"link":"${link.name.lowercase()}","direct":{"ok":${directMs != null},"ms":${directMs ?: "null"}},""" +
        """"gateway":{"connected":$gatewayConnected,"ok":${gatewayMs != null},"ms":${gatewayMs ?: "null"},"alsoAvailable":${t.gatewayAlsoAvailable}},""" +
        """"checkedAt":$checkedAt}"""
