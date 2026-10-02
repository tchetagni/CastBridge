package castbridge.core.owner

import java.security.SecureRandom

/** Durées permises d'une session super administrateur (plafond : [SuperSession.MAX]). Une durée plus longue est impossible par construction. */
enum class SuperDuration(val ms: Long) {
    H1(3_600_000L), H4(4 * 3_600_000L), H12(12 * 3_600_000L), H24(24 * 3_600_000L)
}

/**
 * Session super administrateur limitée dans le temps (cœur pur, sans Android). Le temps vient de [TvClock] : un recul de l'horloge murale n'allonge pas
 * la session (le temps monotone continue d'avancer), un saut en avant douteux (> 45 jours) la termine. Aucun secret : l'état ne contient que deux dates et un nonce.
 * Le scellement du blob [encode] (SecretWrapper/Keystore) est l'affaire de la couche Android ; le nonce n'est pas vérifié ici.
 * Le verrou de 2 min de la console ne touche pas à cette session. [audit] reçoit les événements `super.open` / `super.close`.
 */
class SuperSession(private val clock: TvClock, private val audit: AuditChain? = null, state: State? = null,
                   private val random: () -> ByteArray = { ByteArray(16).also { SecureRandom().nextBytes(it) } }) {
    data class State(val openedAt: Long, val untilMs: Long, val nonce: String)

    companion object {
        val MAX = SuperDuration.H24
        val DEFAULT = SuperDuration.H12
        /** Échecs de mot de passe consécutifs qui ferment la session. */
        const val MAX_FAILURES = 5
        private val NONCE = Regex("[0-9a-f]{32}")

        /** Relit un blob [encode] ; null si altéré ou invalide (jamais « ouverte par défaut »). */
        fun decode(text: String, clock: TvClock, audit: AuditChain? = null): SuperSession? {
            val p = text.trim().split(' ')
            if (p.size != 4 || p[0] != "v1") return null
            val opened = p[1].toLongOrNull() ?: return null
            val until = p[2].toLongOrNull() ?: return null
            val nonce = p[3]
            if (opened < 0 || until <= opened || until - opened > MAX.ms || !NONCE.matches(nonce)) return null
            return SuperSession(clock, audit, State(opened, until, nonce))
        }
    }

    var state: State? = state
        private set
    var failures: Int = 0
        private set

    @Synchronized fun open(d: SuperDuration = DEFAULT, nowWall: Long): State {
        val now = clock.now(nowWall)
        val nonce = random().joinToString("") { "%02x".format(it) }
        val s = State(now, now + d.ms, nonce)
        state = s; failures = 0
        audit?.append(now, "super.open", d.name, "ok")
        return s
    }

    @Synchronized fun close(reason: String, nowWall: Long) {
        if (state == null) return
        state = null
        audit?.append(clock.now(nowWall), "super.close", reason, "ok")
    }

    /** Vrai tant que le temps de la TV est avant l'échéance et que l'horloge murale ne fait pas un saut en avant douteux. */
    @Synchronized fun active(nowWall: Long): Boolean {
        val s = state ?: return false
        if (clock.isAhead(nowWall)) return false
        return clock.now(nowWall) < s.untilMs
    }

    @Synchronized fun remainingMs(nowWall: Long): Long {
        val s = state ?: return 0L
        return if (active(nowWall)) (s.untilMs - clock.now(nowWall)).coerceIn(0L, MAX.ms) else 0L
    }

    /** Un échec de mot de passe ; au 5e consécutif la session est fermée. Renvoie vrai si elle vient de l'être. */
    @Synchronized fun noteFailure(nowWall: Long): Boolean {
        failures++
        if (failures >= MAX_FAILURES && state != null) { close("echecs", nowWall); return true }
        return false
    }

    @Synchronized fun noteSuccess() { failures = 0 }

    /** `v1 openedAt until nonce` ; chaîne vide s'il n'y a pas de session (à ne pas sceller, effacer le fichier). */
    @Synchronized fun encode(): String = state?.let { "v1 ${it.openedAt} ${it.untilMs} ${it.nonce}" } ?: ""
}
