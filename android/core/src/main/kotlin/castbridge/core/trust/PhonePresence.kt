package castbridge.core.trust

/**
 * TV side: which trusted phones are around, truthfully, and when to speak about it.
 *
 * A phone is "connecté" while it is seen (HELLO, a request with its token, a key of the remote) at least every [goneAfterMs], or while a link of
 * its own is open. When it stops, it becomes "déconnecté"; when it is seen again it is "reconnecté" quietly. The banner « téléphone connecté »
 * is only for the first sight and then at most once per [bannerEveryMs] per phone: reconnections after a link loss never spam.
 */
class PhonePresence(
    private val now: () -> Long = System::currentTimeMillis,
    private val goneAfterMs: Long = 60_000,
    private val bannerEveryMs: Long = 10 * 60_000,
) {
    enum class Event { NONE, BANNER, RECONNECTED }
    enum class State { CONNECTED, RECONNECTED, DISCONNECTED }
    data class Status(val address: String, val name: String, val state: State, val lastSeen: Long)

    private class P(var name: String, var lastSeen: Long, var lastBanner: Long, var open: Int = 0, var reconnectedAt: Long = 0)
    private val phones = LinkedHashMap<String, P>()

    @Synchronized fun seen(address: String, name: String = ""): Event {
        val a = TrustRegistry.norm(address); val t = now()
        val p = phones[a]
        if (p == null) { phones[a] = P(name.ifBlank { "Téléphone" }, t, t); return Event.BANNER }
        if (name.isNotBlank()) p.name = name
        val gap = t - p.lastSeen
        p.lastSeen = t
        if (gap <= goneAfterMs || p.open > 0) return Event.NONE
        return if (t - p.lastBanner >= bannerEveryMs) { p.lastBanner = t; Event.BANNER } else { p.reconnectedAt = t; Event.RECONNECTED }
    }

    @Synchronized fun linkOpened(address: String) { phones[TrustRegistry.norm(address)]?.let { it.open++; it.lastSeen = now() } }

    /** A link of this phone closed (also a half-open one the watchdog cut): after [goneAfterMs] without anything else, it is "déconnecté". */
    @Synchronized fun linkClosed(address: String) { phones[TrustRegistry.norm(address)]?.let { it.open = (it.open - 1).coerceAtLeast(0); it.lastSeen = now() } }

    @Synchronized fun forget(address: String) { phones.remove(TrustRegistry.norm(address)) }

    @Synchronized fun statuses(): List<Status> {
        val t = now()
        return phones.map { (a, p) ->
            val live = p.open > 0 || t - p.lastSeen <= goneAfterMs
            Status(a, p.name, when { !live -> State.DISCONNECTED; p.reconnectedAt > 0 && t - p.reconnectedAt <= 2 * goneAfterMs -> State.RECONNECTED; else -> State.CONNECTED }, p.lastSeen)
        }
    }

    /** One line per phone for the TV screen. */
    fun lines(): List<String> = statuses().map { s ->
        when (s.state) {
            State.CONNECTED -> "${s.name} : connecté"
            State.RECONNECTED -> "${s.name} : liaison reprise"
            State.DISCONNECTED -> "${s.name} : téléphone déconnecté"
        }
    }
}

/** What the TV tells its owner when a phone that asked to be added was not accepted (never silent). */
object TvRefusals {
    fun message(name: String, d: PairingSession.Decision): String? = when (d) {
        PairingSession.Decision.DENIED -> "$name a été refusé."
        PairingSession.Decision.TIMEOUT -> "$name attendait votre réponse : trop tard, la demande est annulée."
        PairingSession.Decision.BLOCKED -> "$name est ignoré 10 minutes : trois refus de suite."
        PairingSession.Decision.BUSY -> "$name doit patienter : une autre demande est en cours."
        PairingSession.Decision.FULL -> "$name ne peut pas être ajouté : cette TV a déjà ${TrustRegistry.MAX_PHONES} téléphones."
        PairingSession.Decision.WRITE_FAILED -> "$name n'a pas pu être enregistré : la liste des téléphones n'est pas inscriptible. Réessayez."
        PairingSession.Decision.APPROVED, PairingSession.Decision.NOT_OPEN -> null
    }
}

/** The short Bluetooth status the TV shows, matching the phone's diagnostic: what is ready, how many phones, and the pairing window. */
object TvBtStatus {
    fun line(btReady: Boolean, btReason: String?, trusted: Int, connected: Int, pairing: PairingSession.State): String = buildString {
        append(if (btReady) "Bluetooth prêt" else "Bluetooth : " + (btReason ?: "indisponible"))
        append(" · ").append(when (trusted) { 0 -> "aucun téléphone de confiance"; 1 -> "1 téléphone de confiance"; else -> "$trusted téléphones de confiance" })
        if (trusted > 0) append(" (").append(connected).append(" connecté").append(if (connected > 1) "s" else "").append(')')
        when (pairing) {
            PairingSession.State.Closed -> {}
            is PairingSession.State.Open -> append(" · « Ajouter un téléphone » ouvert")
            is PairingSession.State.Asking -> append(" · ").append(pairing.name).append(" demande l'accès")
        }
    }
}
