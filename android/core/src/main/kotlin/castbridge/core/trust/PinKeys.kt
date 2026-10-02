package castbridge.core.trust

/** The keys under which a TV's code (PIN) may be stored or looked up: name, mDNS name, `bt:<address>`, `host:port` (`TvLink.savedFor`, `PinStore`, `TvScreen`). */
object PinKeys {
    const val DEFAULT_PORT = 8765
    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    /** Every key of one TV, deduplicated, normalized, names first. A host without port gets [DEFAULT_PORT]. */
    fun keysOf(name: String?, mdns: String?, btAddress: String?, host: String?, port: Int?): List<String> = listOf(
        name, mdns,
        btAddress?.takeIf { it.isNotBlank() }?.let { "bt:$it" },
        host?.takeIf { it.isNotBlank() }?.let { "$it:${port ?: DEFAULT_PORT}" },
    ).filterNotNull().map { normalize(it) }.filter { it.isNotBlank() }.distinct()

    /** A bare IPv4 address means the default port ("192.168.0.5" ⇒ "192.168.0.5:8765"); every other key is kept as typed (trimmed). */
    fun normalize(key: String): String = key.trim().let { if (IPV4.matches(it)) "$it:$DEFAULT_PORT" else it }
}
