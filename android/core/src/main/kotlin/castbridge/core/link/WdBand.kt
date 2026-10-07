package castbridge.core.link

/**
 * Which radio band the TV asks for when it creates a Wi-Fi Direct group (owner's group from MENU or the automatic one of W18).
 * Owner decision 2026-10-06: prefer 5 GHz (faster copies, less crowded than 2.4 GHz), fall back to Android's automatic choice
 * (in practice 2.4 GHz) when the hardware or the driver refuses. Pure: the Android layer maps [Band] to WifiP2pConfig.
 */
object WdBand {
    enum class Band { GHZ5, AUTO }

    /** WifiP2pManager failure reasons (copied: the core has no Android). */
    const val REASON_UNSUPPORTED = 1
    const val REASON_BUSY = 2

    /**
     * `setGroupOperatingBand` exists from API 29; below that only the automatic band is possible. Not every TV has a 5 GHz radio
     * (owner, 2026-10-06): [radio5GHz] = what Android says of the Wi-Fi card (null = unknown, treated as possible), [failedBefore] = a
     * 5 GHz group was already refused on this TV (remembered): then no attempt at all, the automatic band at once.
     */
    fun first(sdk: Int, radio5GHz: Boolean? = null, failedBefore: Boolean = false, forPhone: Boolean = false): Band =
        // H3 (audit 2026-10-07): a 5 GHz group is invisible to a phone with a 2.4 GHz-only radio (Tecno, Itel): the AUTOMATIC group made for a
        // phone keeps the automatic band; 5 GHz applies only to the owner's group (MENU), which the owner joins knowingly.
        if (!forPhone && sdk >= 29 && radio5GHz != false && !failedBefore) Band.GHZ5 else Band.AUTO

    /** A 5 GHz group nobody joined within this time is recreated once on the automatic band (the phone may only see 2.4 GHz). */
    const val NO_CLIENT_MS = 45_000L

    /** True = recreate the group on [Band.AUTO] now: a 5 GHz group, no client after [NO_CLIENT_MS], and not already recreated once. */
    fun recreateAuto(band: Band, clients: Int?, elapsedMs: Long, alreadyRecreated: Boolean): Boolean =
        band == Band.GHZ5 && clients == 0 && elapsedMs >= NO_CLIENT_MS && !alreadyRecreated

    /**
     * After a failed createGroup with [tried]: the band to retry with, or null to give up. Only a 5 GHz attempt is retried, and
     * never on BUSY (a stale group: the band is not the cause) nor UNSUPPORTED (P2P itself is missing).
     */
    fun retry(tried: Band, reason: Int): Band? =
        if (tried == Band.GHZ5 && reason != REASON_BUSY && reason != REASON_UNSUPPORTED) Band.AUTO else null

    fun label(b: Band): String = when (b) { Band.GHZ5 -> "5 GHz"; Band.AUTO -> "bande automatique" }

    /** Human line for a measured group frequency (MHz, 0 = unknown). */
    fun frequencyLabel(mhz: Int): String? = when {
        mhz <= 0 -> null
        mhz >= 5000 -> "5 GHz ($mhz MHz)"
        mhz >= 2400 -> "2,4 GHz ($mhz MHz)"
        else -> "$mhz MHz"
    }
}
