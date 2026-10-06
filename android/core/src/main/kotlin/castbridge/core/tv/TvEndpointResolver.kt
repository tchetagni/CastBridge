package castbridge.core.tv

/** Par quelle voie le téléphone joint la TV. L'ordre est la priorité ; [LAST_GOOD] = la dernière adresse qui a répondu (encore fraîche). */
enum class EndpointKind { LAN, WIFI_DIRECT, BLUETOOTH, LAST_GOOD }
data class Endpoint(val kind: EndpointKind, val base: String)

/**
 * R-18 : LA source de l'adresse utilisable de la TV appairée, partagée par le service d'envoi (`runFast`, voies), la session de diffusion et la file.
 * Les [sources] (découverte du réseau, adresse manuelle, groupe Wi-Fi Direct, passerelle Bluetooth) donnent des adresses vivantes ; la meilleure par
 * priorité gagne (réseau local > Wi-Fi Direct > Bluetooth). Si aucune n'est vivante (découverte vidée par un changement de réseau, annonce mDNS
 * perdue un instant), l'adresse qui a répondu il y a moins de [staleMs] reste valable : une TV qui répond n'est pas « introuvable ».
 * Pure : l'horloge et les sources sont données.
 *
 * R-19 : quand aucune source vivante ne répond, les [fallbacks] (adresse Wi-Fi de la liaison de confiance — celle où l'écran voit la TV « Connectée » —,
 * puis la dernière adresse qui a répondu pour ce nom, persistée 10 min) sont essayés AVANT de rendre null : l'envoi ne dit plus « TV introuvable » tant
 * que l'écran voit la TV. Avec un [probe] (« hello » HTTP court), une adresse n'est retenue que si elle répond (résultat gardé [probeCacheMs]) ; une
 * adresse vivante qui ne répond plus (bail DHCP changé) cède la place à un repli qui répond, et reste essayée en dernier si rien ne répond.
 * Sans [probe], tout est pris tel quel (comportement R-18).
 */
class TvEndpointResolver(
    private val clock: () -> Long,
    private val staleMs: Long = 30_000,
    private val fallbacks: () -> List<String?> = { emptyList() },
    private val probe: ((String) -> Boolean)? = null,
    private val probeCacheMs: Long = 5_000,
    private val sources: () -> List<String?>,
) {
    @Volatile private var picked: Endpoint? = null
    @Volatile private var good: String? = null
    @Volatile private var goodAt = 0L
    private val probed = HashMap<String, Pair<Boolean, Long>>()

    @Synchronized fun current(): Endpoint? {
        val live = sources().filterNotNull().distinct().map { Endpoint(kindOf(it), it) }.sortedBy { it.kind.ordinal }
        val lastGood = good?.takeIf { clock() - goodAt <= staleMs }
        val e = if (probe == null) {
            live.firstOrNull() ?: lastGood?.let { Endpoint(EndpointKind.LAST_GOOD, it) }
                ?: fallbacks().firstOrNull { it != null }?.let { Endpoint(kindOf(it), it) }
        } else {
            live.firstOrNull { answers(it.base, lastGood) }
                ?: lastGood?.let { Endpoint(EndpointKind.LAST_GOOD, it) }
                ?: fallbacks().filterNotNull().distinct().filter { b -> live.none { it.base == b } }.firstOrNull { answers(it, lastGood) }?.let { Endpoint(kindOf(it), it) }
                ?: live.firstOrNull()
        }
        picked = e
        return e
    }

    /** The address answered a moment ago (bytes confirmed, or a « hello » kept [probeCacheMs]); else one « hello » now. */
    private fun answers(base: String, lastGood: String?): Boolean {
        if (base == lastGood) return true
        val now = clock()
        probed[base]?.let { (ok, at) -> if (now - at < probeCacheMs) return ok }
        val ok = runCatching { probe!!(base) }.getOrDefault(false)
        probed[base] = ok to now
        return ok
    }

    fun base(): String? = current()?.base

    /** La TV vient de répondre (octets confirmés, appel réussi) à la dernière adresse rendue : elle reste « vivante » [staleMs] de plus. */
    fun answered() {
        val p = picked ?: return
        good = p.base; goodAt = clock()
    }

    companion object {
        fun kindOf(base: String): EndpointKind {
            val host = base.removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':')
            return when {
                host == "127.0.0.1" || host == "localhost" -> EndpointKind.BLUETOOTH
                host.startsWith("192.168.49.") -> EndpointKind.WIFI_DIRECT
                else -> EndpointKind.LAN
            }
        }
    }
}

/**
 * R-18 : quand le service d'envoi peut-il dire « TV introuvable » ? Jamais tant qu'une voie du même envoi a progressé il y a moins de
 * [progressWindowMs] ; un manque isolé est réessayé en silence pendant [silentMs] ; ensuite il est dit (même mot partout) ; après [giveUpMs]
 * l'attente s'arrête sur un échec visible (plus de boucle sans fin ni sans voix).
 */
class MissingTvGate(private val clock: () -> Long, private val progressWindowMs: Long = 10_000, private val silentMs: Long = 15_000, private val giveUpMs: Long = 600_000) {
    enum class Verdict { SILENT, REPORT, GIVE_UP }
    @Volatile private var lastProgress = Long.MIN_VALUE / 2
    @Volatile private var missSince = -1L

    fun progress() { lastProgress = clock() }
    fun found() { missSince = -1L }
    fun recentProgress(): Boolean = clock() - lastProgress <= progressWindowMs

    fun miss(): Verdict {
        val now = clock()
        if (missSince < 0) missSince = now
        if (recentProgress()) return Verdict.SILENT
        val elapsed = now - missSince
        return when { elapsed >= giveUpMs -> Verdict.GIVE_UP; elapsed < silentMs -> Verdict.SILENT; else -> Verdict.REPORT }
    }
}

/** La boucle d'attente de la TV de `UploadService.runFast`, testable : horloge, sommeil et rapports sont donnés. */
object TvWait {
    enum class Outcome { FOUND, CANCELLED, GAVE_UP }
    const val WAITING_REASON = "TV introuvable"
    /** The visible end of the wait (R-18); the queue ([ResumeWait]) puts the file back and relaunches it as soon as the TV answers again (R-19). */
    const val GAVE_UP_TEXT = "La TV reste introuvable : vérifiez qu'elle est allumée, sur le même Wi-Fi, puis réessayez"

    fun until(resolve: () -> String?, gate: MissingTvGate, cancelled: () -> Boolean, sleep: (Long) -> Unit, onReport: (String) -> Unit, onGiveUp: () -> Unit): Outcome {
        while (!cancelled()) {
            if (resolve() != null) { gate.found(); return Outcome.FOUND }
            when (gate.miss()) {
                MissingTvGate.Verdict.GIVE_UP -> { onGiveUp(); return Outcome.GAVE_UP }
                MissingTvGate.Verdict.REPORT -> onReport(WAITING_REASON)
                MissingTvGate.Verdict.SILENT -> {}
            }
            sleep(1000)
        }
        return Outcome.CANCELLED
    }
}
