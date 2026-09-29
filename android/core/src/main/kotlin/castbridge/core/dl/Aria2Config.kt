package castbridge.core.dl

import java.io.File

/** Download settings the user can change (persisted by [DownloadManager] in its work folder). */
data class DlSettings(
    /** Bytes/s, 0 = unlimited. */
    val downLimit: Long = 0,
    /**
     * Bytes/s, 0 = unlimited. Default 512 kB/s: a home ADSL/4G uplink is small, and BitTorrent peers would otherwise fill it
     * and slow down the whole household (and the TV's own downloads, whose TCP acknowledgements share that uplink).
     */
    val upLimit: Long = 512L * 1024,
    /** Keep sharing a torrent after it is complete (off by default: the file goes to the library at once). */
    val seeding: Boolean = false,
    /** Two at once: more would split the slow USB drive's bandwidth and multiply aria2's buffers in 1 GB of RAM. */
    val maxConcurrent: Int = 2,
    /** The "only download what you have the right to" notice was acknowledged. */
    val warningAccepted: Boolean = false,
) {
    fun toJson(): String = Json.write(mapOf("downLimit" to downLimit, "upLimit" to upLimit, "seeding" to seeding,
        "maxConcurrent" to maxConcurrent, "warningAccepted" to warningAccepted))

    companion object {
        fun parse(s: String): DlSettings = runCatching {
            val m = Json.parse(s).obj()
            DlSettings(m.n("downLimit").coerceAtLeast(0), if (m.containsKey("upLimit")) m.n("upLimit").coerceAtLeast(0) else 512L * 1024,
                m.b("seeding"), m.n("maxConcurrent").toInt().takeIf { it in 1..3 } ?: 2, m.b("warningAccepted"))
        }.getOrDefault(DlSettings())
    }
}

/**
 * aria2 command line and the options the API may pass on. Every choice below is for a 1 GB RAM, 32-bit TV writing to a
 * slow exFAT USB drive; see docs/DOWNLOADS.md for the reasoning behind each one.
 */
object Aria2Config {
    /** Seeding when the user turns it on: until a ratio of 1.0, and at most 2 hours. */
    const val SEED_RATIO_ON = "1.0"
    const val SEED_TIME_ON_MIN = 120

    /**
     * Command line. The RPC secret is NOT here (it would be visible in /proc/<pid>/cmdline): it goes in [confFile],
     * written with owner-only permissions and deleted once aria2 has read it.
     */
    fun args(
        confFile: File, session: File, defaultDir: File, port: Int, parentPid: Long, settings: DlSettings,
        caBundle: File?, dnsServers: List<String>, dhtFile: File,
    ): List<String> = buildList {
        add("--conf-path=${confFile.absolutePath}")
        add("--enable-rpc=true")
        add("--rpc-listen-all=false")                  // 127.0.0.1 only: the CastBridge API is the only way in
        add("--rpc-listen-port=$port")
        add("--rpc-allow-origin-all=false")
        add("--rpc-max-request-size=8M")               // a .torrent of up to 4 MB, base64-encoded
        add("--rpc-save-upload-metadata=true")         // an uploaded .torrent survives a restart (saved in the task folder)
        add("--dir=${defaultDir.absolutePath}")        // every task gets its own dir; this is only a fallback
        add("--input-file=${session.absolutePath}")
        add("--save-session=${session.absolutePath}")
        add("--save-session-interval=30")
        add("--force-save=false")
        add("--continue=true")
        add("--max-concurrent-downloads=${settings.maxConcurrent}")
        add("--max-connection-per-server=8")
        add("--split=8")
        add("--min-split-size=4M")                     // no 8-way split of small files (useless connections, random writes)
        add("--stream-piece-selector=inorder")         // HTTP/FTP pieces written near the front: FAT/exFAT never zero-fills a gap
        add("--file-allocation=none")                  // exFAT has no fallocate; "prealloc" would write the whole file twice
        add("--disk-cache=8M")                         // bigger, fewer writes to the slow drive, for 8 MB of RAM
        add("--check-certificate=true")
        caBundle?.let { add("--ca-certificate=${it.absolutePath}") }
        add("--min-tls-version=TLSv1.2")
        add("--seed-ratio=${if (settings.seeding) SEED_RATIO_ON else "0.0"}")
        add("--seed-time=${if (settings.seeding) SEED_TIME_ON_MIN else 0}")   // 0 = stop sharing as soon as it is complete
        add("--bt-detach-seed-only=true")
        add("--bt-max-peers=30")                       // default 55: fewer sockets and buffers on a 1 GB TV
        add("--bt-save-metadata=true")                 // a magnet's metadata is kept: no second lookup after a restart
        add("--pause-metadata=true")                   // stop after the metadata: the TV checks the space before the real download
        add("--bt-enable-lpd=false")
        add("--enable-dht=true")
        add("--enable-dht6=false")                     // IPv6 DHT rarely works behind a home box and costs another socket and table
        add("--dht-file-path=${dhtFile.absolutePath}")
        add("--listen-port=6881-6889")
        add("--dht-listen-port=6881-6889")
        add("--max-overall-download-limit=${settings.downLimit}")
        add("--max-overall-upload-limit=${settings.upLimit}")
        add("--max-tries=10")
        add("--retry-wait=15")                         // Wi-Fi of a TV comes and goes: retry calmly instead of failing
        add("--connect-timeout=30")
        add("--max-download-result=100")
        add("--auto-file-renaming=true")
        add("--allow-overwrite=false")
        add("--remote-time=false")
        add("--no-netrc=true")
        add("--console-log-level=warn")
        add("--show-console-readout=false")
        add("--summary-interval=0")
        add("--enable-color=false")
        add("--stop-with-process=$parentPid")          // aria2 exits by itself if the app process dies: never an orphan
        if (dnsServers.isNotEmpty()) {
            // Built with c-ares: Android has no /etc/resolv.conf, so tell it which servers the TV uses.
            add("--async-dns=true")
            add("--async-dns-server=${dnsServers.joinToString(",")}")
        } else add("--async-dns=false")
    }

    /** aria2.conf content: the secret only. */
    fun conf(secret: String): String = "rpc-secret=$secret\n"

    // ---- options the API accepts (whitelist; anything else is refused) ----

    sealed class Check {
        data class Ok(val options: Map<String, String>) : Check()
        data class Bad(val message: String) : Check()
    }

    private val SPEED = Regex("^\\d{1,9}[KkMm]?$")
    private val INT = Regex("^\\d{1,6}$")
    private val RATIO = Regex("^\\d{1,2}(\\.\\d{1,2})?$")
    private val SELECT = Regex("^\\d{1,5}(-\\d{1,5})?(,\\d{1,5}(-\\d{1,5})?)*$")
    private val CHECKSUM = Regex("^(sha-1|sha-224|sha-256|sha-384|sha-512|md5|adler32)=[0-9a-fA-F]{8,128}$")

    private fun intIn(lo: Int, hi: Int) = { v: String -> INT.matches(v) && v.toInt() in lo..hi }
    private val speed = { v: String -> SPEED.matches(v) }
    private val text = { v: String -> v.length <= 512 }

    /** Per-download options a client may set. `dir`, hooks (`on-*`), paths and RPC options are never accepted. */
    val TASK: Map<String, (String) -> Boolean> = mapOf(
        "max-download-limit" to speed,
        "max-upload-limit" to speed,
        "max-connection-per-server" to intIn(1, 16),
        "split" to intIn(1, 16),
        "min-split-size" to { v -> Regex("^\\d{1,4}[Mm]$").matches(v) && v.dropLast(1).toInt() in 1..1024 },
        "seed-ratio" to { v -> RATIO.matches(v) && v.toDouble() <= 10.0 },
        "seed-time" to intIn(0, 10_080),
        "bt-max-peers" to intIn(0, 100),
        "select-file" to { v -> SELECT.matches(v) },
        "out" to { v -> v.length <= 200 && !v.startsWith(".") && v.none { it == '/' || it == '\\' } },
        "referer" to text,
        "user-agent" to text,
        "http-user" to text, "http-passwd" to text,
        "ftp-user" to text, "ftp-passwd" to text,
        "checksum" to { v -> CHECKSUM.matches(v) },
        "max-tries" to intIn(0, 100),
        "retry-wait" to intIn(0, 600),
        "lowest-speed-limit" to speed,
        "bt-tracker" to { v -> v.length <= 4096 && v.split(',').all { t -> t.matches(Regex("^(udp|http|https)://[^\\s,]+$")) } },
        "bt-prioritize-piece" to { v -> v in setOf("head", "tail", "head,tail") },
    )

    /** Global options a client may change at run time. */
    val GLOBAL: Map<String, (String) -> Boolean> = mapOf(
        "max-overall-download-limit" to speed,
        "max-overall-upload-limit" to speed,
        "max-concurrent-downloads" to intIn(1, 3),
        "max-connection-per-server" to intIn(1, 16),
        "split" to intIn(1, 16),
        "bt-max-peers" to intIn(0, 100),
        "seed-ratio" to { v -> RATIO.matches(v) && v.toDouble() <= 10.0 },
        "seed-time" to intIn(0, 10_080),
    )

    /** Validates [given] against [allowed]: unknown keys, control characters (session-file injection) and bad values are refused. */
    fun check(given: Map<String, String>, allowed: Map<String, (String) -> Boolean>): Check {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in given) {
            val rule = allowed[k] ?: return Check.Bad("Option non autorisée : $k")
            if (v.any { it < ' ' || it == '\u007f' }) return Check.Bad("Valeur invalide pour $k")
            if (!rule(v)) return Check.Bad("Valeur invalide pour $k")
            out[k] = v
        }
        return Check.Ok(out)
    }

    /** "512K" / "2M" / "1000" to bytes/s (0 = unlimited); null if malformed. */
    fun speedBytes(v: String): Long? {
        if (!SPEED.matches(v)) return null
        val n = v.trimEnd('K', 'k', 'M', 'm').toLong()
        return when (v.last()) { 'K', 'k' -> n * 1024; 'M', 'm' -> n * 1024 * 1024; else -> n }
    }
}
