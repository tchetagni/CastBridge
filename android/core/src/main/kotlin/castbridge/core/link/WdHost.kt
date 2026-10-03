package castbridge.core.link

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.LinkInfo
import castbridge.core.tv.LinkPlanner
import castbridge.core.tv.WifiDirect

/**
 * Adresses qu'une TV peut annoncer comme « réseau commun » : jamais celles d'un groupe Wi-Fi Direct (192.168.49.x, ou toute adresse du groupe courant).
 * Sinon un téléphone déjà dans le groupe prendrait 192.168.49.1 pour un LAN : la file sauterait le Wi-Fi Direct et la route de contrôle mourrait avec le
 * groupe (audit R-14, I-1). Appliqué par la TV (HELLO/CBTN) ET par le téléphone ([LinkPlanner.plan], [BulkRoute.lanRoute]) : défense en profondeur.
 */
object HelloIps {
    const val GROUP_PREFIX = "192.168.49."
    fun isLan(ip: String, groupIps: Set<String> = emptySet()) = !ip.startsWith(GROUP_PREFIX) && ip !in groupIps
    fun lanOnly(ips: List<String>, groupIps: Set<String> = emptySet()): List<String> = ips.filter { isLan(it, groupIps) }
}

/**
 * Ce qui fait vivre le groupe automatique de la TV : seulement les réceptions HTTP en cours (`/api/upload`, `/api/transfer`), les seules qui passent par
 * le groupe. Une télécommande Bluetooth ouverte (CBTR) ou une réception Bluetooth ne le gardent pas (audit R-14, I-2).
 */
object LeaseBusy {
    @Suppress("UNUSED_PARAMETER")
    fun count(httpTransfers: Int, btReceptions: Int, remoteSessions: Int): Int = maxOf(0, httpTransfers)
}

/** Le groupe Wi-Fi Direct tel que la TV Android le pilote (`R/WifiDirectGroup.kt`) ; un faux en test. */
interface WdGroupDriver {
    /** (nom du réseau, mot de passe) tant que le groupe existe. */
    val active: Pair<String, String>?
    /** Créé pour un téléphone (et non depuis le MENU). */
    val auto: Boolean
    val createdAt: Long
    val lastError: String?
    fun capable(): Boolean
    fun hasPermission(): Boolean
    /** Asynchrone : [active] devient non nul plus tard (rappel d'Android). */
    fun start(forPhone: Boolean)
    fun stop()
}

/**
 * Le côté TV du Wi-Fi Direct automatique, testable (R-14) : réponse CBTN/HELLO, une seule création à la fois (deux CBTN simultanés reçoivent le MÊME
 * groupe), un bail PAR TÉLÉPHONE (le WD_RELEASE d'un téléphone ne supprime pas le groupe d'un autre ; un tiers qui n'a rien demandé ne fait rien), jamais en
 * version d'essai, le mot de passe d'un groupe automatique jamais dans le HELLO. `R/TvService.kt` n'y ajoute que les adresses et l'horloge.
 */
class TvWdHost(
    private val driver: WdGroupDriver,
    private val clock: () -> Long,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val waitMs: Long = 8_000,
) {
    private val createLock = Any()
    private val state = Any()
    private val holders = LinkedHashSet<String>()
    @Volatile private var creating = false
    @Volatile var lastUse = 0L; private set

    fun holderCount(): Int = synchronized(state) { holders.size }

    /** [peer] null = la réponse HELLO ; sinon la réponse CBTN à ce téléphone (déjà contrôlé : de confiance ou PIN, lien appairé). */
    fun answer(peer: String?, flags: Int, ips: List<String>, port: Int, trial: Boolean, ownerOn: Boolean): LinkInfo {
        val lan = HelloIps.lanOnly(ips)
        val cap = driver.capable() && !trial
        fun info(give: Boolean, err: String? = null): LinkInfo {
            val a = driver.active?.takeIf { give }
            return LinkInfo(port, lan, a?.first, a?.second, a?.let { WifiDirect.GROUP_OWNER_IP }, wdCap = cap, wdErr = err)
        }
        // HELLO: only the owner's group (its password is on the TV's screen anyway), never on a trial
        if (peer == null) return info(give = !trial && driver.active != null && !driver.auto)
        if (flags and BtProtocol.WD_RELEASE != 0) {
            synchronized(state) { holders.remove(peer) }
            return info(give = false)
        }
        if (flags and BtProtocol.WANT_WIFI_DIRECT == 0) return info(give = !trial && driver.active != null && !driver.auto)
        if (trial) return info(give = false, err = WifiDirect.Err.TRIAL)            // before any claim
        var err: String? = null
        synchronized(createLock) {
            if (driver.active == null) {
                when {
                    !LinkPlanner.mayStartWifiDirect(true, ownerOn, lan.isNotEmpty(), flags and BtProtocol.WD_LAN_UNREACHABLE != 0) -> err = WifiDirect.Err.POLICY
                    !driver.hasPermission() -> err = WifiDirect.Err.PERMISSION
                    else -> {
                        creating = true
                        try {
                            synchronized(state) { holders.clear() }                 // a new group: the old leases are gone with the old one
                            driver.start(forPhone = !ownerOn)
                            val until = clock() + waitMs
                            while (driver.active == null && clock() < until) sleep(200)
                            if (driver.active == null) err = driver.lastError ?: WifiDirect.Err.FAILED
                        } finally { creating = false }
                    }
                }
            }
            if (err == null && driver.active != null) synchronized(state) { holders += peer; lastUse = clock() }
        }
        return info(give = err == null && driver.active != null, err = err)
    }

    /** Toutes les 5 s (et après un WD_RELEASE) : [busy] = [LeaseBusy.count], [clients] = téléphones associés (null = inconnu). true = groupe supprimé. */
    fun leaseCheck(busy: Int, clients: Int?): Boolean {
        if (creating || driver.active == null || !driver.auto) return false
        val now = clock()
        val remove = synchronized(state) {
            if (busy > 0) lastUse = now
            WdGroupLease.shouldRemove(WdGroupLease.Facts(true, driver.createdAt, lastUse, busy, clients, holders.size, now))
                .also { if (it) holders.clear() }
        }
        if (remove) driver.stop()
        return remove
    }
}
