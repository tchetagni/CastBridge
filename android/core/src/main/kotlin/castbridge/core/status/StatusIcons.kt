package castbridge.core.status

import castbridge.core.net.NetState

/** What an icon of the TV status bar stands for. [order] fixes the position in the bar (Internet first), whatever the arrival order. */
enum class IconKind(val wire: String, val label: String, val order: Int) {
    INTERNET("internet", "Internet", 0),
    PHONE("phone", "Téléphone", 1),
    REMOTE_CONTROL("remote", "Télécommande", 2),
    SSH("ssh", "SSH", 3),
    GATEWAY("gateway", "Internet du téléphone", 4),
    CAST("cast", "Diffusion", 5),
    USB_DRIVE("usb", "Clé USB", 6),
    WIFI_DIRECT_GROUP("wifi_direct", "Wi-Fi Direct", 7),
    QUIZ_PLAYER("quiz", "Quiz", 8),
    CHESS_PLAYER("chess", "Échecs", 9),
    DOWNLOAD("download", "Téléchargement", 10),
    PARENTAL_MODE("parental", "Mode enfant", 11),
    UPDATE("update", "Mise à jour", 12),
}

/** How a connection reaches the TV. [rank]: higher = better link (the one an icon shows; the next one becomes the small secondary mark). */
enum class Tech(val wire: String, val label: String, val rank: Int) {
    ETHERNET("ethernet", "Ethernet", 7),
    WIFI_LAN("wifi_lan", "Wi-Fi", 6),
    WIFI_DIRECT("wifi_direct", "Wi-Fi Direct", 5),
    USB("usb", "USB", 4),
    SSH_OVER_LAN("ssh_lan", "SSH (Wi-Fi)", 3),
    BLUETOOTH_TUNNEL("bluetooth_tunnel", "Bluetooth (tunnel)", 2),
    SSH_OVER_BLUETOOTH("ssh_bluetooth", "SSH (Bluetooth)", 1),
    BLUETOOTH("bluetooth", "Bluetooth", 1),
    NONE("none", "", 0);

    companion object {
        /** Technology of a peer from the address it connects from: the Wi-Fi Direct group is 192.168.49.x, anything else is the LAN. */
        fun fromIp(ip: String?): Tech = if (ip != null && ip.startsWith("192.168.49.")) WIFI_DIRECT else WIFI_LAN
    }
}

enum class IconState(val wire: String, val label: String) {
    CONNECTED("connected", ""),
    CONNECTING("connecting", "connexion…"),
    DEGRADED("degraded", "reconnexion"),
    ERROR("error", "erreur"),
}

/** One icon of the bar. [ref] is the internal key of the device (an address): never shown, never in JSON. */
data class StatusIcon(
    val kind: IconKind, val tech: Tech, val secondary: Tech?, val label: String, val state: IconState,
    val since: Long, val count: Int = 1, val latencyMs: Long? = null, val ref: String = "",
) {
    /** Stable id for the views (one per device and kind). */
    val id get() = kind.wire + ":" + ref

    /** Short French text of the chip (shown only when the bar is expanded, focused or just changed), colour never being the only signal. */
    fun text(): String {
        val head = when {
            kind == IconKind.INTERNET -> label
            count > 1 && (kind == IconKind.SSH) -> "SSH ×$count"
            else -> label
        }
        val mark = if (kind != IconKind.INTERNET && tech != Tech.NONE && tech.label.isNotEmpty()) " · ${tech.label}" else ""
        return head + mark + (if (state == IconState.DEGRADED || state == IconState.ERROR) " · ${state.label}" else "")
    }

    /** « depuis 3 min » for the Connexions panel. */
    fun sinceText(now: Long): String {
        val s = ((now - since) / 1000).coerceAtLeast(0)
        return when { s < 60 -> "depuis moins d'une minute"; s < 3600 -> "depuis ${s / 60} min"; else -> "depuis ${s / 3600} h ${(s % 3600) / 60} min" }
    }
}

/** What the bar shows: the visible icons in a stable order, and how many more did not fit (« +N »). */
data class StatusBar(val icons: List<StatusIcon>, val hidden: Int, val all: List<StatusIcon>)

/**
 * The permanent status bar of the TV, as a pure model: the TV service tells it what is connected ([up] / [down], [setInternet],
 * [setSsh]), the screen reads [snapshot]. No threads, no Android: time comes from [clock].
 *
 * - One icon per device and kind: a phone linked by Bluetooth and Wi-Fi is ONE icon (best technology + small secondary mark).
 * - A link that drops (or whose lease is not renewed) keeps its icon as DEGRADED (« reconnexion ») for [holdMs]; only a stable
 *   absence removes it, and a return inside that time keeps the same icon and the same « depuis ».
 * - At most [maxVisible] icons, then a « +N » chip; an icon in ERROR is never the one that is hidden.
 * - Labels are sanitized ([cleanLabel]); the order never depends on arrival order or on the state.
 */
class StatusIconModel(private val clock: () -> Long, val holdMs: Long = 20_000, val maxVisible: Int = 6) {
    private class Link(var state: IconState, var label: String, var count: Int, var latencyMs: Long?, var endsAt: Long?)
    private class Entry(val kind: IconKind, val ref: String, var since: Long) { val links = LinkedHashMap<Tech, Link>() }

    private val entries = LinkedHashMap<String, Entry>()
    private var internet: NetState? = null
    private var internetSince = 0L

    /**
     * A link of [kind] to the device [ref] (an opaque key, e.g. the Bluetooth address) through [tech]. Calling it again renews it.
     * [leaseMs]: the link is considered gone after that time unless renewed (for peers that never say goodbye); null = until [down].
     */
    @Synchronized fun up(kind: IconKind, ref: String, tech: Tech, label: String, state: IconState = IconState.CONNECTED,
                         count: Int = 1, latencyMs: Long? = null, leaseMs: Long? = null) {
        val now = clock()
        val e = entries.getOrPut(kind.wire + ":" + ref) { Entry(kind, ref, now) }
        if (e.links.isNotEmpty() && !alive(e, now) && now - lastEnd(e) >= holdMs) e.links.clear()   // gone for good: a new life, a new « depuis »
        if (e.links.isEmpty()) e.since = now
        e.links[tech] = Link(state, cleanLabel(label, kind.label), count.coerceAtLeast(1), latencyMs, leaseMs?.let { now + it })
    }

    /** The link through [tech] ended (null = every link of that device). The icon stays as DEGRADED for [holdMs]. */
    @Synchronized fun down(kind: IconKind, ref: String, tech: Tech? = null) {
        val now = clock()
        val e = entries[kind.wire + ":" + ref] ?: return
        for ((t, l) in e.links) if (tech == null || t == tech) { if (l.endsAt == null || l.endsAt!! > now) l.endsAt = now }
    }

    /** Immediate removal (the thing is over for good: a quiz room closed, SSH turned off). */
    @Synchronized fun remove(kind: IconKind, ref: String) { entries.remove(kind.wire + ":" + ref) }

    /** The TV's Internet (already debounced by NetStateTracker): one fixed entry, never held, never removed. */
    @Synchronized fun setInternet(st: NetState) {
        if (st != internet) { internet = st; internetSince = clock() }
    }

    /** SSH sessions by path (the SSH policy only counts them: [viaBluetooth] is what the Bluetooth tunnel carries). */
    @Synchronized fun setSsh(total: Int, viaBluetooth: Int = 0) {
        if (total <= 0) { down(IconKind.SSH, ""); return }
        val bt = viaBluetooth.coerceIn(0, total)
        if (total - bt > 0) up(IconKind.SSH, "", Tech.SSH_OVER_LAN, "SSH", count = total - bt) else down(IconKind.SSH, "", Tech.SSH_OVER_LAN)
        if (bt > 0) up(IconKind.SSH, "", Tech.SSH_OVER_BLUETOOTH, "SSH", count = bt) else down(IconKind.SSH, "", Tech.SSH_OVER_BLUETOOTH)
    }

    private fun alive(e: Entry, now: Long) = e.links.values.any { it.endsAt == null || it.endsAt!! > now }
    private fun lastEnd(e: Entry) = e.links.values.maxOf { it.endsAt ?: Long.MAX_VALUE }

    @Synchronized fun snapshot(): StatusBar {
        val now = clock()
        val it = entries.values.iterator()
        while (it.hasNext()) { val e = it.next(); if (e.links.isEmpty() || (!alive(e, now) && now - lastEnd(e) >= holdMs)) it.remove() }
        val all = ArrayList<StatusIcon>()
        internet?.let { all += internetIcon(it) }
        for (e in entries.values) all += icon(e, now)
        all.sortWith(compareBy({ it.kind.order }, { it.since }, { it.ref }))
        if (all.size <= maxVisible) return StatusBar(all, 0, all)
        // the cap keeps errors first, then the canonical order
        val keep = (all.filter { it.state == IconState.ERROR } + all.filter { it.state != IconState.ERROR }).take(maxVisible).toSet()
        return StatusBar(all.filter { it in keep }, all.size - maxVisible, all)
    }

    private fun internetIcon(st: NetState) = StatusIcon(IconKind.INTERNET, when (st) {
        NetState.INTERNET_WIFI -> Tech.WIFI_LAN; NetState.INTERNET_ETHERNET -> Tech.ETHERNET
        NetState.INTERNET_VIA_PHONE -> Tech.BLUETOOTH; else -> Tech.NONE }, null, st.label,
        when (st) { NetState.NONE -> IconState.ERROR; NetState.CHECKING -> IconState.CONNECTING; else -> IconState.CONNECTED }, internetSince)

    private fun icon(e: Entry, now: Long): StatusIcon {
        val live = e.links.filter { it.value.endsAt == null || it.value.endsAt!! > now }
        val pool = live.ifEmpty { e.links }
        val ranked = pool.entries.sortedByDescending { it.key.rank }
        val best = ranked.first()
        val state = when {
            live.isEmpty() -> IconState.DEGRADED
            live.values.any { it.state == IconState.CONNECTED } -> IconState.CONNECTED
            live.values.any { it.state == IconState.CONNECTING } -> IconState.CONNECTING
            else -> live.values.first().state
        }
        return StatusIcon(e.kind, best.key, ranked.getOrNull(1)?.key?.takeIf { it != best.key }, best.value.label, state, e.since,
            pool.values.sumOf { it.count }, best.value.latencyMs, e.ref)
    }

    companion object {
        const val MAX_LABEL = 24

        /** A name safe to draw: no control / invisible / bidi characters, single spaces, at most [MAX_LABEL] characters (« … »). */
        fun cleanLabel(raw: String?, fallback: String): String {
            val sb = StringBuilder()
            var space = false
            raw.orEmpty().codePoints().forEach { cp ->
                val bad = Character.isISOControl(cp) || Character.getType(cp).let { it == Character.FORMAT.toInt() || it == Character.LINE_SEPARATOR.toInt() ||
                    it == Character.PARAGRAPH_SEPARATOR.toInt() || it == Character.SURROGATE.toInt() || it == Character.UNASSIGNED.toInt() }
                if (bad && !Character.isWhitespace(cp)) return@forEach
                if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) { space = sb.isNotEmpty(); return@forEach }
                if (space) { sb.append(' '); space = false }
                sb.appendCodePoint(cp)
            }
            val s = sb.toString()
            if (s.isBlank()) return fallback
            val n = s.codePointCount(0, s.length)
            return if (n <= MAX_LABEL) s else s.substring(0, s.offsetByCodePoints(0, MAX_LABEL - 1)) + "…"
        }

        /** Body of GET /api/connections: labels, technologies, states and start times only — no token, PIN, address or [StatusIcon.ref]. */
        fun json(bar: StatusBar, now: Long): String {
            fun q(s: String) = buildString { append('"'); for (c in s) when { c == '"' -> append("\\\""); c == '\\' -> append("\\\\"); c < ' ' -> append(' '); else -> append(c) }; append('"') }
            val items = bar.all.joinToString(",") { i ->
                """{"kind":"${i.kind.wire}","technology":"${i.tech.wire}","secondary":${i.secondary?.let { "\"${it.wire}\"" } ?: "null"},""" +
                    """"label":${q(i.label)},"state":"${i.state.wire}","since":${i.since},"count":${i.count},"latencyMs":${i.latencyMs ?: "null"}}"""
            }
            return """{"connections":[$items],"visible":${bar.icons.size},"hidden":${bar.hidden},"now":$now}"""
        }
    }
}
