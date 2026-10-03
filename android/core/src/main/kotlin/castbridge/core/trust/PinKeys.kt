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
    private const val BT_SUFFIX = " (Bluetooth)"
    private val DECOR = Regex("""\s*\((Bluetooth|\d+)\)\s*$""", RegexOption.IGNORE_CASE)
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

    /** Every key an app screen may use for [tv] (name, "name (Bluetooth)", mDNS name, `bt:<address>`, each IP bare, `ip:port` and `http://ip:port`); never blank, distinct. The tunnel loopback is NOT here: it designates the TV the gateway is connected to, not this one ([resolve]). */
    fun keysOf(tv: SavedTv, apiPort: Int = DEFAULT_PORT): List<String> {
        val ports = listOf(tv.port, apiPort).distinct()
        val hosts = tv.lastIps.map { it.trim() }.filter { it.isNotBlank() }
        val names = listOfNotNull(tv.name.trim().takeIf { it.isNotBlank() }, tv.mdns?.trim()?.takeIf { it.isNotBlank() })
        return (names + names.take(1).map { "$it$BT_SUFFIX" } + listOf("bt:${TrustRegistry.norm(tv.address)}") +
            hosts.flatMap { h -> listOf(h) + ports.flatMap { listOf(hostPort(h, it), "http://${hostPort(h, it)}") } }).filter { it.isNotBlank() }.distinct()
    }

    /** Keys to SEARCH a stored PIN with for [tv]: [keysOf] then the legacy raw spellings (see the field overload). */
    fun lookupKeys(tv: SavedTv, apiPort: Int = DEFAULT_PORT): List<String> =
        (keysOf(tv, apiPort) + lookupKeys(tv.name, tv.mdns, tv.address, tv.lastIps, tv.port)).distinct()

    /** Wi-Fi Direct group owner address: the same on EVERY TV (`WifiDirect.kt`), so it identifies none. */
    const val WIFI_DIRECT_IP = "192.168.49.1"

    /**
     * The saved TV a screen [key] designates, or null (unknown, blank, or ambiguous with no usable [default]). Tolerant to case, " (Bluetooth)", NSD " (2)",
     * a host without port, a full URL, IPv6 brackets (and zone `%wlan0`), and the Bluetooth tunnel: `127.0.0.1:[tunnelPort]` designates [tunnelTv] (the ADDRESS
     * of the TV the running gateway is connected to) and nobody else; with no running gateway ([tunnelTv] or [tunnelPort] null), or a gateway TV that is not saved,
     * a loopback key resolves to null (no token for loopback). Order: the unique forms first (`bt:` address, IP: an IP listed by several TVs, stale DHCP, or the
     * Wi-Fi Direct address, resolve to null), then names; a name shared by several TVs resolves to [default] only if it is one of them, else null: a token is
     * never offered to a TV picked at random.
     */
    fun resolve(key: String, saved: List<SavedTv>, default: SavedTv?, apiPort: Int = DEFAULT_PORT, tunnelPort: Int? = null, tunnelTv: String? = null): SavedTv? {
        val raw = key.trim()
        if (raw.isBlank() || saved.isEmpty()) return null
        if (raw.startsWith("bt:", ignoreCase = true)) {
            val a = TrustRegistry.norm(raw.substring(3))
            return saved.firstOrNull { TrustRegistry.norm(it.address) == a }
        }
        val (host, port) = hostAndPort(raw)
        if (host != null && (host == "127.0.0.1" || host.equals("localhost", true))) {
            if (tunnelPort == null || tunnelTv == null || port != tunnelPort) return null
            val a = TrustRegistry.norm(tunnelTv)
            return saved.firstOrNull { TrustRegistry.norm(it.address) == a }
        }
        if (host != null && host != WIFI_DIRECT_IP) {
            val byIp = saved.filter { tv -> tv.lastIps.any { it.trim().equals(host, true) } && (port == null || port == tv.port || port == apiPort) }
            if (byIp.size == 1) return byIp.first()
            if (byIp.size > 1) return null
        }
        val exact = saved.filter { tv -> sameName(raw, tv.name) || (tv.mdns != null && sameName(raw, tv.mdns)) }
        if (exact.isNotEmpty()) return pick(exact, default)
        val stripped = stripDecor(raw)
        if (stripped.isBlank() || stripped == raw) return null
        return pick(saved.filter { tv -> sameName(stripped, tv.name) || (tv.mdns != null && sameName(stripped, tv.mdns)) }, default)
    }

    private fun pick(candidates: List<SavedTv>, default: SavedTv?): SavedTv? = when {
        candidates.isEmpty() -> null
        candidates.size == 1 -> candidates.first()
        else -> candidates.firstOrNull { it.address == default?.address }
    }

    private fun sameName(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /** "X (Bluetooth)" and "X (2)" give "X"; repeated. */
    private fun stripDecor(k: String): String {
        var s = k.trim()
        while (true) { val n = DECOR.replace(s, "").trim(); if (n == s) return s; s = n }
    }

    /** host and port of "host", "host:port", "[v6]:port", "scheme://host:port/path"; host null when [key] cannot be one (contains spaces). */
    internal fun hostAndPort(key: String): Pair<String?, Int?> {
        var s = key.trim()
        s.indexOf("://").takeIf { it >= 0 }?.let { s = s.substring(it + 3) }
        s = s.substringBefore('/').substringBefore('?')
        if (s.isBlank() || s.any { it.isWhitespace() }) return null to null
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end < 0) return null to null
            return s.substring(1, end) to s.substring(end + 1).removePrefix(":").toIntOrNull()
        }
        val colons = s.count { it == ':' }
        return when {
            colons == 0 -> s to null
            colons == 1 -> s.substringBefore(':') to (s.substringAfter(':').toIntOrNull() ?: return null to null)
            else -> s to null                                   // bare IPv6
        }
    }

    /** Keys under which to WRITE a typed PIN entered on screen [key]: a TV that already has a token ([hasToken]) needs no PIN, so nothing goes under its IP keys
     * (a stale DHCP address would hand this PIN to another TV): only [key] itself when it is not one of the TV's IPs; a PIN-only TV gets [key] + [keysOf]. Unknown TV: [key]. */
    fun writeKeys(key: String, tv: SavedTv?, hasToken: Boolean, apiPort: Int = DEFAULT_PORT): List<String> = when {
        tv == null -> listOf(key)
        hasToken -> listOf(key).filterNot { k -> hostAndPort(k).first?.let { h -> tv.lastIps.any { it.trim().equals(h, true) } } == true }
        else -> (listOf(key) + keysOf(tv, apiPort)).distinct()
    }

    /** What `PinStore.get` returns: the live [token] if any, else the PIN found under [key] or under any key of [tv] (read order of [lookupKeys], legacy spellings included), else "". */
    fun credential(token: String?, key: String?, tv: SavedTv?, read: (String) -> String?): String {
        if (!token.isNullOrBlank()) return token
        if (key == null) return ""
        read(key)?.takeIf { it.isNotBlank() }?.let { return it }
        if (tv == null) return ""
        return lookupKeys(tv).firstNotNullOfOrNull { k -> read(k)?.takeIf { it.isNotBlank() } }.orEmpty()
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
