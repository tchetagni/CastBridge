package castbridge.core.trust

/**
 * The keys under which a TV's code (PIN) may be stored or looked up: name, mDNS name, `bt:<address>`, `host:port` (`TvLink.savedFor`, `PinStore`, `TvScreen`).
 *
 * Host forms: an IPv4 address gets the port ("192.168.0.5" gives "192.168.0.5:8765"); a hostname ("tv.local") gets it too in [keysOf], but a BARE hostname is
 * left as typed by [normalize] (only IPv4 is recognised there); an IPv6 address is written in brackets with its port ("[fe80::1]:8765") by [keysOf] and
 * left untouched by [normalize]. Legacy entries (written by `TvScreen.kt:77` under the bare host) are found by [lookupKeys].
 */
object PinKeys {
    const val DEFAULT_PORT = 8765
    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    private fun hostPort(host: String, port: Int?): String {
        val h = host.trim()
        val shown = if (h.contains(':') && !h.startsWith("[")) "[$h]" else h
        return "$shown:${port ?: DEFAULT_PORT}"
    }

    /** Every key of one TV, deduplicated, normalized, names first. [hosts] = `SavedTv.lastIps` (several addresses per TV). A host without port gets [DEFAULT_PORT]. */
    fun keysOf(name: String?, mdns: String?, btAddress: String?, hosts: List<String>, port: Int?): List<String> = (listOf(
        name, mdns,
        btAddress?.takeIf { it.isNotBlank() }?.let { "bt:$it" },
    ) + hosts.filter { it.isNotBlank() }.map { hostPort(it, port) }).filterNotNull().map { normalize(it) }.filter { it.isNotBlank() }.distinct()

    /** Keys to SEARCH with: the normalized ones of [keysOf] first, then the raw legacy spellings (bare host, as typed/stored before normalization). Distinct. */
    fun lookupKeys(name: String?, mdns: String?, btAddress: String?, hosts: List<String>, port: Int?): List<String> {
        val raw = listOf(name, mdns, btAddress?.takeIf { it.isNotBlank() }?.let { "bt:$it" }).filterNotNull() +
            hosts.filter { it.isNotBlank() }.flatMap { listOf(it, "${it.trim()}:${port ?: DEFAULT_PORT}") }
        return (keysOf(name, mdns, btAddress, hosts, port) + raw.map { it.trim() }).filter { it.isNotBlank() }.distinct()
    }

    /** A bare IPv4 address means the default port ("192.168.0.5" gives "192.168.0.5:8765"); every other key is kept as typed (trimmed). */
    fun normalize(key: String): String = key.trim().let { if (IPV4.matches(it)) "$it:$DEFAULT_PORT" else it }
}

/**
 * R-01, second half: `PinStore.get` falls back to the typed PIN when `TvLinkManager.credentialFor` is null, even when the null means « the token was refused or
 * expired » for a TV that trusts this phone. Screens call [choose]: for a trusted TV whose token was refused, NO PIN is presented (it is stale or not meant for
 * this TV), the caller shows [REFUSED] and waits for the link loop to renew the token.
 */
object PinFallback {
    enum class Source { TOKEN, PIN, NONE }
    data class Choice(val credential: String, val source: Source, val reason: String?)

    const val REFUSED = "Autorisation de ce téléphone expirée : reconnexion en cours, patientez."
    const val NEEDS_CODE = "Saisissez le code affiché sur la TV."

    /**
     * @param token the live session token (`credentialFor`), null if none; @param typedPin the stored typed code ("" = none);
     * @param trustedTv the TV is in the trusted registry; @param tokenRefused the TV answered 401 to the last token or the link loop dropped it.
     */
    fun choose(token: String?, typedPin: String, trustedTv: Boolean, tokenRefused: Boolean): Choice = when {
        !token.isNullOrBlank() && !tokenRefused -> Choice(token, Source.TOKEN, null)
        trustedTv && tokenRefused -> Choice("", Source.NONE, REFUSED)
        typedPin.isNotBlank() -> Choice(typedPin, Source.PIN, null)
        else -> Choice("", Source.NONE, NEEDS_CODE)
    }
}
