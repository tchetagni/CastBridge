package castbridge.core.owner

import java.security.SecureRandom

/** Durées permises d'une session super administrateur (plafond : [SuperSession.MAX]). Une durée plus longue est impossible par construction. */
enum class SuperDuration(val ms: Long) {
    H1(3_600_000L), H4(4 * 3_600_000L), H12(12 * 3_600_000L), H24(24 * 3_600_000L)
}

/**
 * Session super administrateur limitée dans le temps (cœur pur, sans Android). Le temps vient de [TvClock] : un recul de l'horloge murale n'allonge pas
 * la session (le temps monotone continue d'avancer), un saut en avant douteux (> 45 jours) la termine ([close] « horloge »). L'horloge est observée par la session elle-même
 * ([open], [active], [decode]) : l'appelant n'a rien à faire. Contre le « gel » de l'horloge (reculer l'heure murale de moins de 60 s chaque minute), la durée de fonctionnement
 * cumulée ([TvClock.uptimeNow]) est aussi plafonnée : `uptimeNow - uptimeAtOpen < durée`.
 * CONTRAT Android (w6-19) : resceller et écrire le blob [encode] après chaque [open], [close], [noteFailure] et [noteSuccess], pour que tuer l'application ne remette pas le compteur d'échecs à zéro ;
 * ne donner à PhoneGate que [activeState]. Aucun secret : l'état ne contient que deux dates et un nonce.
 * Le scellement du blob [encode] (SecretWrapper/Keystore) est l'affaire de la couche Android ; le nonce n'est pas vérifié ici.
 * Le verrou de 2 min de la console ne touche pas à cette session. [audit] reçoit les événements `super.open` / `super.close`.
 */
class SuperSession(private val clock: TvClock, private val audit: AuditChain? = null, state: State? = null,
                   private val random: () -> ByteArray = { ByteArray(16).also { SecureRandom().nextBytes(it) } }, failures: Int = 0) {
    data class State(val openedAt: Long, val untilMs: Long, val nonce: String, val uptimeAtOpen: Long)

    companion object {
        val MAX = SuperDuration.H24
        val DEFAULT = SuperDuration.H12
        /** Échecs de mot de passe consécutifs qui ferment la session. */
        const val MAX_FAILURES = 5
        /** Avance tolérée de `openedAt` sur l'heure de la TV au relire du blob. */
        const val FUTURE_TOLERANCE_MS = 60_000L
        private val NONCE = Regex("[0-9a-f]{32}")

        /**
         * Relit un blob [encode] (`v2 openedAt until nonce uptimeAtOpen failures`) ; null si altéré, invalide, ancien format v1 (refusé = fermé) ou ouvert « dans le futur ».
         * Observe l'horloge (relève le plancher avec `openedAt`, signé par le scellement). Si l'horloge n'est pas persistée (uptime plus petit que celui de l'ouverture), la session est relue fermée.
         */
        fun decode(text: String, clock: TvClock, nowWall: Long, audit: AuditChain? = null): SuperSession? {
            val p = text.trim().split(' ')
            if (p.size != 6 || p[0] != "v2") return null
            val opened = p[1].toLongOrNull() ?: return null
            val until = p[2].toLongOrNull() ?: return null
            val nonce = p[3]
            val up = p[4].toLongOrNull() ?: return null
            val fails = p[5].toIntOrNull() ?: return null
            if (opened < 0 || up < 0 || until <= opened || until - opened > MAX.ms || !NONCE.matches(nonce) || fails !in 0 until MAX_FAILURES) return null
            clock.observe(nowWall)
            if (opened > clock.now(nowWall) + FUTURE_TOLERANCE_MS) return null
            clock.observe(nowWall, signedIssuedAt = opened)
            val s = SuperSession(clock, audit, State(opened, until, nonce, up), failures = fails)
            if (clock.uptimeNow() < up) s.close("horloge", nowWall)   // horloge non persistée : on ne peut plus borner la durée
            return s
        }
    }

    var state: State? = state
        private set
    var failures: Int = failures
        private set

    @Synchronized fun open(d: SuperDuration = DEFAULT, nowWall: Long): State {
        clock.observe(nowWall)
        val now = clock.now(nowWall)
        val nonce = random().joinToString("") { "%02x".format(it) }
        val s = State(now, now + d.ms, nonce, clock.uptimeNow())
        state = s; failures = 0
        audit?.append(now, "super.open", d.name, "ok")
        return s
    }

    @Synchronized fun close(reason: String, nowWall: Long) {
        if (state == null) return
        state = null
        audit?.append(clock.now(nowWall), "super.close", reason, "ok")
    }

    /**
     * Vrai tant que le temps de la TV est avant l'échéance, que la durée de fonctionnement n'est pas épuisée et que l'horloge murale ne fait pas un saut en avant douteux.
     * Quand elle devient fausse, la session est fermée (une seule entrée d'audit).
     */
    @Synchronized fun active(nowWall: Long): Boolean {
        val s = state ?: return false
        clock.observe(nowWall)
        if (clock.isAhead(nowWall)) { close("horloge", nowWall); return false }
        val ran = clock.uptimeNow() - s.uptimeAtOpen
        if (clock.now(nowWall) >= s.untilMs || ran < 0 || ran >= s.untilMs - s.openedAt) { close("expiration", nowWall); return false }
        return true
    }

    /** L'état seulement si la session est active (le seul accès pour PhoneGate). */
    @Synchronized fun activeState(nowWall: Long): State? = if (active(nowWall)) state else null

    @Synchronized fun remainingMs(nowWall: Long): Long {
        val s = activeState(nowWall) ?: return 0L
        val byWall = s.untilMs - clock.now(nowWall)
        val byRun = (s.untilMs - s.openedAt) - (clock.uptimeNow() - s.uptimeAtOpen)
        return minOf(byWall, byRun).coerceIn(0L, MAX.ms)
    }

    /** Un échec de mot de passe ; au 5e consécutif la session est fermée. Renvoie vrai si elle vient de l'être. Resceller le blob ensuite (le compteur y est). */
    @Synchronized fun noteFailure(nowWall: Long): Boolean {
        failures++
        if (failures >= MAX_FAILURES && state != null) { close("echecs", nowWall); return true }
        return false
    }

    @Synchronized fun noteSuccess() { failures = 0 }

    /** `v2 openedAt until nonce uptimeAtOpen failures` ; chaîne vide s'il n'y a pas de session (à ne pas sceller, effacer le fichier). */
    @Synchronized fun encode(): String = state?.let { "v2 ${it.openedAt} ${it.untilMs} ${it.nonce} ${it.uptimeAtOpen} $failures" } ?: ""
}
