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

    /** `setGroupOperatingBand` exists from API 29; below that only the automatic band is possible. */
    fun first(sdk: Int): Band = if (sdk >= 29) Band.GHZ5 else Band.AUTO

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
