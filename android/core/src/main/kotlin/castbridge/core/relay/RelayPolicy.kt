package castbridge.core.relay

import castbridge.core.connect.KeyValueStore
import java.time.Instant
import java.time.ZoneId

/**
 * Ce pour quoi la TV demande le tuyau : des opérations « vivantes », qui attendent. [bulk] = gros volume (une mise à jour de 41 Mo) : jamais sur données mobiles sans
 * réglage explicite (REL-F7) ; le reste est « petit » (quelques Ko à quelques centaines de Ko par heure).
 */
enum class PipeNeed(val wire: String, val bulk: Boolean) {
    PLAY("play", false), WALLET("wallet", false), UPDATE_NOW("update", true), ASSIST("assist", false);

    companion object { fun of(w: String?): PipeNeed? = values().firstOrNull { it.wire == w } }
}

/** Le réseau du téléphone vu par la politique de coût (Android : `NET_CAPABILITY_NOT_METERED`, `NET_CAPABILITY_VALIDATED`). */
enum class PhoneNet { NONE, UNMETERED, METERED }

/** Pourquoi le téléphone ne relaie pas (codes stables, échangés avec la TV dans `RELAY_STATE` ; additifs : un code inconnu reste lisible). */
enum class RelayReason(val wire: String) {
    NOT_SYNCED("nosync"), OPTED_OUT("optout"), OFFLINE("offline"), METERED_BULK("metered_bulk"), CAP("cap"),
    /** Android refuse de démarrer un service au premier plan depuis l'arrière-plan : l'utilisateur doit ouvrir CastBridge une fois. */
    BACKGROUND("background"),
    BUSY("busy");

    companion object { fun of(w: String?): RelayReason? = values().firstOrNull { it.wire == w } }
}

/** Tout ce que la politique a besoin de savoir, sans Android. */
data class RelayInput(
    /** Un identifiant utilisable pour cette TV : PIN gardé (PinBook) ou jeton de confiance. La synchronisation par PIN EST le consentement (décision du propriétaire). */
    val synced: Boolean,
    /** Réglage « Ne plus relayer pour cette TV » (éteint par défaut). */
    val optedOut: Boolean,
    val net: PhoneNet,
    /** Réglage « Données mobiles pour la TV » : lève le plafond et l'interdiction du bulk sur réseau facturé. */
    val allowMobile: Boolean,
    /** Octets déjà relayés aujourd'hui sur réseau facturé (compteur persisté, [RelayMeter]). */
    val usedTodayBytes: Long,
    val capBytes: Long = RelayPolicy.DEFAULT_CAP_BYTES,
    val need: PipeNeed? = null,
)

sealed class RelayDecision {
    /** Ouvrir. [limitBytes] = ce qui reste avant d'arrêter (réseau facturé), null = pas de plafond. */
    data class Open(val limitBytes: Long?) : RelayDecision()
    data class Refuse(val reason: RelayReason) : RelayDecision()
}

/**
 * Quand le téléphone ouvre le tuyau SANS rien demander à son propriétaire (relay-R1 § 3, DESIGN-RELAIS § 2.4) : TV synchronisée, relais non retiré, puis le coût.
 * Réseau non facturé : libre. Réseau facturé : le tuyau ne sert que le serveur CastBridge ([RelayScope]), 5 Mo par jour par défaut, le « bulk » jamais ; le réglage
 * « Données mobiles pour la TV » lève tout cela. Pur : aucune horloge, aucun fichier.
 */
object RelayPolicy {
    const val DEFAULT_CAP_BYTES = 5L * 1024 * 1024
    /** Le tuyau se ferme 10 minutes après la dernière connexion. */
    const val IDLE_STOP_MS = 10 * 60_000L

    fun decide(i: RelayInput): RelayDecision {
        if (!i.synced) return RelayDecision.Refuse(RelayReason.NOT_SYNCED)
        if (i.optedOut) return RelayDecision.Refuse(RelayReason.OPTED_OUT)
        return when (i.net) {
            PhoneNet.NONE -> RelayDecision.Refuse(RelayReason.OFFLINE)
            PhoneNet.UNMETERED -> RelayDecision.Open(null)
            PhoneNet.METERED -> when {
                i.allowMobile -> RelayDecision.Open(null)
                i.need?.bulk == true -> RelayDecision.Refuse(RelayReason.METERED_BULK)
                else -> {
                    val left = i.capBytes - i.usedTodayBytes
                    if (left > 0) RelayDecision.Open(left) else RelayDecision.Refuse(RelayReason.CAP)
                }
            }
        }
    }
}

/** Octets relayés AUJOURD'HUI (jour local du téléphone) sur réseau facturé : persisté, remis à zéro au changement de jour, jamais négatif ni débordant. */
class RelayMeter(private val kv: KeyValueStore, private val now: () -> Long, private val zone: ZoneId = ZoneId.systemDefault()) {
    private fun today(): String = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate().toString()

    @Synchronized fun usedToday(): Long =
        if (kv.get(DAY) == today()) (kv.get(BYTES)?.toLongOrNull() ?: 0L).coerceAtLeast(0L) else 0L

    @Synchronized fun add(bytes: Long) {
        if (bytes <= 0) return
        val used = usedToday()
        val sum = if (used > Long.MAX_VALUE - bytes) Long.MAX_VALUE else used + bytes
        kv.put(DAY, today()); kv.put(BYTES, sum.toString())
    }

    private companion object { const val DAY = "relay.day"; const val BYTES = "relay.bytes" }
}

/**
 * Arrêt du tuyau à l'inactivité : dix minutes après la dernière connexion ouverte ou le dernier vrai trafic. Les PING de mesure (14 octets) ne comptent pas : ils
 * ne doivent pas tenir le tuyau ouvert indéfiniment.
 */
class IdleStop(private val idleMs: Long = RelayPolicy.IDLE_STOP_MS, start: Long) {
    private var lastActivity = start
    private var lastBytes = 0L

    @Synchronized fun update(now: Long, openStreams: Int, totalBytes: Long) {
        if (openStreams > 0 || totalBytes - lastBytes >= NOISE_BYTES) lastActivity = now
        lastBytes = totalBytes
    }

    @Synchronized fun expired(now: Long): Boolean = now - lastActivity >= idleMs

    companion object {
        /** En dessous, la différence d'octets entre deux relevés est du bruit de mesure (PING, accusés). */
        const val NOISE_BYTES = 512L
    }
}
