package castbridge.core.remote.smart

enum class Vendor(val label: String) {
    CASTBRIDGE("CastBridge-TV"), CVTE("CVTE / Amlogic (marque blanche)"), ANDROID_TV("Android TV / Google TV"), SAMSUNG("Samsung"),
    LG("LG"), ROKU("Roku"), SONY("Sony"), PHILIPS("Philips"), VIZIO("Vizio"), UNKNOWN("Inconnu")
}

/** One DNS-SD instance seen on the network (type like "_share._tcp", name, TXT attributes). */
data class MdnsRecord(val type: String, val name: String, val txt: Map<String, String> = emptyMap(), val port: Int? = null)

/** What a UPnP `description.xml` says (or what an SSDP answer carries). */
data class UpnpInfo(
    val manufacturer: String? = null, val modelName: String? = null, val modelNumber: String? = null, val friendlyName: String? = null,
    val deviceType: String? = null, val server: String? = null,
    val serviceTypes: List<String> = emptyList(),
    /** service type -> absolute control URL (resolved against the description's location). */
    val controlUrls: Map<String, String> = emptyMap(),
)

/** A passive HTTP signature (port, `Server` header, a short body excerpt). */
data class HttpSignature(val port: Int, val server: String? = null, val body: String? = null)

/** Everything known about the TV without sending it any command. Every field is optional. */
data class TvHints(
    val host: String? = null,
    val mdns: List<MdnsRecord> = emptyList(),
    val upnp: UpnpInfo? = null,
    val mac: String? = null,
    val bluetoothName: String? = null,
    /** null = ports were not checked; empty = checked, none open. */
    val openPorts: Set<Int>? = null,
    val http: List<HttpSignature> = emptyList(),
)

data class TvFingerprint(
    val vendor: Vendor,
    val family: String?,
    val model: String?,
    /** 0.0 – 1.0, combined from independent clues. Low (< 0.3) = [Vendor.UNKNOWN]. */
    val confidence: Double,
    val evidence: List<String>,
    /** Ordered strategy ids worth trying first (best first). The orchestrator appends the generic ones. */
    val candidates: List<String>,
    val openPorts: Set<Int>? = null,
    val upnp: UpnpInfo? = null,
    val hints: TvHints = TvHints(),
) {
    /** False only when ports were checked and [port] is closed (so a strategy can be skipped without trying it). */
    fun mayUse(port: Int) = openPorts == null || port in openPorts
    fun nearestStrategy(): String? = candidates.firstOrNull()
    companion object {
        fun unknown(host: String? = null) = TvFingerprint(Vendor.UNKNOWN, null, null, 0.0, emptyList(), emptyList(), null, null, TvHints(host = host))
    }
}

/** Parsers for the UPnP side (no XML library: a DOCTYPE or an entity in a TV's answer must never be interpreted). */
object Upnp {
    private fun tag(xml: String, name: String): String? =
        Regex("<(?:\\w+:)?$name(?:\\s[^>]*)?>\\s*([^<]*?)\\s*</(?:\\w+:)?$name>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.isNotEmpty() }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    /** [location]: the URL the description was fetched from (to resolve relative control URLs); [server]: the SSDP/HTTP `Server` header. */
    fun parseDescription(xml: String, location: String? = null, server: String? = null): UpnpInfo {
        val head = xml.substringBefore("<serviceList")        // device fields come before the first service list
        val types = ArrayList<String>(); val urls = LinkedHashMap<String, String>()
        for (m in Regex("<service>(.*?)</service>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(xml)) {
            val t = tag(m.groupValues[1], "serviceType") ?: continue
            types += t
            tag(m.groupValues[1], "controlURL")?.let { urls[t] = resolve(location, it) }
        }
        return UpnpInfo(
            manufacturer = tag(head, "manufacturer"), modelName = tag(head, "modelName"), modelNumber = tag(head, "modelNumber"),
            friendlyName = tag(head, "friendlyName"), deviceType = tag(head, "deviceType"), server = server,
            serviceTypes = types, controlUrls = urls,
        )
    }

    /** Absolute URL of [path] relative to [base] (an http URL), or [path] itself when it is already absolute / base unknown. */
    fun resolve(base: String?, path: String): String = when {
        path.startsWith("http://", true) || path.startsWith("https://", true) -> path
        base == null -> path
        else -> runCatching { java.net.URI(base).resolve(path).toString() }.getOrDefault(path)
    }

    /** Header lines of an SSDP answer ("HTTP/1.1 200 OK\r\nSERVER: …\r\nLOCATION: …") as lower-case keys. */
    fun parseSsdpHeaders(text: String): Map<String, String> =
        text.lineSequence().drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
}

/** Small public OUI sample (IEEE registry prefixes). Weak evidence only: MACs are often randomised or belong to the module maker. */
object Oui {
    val TABLE: Map<String, Vendor> = buildMap {
        for (p in listOf("00:07:AB", "00:12:47", "00:15:99", "00:16:32", "00:17:C9")) put(p, Vendor.SAMSUNG)
        for (p in listOf("00:1C:62", "00:1E:75", "00:E0:91", "00:05:C9")) put(p, Vendor.LG)
        for (p in listOf("00:0D:4B", "B0:A7:37", "08:05:81")) put(p, Vendor.ROKU)
        for (p in listOf("00:01:4A", "00:13:A9", "00:1D:BA", "00:19:63")) put(p, Vendor.SONY)
    }
    fun vendorOf(mac: String?): Vendor? {
        val m = mac?.uppercase()?.replace('-', ':') ?: return null
        return if (m.length >= 8) TABLE[m.substring(0, 8)] else null
    }
}

/** Turns passive clues into a [TvFingerprint]. Pure: the same hints always give the same result. */
object TvIdentifier {
    private class Clue(val vendor: Vendor?, val strategy: String?, val weight: Double, val text: String, val family: String? = null, val model: String? = null)

    fun identify(h: TvHints): TvFingerprint {
        val c = ArrayList<Clue>()
        val up = h.upnp
        val mdnsTypes = h.mdns.map { it.type.lowercase().removeSuffix(".") }
        val ports = h.openPorts ?: emptySet()
        fun has(port: Int) = port in ports
        fun mdns(prefix: String) = h.mdns.firstOrNull { it.type.lowercase().removeSuffix(".").startsWith(prefix) }
        val blob = listOfNotNull(up?.manufacturer, up?.modelName, up?.friendlyName, up?.server, h.bluetoothName, *h.http.mapNotNull { it.server }.toTypedArray()).joinToString(" | ").lowercase()
        fun has(vararg words: String) = words.any { it in blob }

        // CastBridge-TV
        mdns("_castbridge")?.let { r ->
            if (r.txt["role"] == "receiver" || r.txt.isEmpty()) c += Clue(Vendor.CASTBRIDGE, StrategyIds.CASTBRIDGE, 0.95, "mDNS _castbridge._tcp (role=receiver)", "CastBridge-TV")
        }
        if (has(8765)) c += Clue(Vendor.CASTBRIDGE, StrategyIds.CASTBRIDGE, 0.3, "port 8765 ouvert")

        // CVTE / Amlogic white label
        mdns("_share._tcp")?.let { r ->
            c += Clue(Vendor.CVTE, StrategyIds.CVTE, 0.6, "mDNS _share._tcp (« ${r.name} »)", "CVTE/Amlogic")
            if (r.txt.keys.any { it == "websocket_port" || it == "device_code" || it == "device_mac" }) c += Clue(Vendor.CVTE, StrategyIds.CVTE, 0.5, "TXT du service de partage (websocket_port, device_code…)")
        }
        mdns("_maxhubmobile")?.let { c += Clue(Vendor.CVTE, StrategyIds.CVTE, 0.5, "mDNS _maxhubmobile._tcp") }
        if (has(8125)) c += Clue(Vendor.CVTE, StrategyIds.CVTE, 0.5, "port 8125 ouvert")
        if (has(9909)) c += Clue(Vendor.CVTE, StrategyIds.CVTE, 0.2, "port 9909 ouvert")

        // Roku
        if (has("roku")) c += Clue(Vendor.ROKU, StrategyIds.ROKU, 0.9, "UPnP/HTTP : Roku", "Roku")
        if (has(8060)) c += Clue(Vendor.ROKU, StrategyIds.ROKU, 0.6, "port 8060 (ECP) ouvert")
        if (up?.serviceTypes?.any { it.contains("roku", true) } == true) c += Clue(Vendor.ROKU, StrategyIds.ROKU, 0.8, "service UPnP Roku")

        // Samsung
        if (has("samsung")) c += Clue(Vendor.SAMSUNG, StrategyIds.SAMSUNG, 0.75, "UPnP/Bluetooth : Samsung", "Tizen")
        if (has("tizen")) c += Clue(Vendor.SAMSUNG, StrategyIds.SAMSUNG, 0.5, "Tizen annoncé", "Tizen")
        mdns("_samsungmsf")?.let { c += Clue(Vendor.SAMSUNG, StrategyIds.SAMSUNG, 0.7, "mDNS _samsungmsf._tcp", "Tizen") }
        if (has(8001) || has(8002)) c += Clue(Vendor.SAMSUNG, StrategyIds.SAMSUNG, 0.5, "ports 8001/8002 ouverts")

        // LG webOS
        if (has("lg electronics", "[lg]", "lge ")) c += Clue(Vendor.LG, StrategyIds.LG, 0.75, "UPnP/Bluetooth : LG", "webOS")
        if (has("webos")) c += Clue(Vendor.LG, StrategyIds.LG, 0.7, "webOS annoncé", "webOS")
        if (has(3000) && has(3001)) c += Clue(Vendor.LG, StrategyIds.LG, 0.5, "ports 3000/3001 ouverts")
        else if (has(3001)) c += Clue(Vendor.LG, StrategyIds.LG, 0.3, "port 3001 ouvert")

        // Sony
        if (has("sony")) c += Clue(Vendor.SONY, StrategyIds.SONY, 0.6, "UPnP/Bluetooth : Sony", "BRAVIA")
        if (has("bravia")) c += Clue(Vendor.SONY, StrategyIds.SONY, 0.5, "modèle BRAVIA", "BRAVIA")
        if (up?.serviceTypes?.any { it.contains("sony-com:service:IRCC", true) } == true) c += Clue(Vendor.SONY, StrategyIds.SONY, 0.9, "service UPnP IRCC (Sony)", "BRAVIA")

        // Philips
        if (has("philips", "tp vision")) c += Clue(Vendor.PHILIPS, StrategyIds.PHILIPS, 0.75, "UPnP/Bluetooth : Philips / TP Vision", "JointSpace")
        if (mdnsTypes.any { it.contains("philips") }) c += Clue(Vendor.PHILIPS, StrategyIds.PHILIPS, 0.8, "mDNS Philips")
        if (has(1925) || has(1926)) c += Clue(Vendor.PHILIPS, StrategyIds.PHILIPS, 0.5, "port 1925/1926 ouvert")

        // Vizio
        if (has("vizio")) c += Clue(Vendor.VIZIO, StrategyIds.VIZIO, 0.75, "UPnP/Bluetooth : Vizio", "SmartCast")
        if (mdnsTypes.any { it.contains("vizio") }) c += Clue(Vendor.VIZIO, StrategyIds.VIZIO, 0.8, "mDNS Vizio")
        if (has(7345)) c += Clue(Vendor.VIZIO, StrategyIds.VIZIO, 0.5, "port 7345 ouvert")
        else if (has(9000)) c += Clue(Vendor.VIZIO, StrategyIds.VIZIO, 0.3, "port 9000 ouvert")

        // Android TV / Google TV remote service (also a clue next to another maker: Sony, Philips, TCL… run Android TV)
        mdns("_androidtvremote2")?.let { c += Clue(Vendor.ANDROID_TV, StrategyIds.ANDROID_TV, 0.9, "mDNS _androidtvremote2._tcp", "Android TV") }
        if (has(6466) || has(6467)) c += Clue(Vendor.ANDROID_TV, StrategyIds.ANDROID_TV, 0.6, "ports 6466/6467 ouverts", "Android TV")

        // Generic UPnP media renderer (any maker)
        val renderer = up?.serviceTypes?.let { t -> t.any { it.contains("AVTransport") } && t.any { it.contains("RenderingControl") } } == true
        if (renderer) c += Clue(null, StrategyIds.DLNA, 0.5, "UPnP : AVTransport + RenderingControl")

        // MAC prefix: weak
        Oui.vendorOf(h.mac)?.let { v -> c += Clue(v, strategyOf(v), 0.25, "préfixe MAC (OUI) ${v.label}") }

        return build(h, c)
    }

    private fun strategyOf(v: Vendor) = when (v) {
        Vendor.SAMSUNG -> StrategyIds.SAMSUNG; Vendor.LG -> StrategyIds.LG; Vendor.ROKU -> StrategyIds.ROKU; Vendor.SONY -> StrategyIds.SONY
        Vendor.PHILIPS -> StrategyIds.PHILIPS; Vendor.VIZIO -> StrategyIds.VIZIO; Vendor.CVTE -> StrategyIds.CVTE
        Vendor.ANDROID_TV -> StrategyIds.ANDROID_TV; Vendor.CASTBRIDGE -> StrategyIds.CASTBRIDGE; Vendor.UNKNOWN -> null
    }

    private fun noisyOr(ws: List<Double>) = 1.0 - ws.fold(1.0) { a, w -> a * (1.0 - w) }

    private fun build(h: TvHints, clues: List<Clue>): TvFingerprint {
        val byVendor = clues.filter { it.vendor != null }.groupBy { it.vendor!! }.mapValues { noisyOr(it.value.map { c -> c.weight }) }
        // Android TV is a platform, not a maker: a maker clue (Sony, Philips…) wins the identity when it is at least as plausible.
        val best = byVendor.entries.sortedWith(compareByDescending<Map.Entry<Vendor, Double>> { it.value }
            .thenBy { if (it.key == Vendor.ANDROID_TV) 1 else 0 }).firstOrNull()
        val vendor = best?.takeIf { it.value >= 0.3 }?.key ?: Vendor.UNKNOWN
        val maker = byVendor.entries.filter { it.key != Vendor.ANDROID_TV && it.key != Vendor.CASTBRIDGE && it.value >= 0.3 }.maxByOrNull { it.value }?.key
        val identity = if (vendor == Vendor.ANDROID_TV && maker != null) maker else vendor
        val conf = (if (identity == vendor) best?.value else byVendor[identity]) ?: 0.0
        // Strategy ranking: score = noisy-or of the clues pointing at it; ties keep the default order. CastBridge-TV is always first.
        val score = clues.filter { it.strategy != null }.groupBy { it.strategy!! }.mapValues { noisyOr(it.value.map { c -> c.weight }) }
        val order = StrategyIds.DEFAULT_ORDER
        val candidates = score.entries.filter { it.value >= 0.3 || it.key == StrategyIds.CASTBRIDGE }
            .sortedWith(compareBy<Map.Entry<String, Double>> { if (it.key == StrategyIds.CASTBRIDGE) 0 else 1 }
                .thenByDescending { it.value }.thenBy { order.indexOf(it.key) }).map { it.key }
        val family = clues.firstOrNull { it.vendor == identity && it.family != null }?.family
            ?: if (identity == Vendor.ANDROID_TV || (byVendor[Vendor.ANDROID_TV] ?: 0.0) >= 0.5) "Android TV" else null
        val model = h.upnp?.modelName ?: h.upnp?.modelNumber ?: h.mdns.firstNotNullOfOrNull { it.txt["device_name"] ?: it.txt["md"] }
        return TvFingerprint(
            vendor = identity, family = family, model = model, confidence = Math.round(conf * 100) / 100.0,
            evidence = clues.sortedByDescending { it.weight }.map { it.text }.distinct(), candidates = candidates,
            openPorts = h.openPorts, upnp = h.upnp, hints = h,
        )
    }
}
