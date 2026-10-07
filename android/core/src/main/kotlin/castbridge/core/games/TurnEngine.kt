package castbridge.core.games

/** Le verdict de l'autorité sur un coup proposé. Dans l'ordre des contrôles : fini, inconnu, en retard, pas son tour, temps écoulé, illégal. */
enum class MoveVerdict {
    OK,
    /** Le numéro du coup n'est pas celui de la partie (doublon renvoyé, coup en retard) : rien n'est joué. */
    STALE,
    ILLEGAL, NOT_YOUR_TURN, OVER, UNKNOWN_PLAYER,
}

/** Que faire quand le compte à rebours d'un joueur tombe à zéro. */
enum class TimeoutPolicy {
    /** Il perd la partie (règle standard). */
    FORFEIT,
    /** Un coup est joué d'office à sa place ([GameRules.fallbackMove]) et la partie continue. */
    AUTO_MOVE,
}

/** La pendule d'une partie : au plus [perMoveMs] pour jouer chaque coup (le compteur repart à chaque coup). Fournie par l'AUTORITÉ ; les clients n'envoient jamais de temps. */
data class MoveClock(val perMoveMs: Long, val onTimeout: TimeoutPolicy = TimeoutPolicy.FORFEIT) {
    init { require(perMoveMs > 0) { "une pendule dure au moins une milliseconde : $perMoveMs" } }
}

/** Un coup joué, avec le temps écoulé depuis le début de la partie (horloge de l'autorité) et s'il a été joué d'office ([TimeoutPolicy.AUTO_MOVE]). */
class LoggedMove<M : GameMove>(val move: M, val atMs: Long, val auto: Boolean)

/**
 * LE MOTEUR DE TOURS : fait respecter les règles d'un [GameRules] autour d'une partie, comme `ChessGame` le fait pour les échecs, sans rien connaître du jeu.
 *
 * - **Coups numérotés** : [submit] reçoit le numéro de coup que le client croyait jouer ; un doublon ou un coup en retard est refusé ([MoveVerdict.STALE]), rien ne change.
 * - **Pendule** (facultative) : [MoveClock] ; le temps est compté par l'AUTORITÉ (`nowMs` fourni à chaque appel, jamais envoyé par un client). Un coup arrivé après l'échéance est refusé.
 * - **Abandon**, **déconnexion** : un joueur déconnecté voit sa pendule s'arrêter (s'il a la main ; elle s'arrête à son dernier signe de vie : le silence déjà écoulé lui est rendu) et a
 *   [graceMs] (60 s) de SILENCE pour revenir, comptées depuis ce dernier signe de vie ; passé ce délai il perd ([EndReason.DISCONNECTED]). Si plus aucun humain n'est là, la partie est
 *   abandonnée sans perdant. Revenir (même jeton de place, côté salle) suffit à reprendre. Limite connue : la salle ne s'aperçoit d'une coupure qu'au bout de son délai de présence (35 s) ;
 *   une pendule plus courte que cela peut donc s'épuiser avant que la coupure soit constatée (comme aux échecs).
 * - **Pause** de la pendule (menu de la TV, partie sans téléphone).
 * - **Journal** : chaque coup avec son heure ([log]) ; la graine et le résultat suffisent à rejouer la partie ([GameJournal]).
 *
 * Les ordinateurs ([ai]) ne se déconnectent jamais. Pas thread-safe : la salle prend son verrou. Toutes les méthodes qui reçoivent `nowMs` le font avancer ; une horloge qui recule n'ajoute
 * jamais de temps.
 */
class TurnEngine<S, M : GameMove>(
    val rules: GameRules<S, M>,
    val players: List<PlayerId>,
    val seed: Long,
    /** Heure de l'autorité au début de la partie. */
    val startMs: Long,
    val clock: MoveClock? = null,
    /** Les places jouées par l'ordinateur. */
    val ai: Set<PlayerId> = emptySet(),
    val graceMs: Long = DEFAULT_GRACE_MS,
) {
    init {
        require(players.size in rules.minPlayers..rules.maxPlayers) { "${rules.id} se joue de ${rules.minPlayers} à ${rules.maxPlayers} joueurs, pas ${players.size}" }
        require(players.toSet().size == players.size) { "une place ne peut pas figurer deux fois à la table" }
        require(ai.all { it in players }) { "une place de l'ordinateur doit être à la table" }
        require(graceMs > 0) { "la durée de reprise est positive : $graceMs" }
    }

    var state: S = rules.initial(seed, players); private set
    /** Nombre de coups joués : le numéro que les clients envoient avec leur coup. */
    var moveNo = 0; private set
    var result: GameResult? = null; private set
    var endedAtMs: Long? = null; private set
    var paused = false; private set
    val over: Boolean get() = result != null

    private val moves = ArrayList<LoggedMove<M>>()
    val log: List<LoggedMove<M>> get() = java.util.Collections.unmodifiableList(moves)

    private var lastAt = startMs
    /** Temps de pendule déjà consommé par le coup en cours (hors pause et hors absence du joueur qui a la main). */
    private var elapsed = 0L
    /** Les places déconnectées en ce moment, avec l'instant de leur dernier signe de vie. */
    private val silentSince = HashMap<PlayerId, Long>()

    init { checkRulesEnd(startMs) }

    /** Qui doit jouer maintenant (personne si la partie est finie). */
    fun toMove(): List<PlayerId> = if (over) emptyList() else rules.toMove(state, players)

    // ---------------------------------------------------------------- coups

    /**
     * Propose [move] (son auteur est la place de celui qui l'envoie) en croyant jouer le coup numéro [expectedNo] (null : pas de contrôle de retard).
     * Ordre des contrôles : partie finie, place inconnue, numéro en retard, pas la main, temps écoulé, coup illégal.
     */
    fun submit(move: M, expectedNo: Int?, nowMs: Long): MoveVerdict {
        if (over) return MoveVerdict.OVER
        if (move.player !in players) return MoveVerdict.UNKNOWN_PLAYER
        advance(nowMs)
        if (expectedNo != null && expectedNo != moveNo) return MoveVerdict.STALE
        val legal = rules.legal(state, move.player)
        if (legal.isEmpty()) return MoveVerdict.NOT_YOUR_TURN
        // la pendule de l'hôte décide : un coup arrivé après l'échéance n'est pas joué
        if (clock != null && running() && elapsed >= clock.perMoveMs) { tick(nowMs); return if (over) MoveVerdict.OVER else MoveVerdict.STALE }
        if (move !in legal) return MoveVerdict.ILLEGAL
        play(move, nowMs, auto = false)
        return MoveVerdict.OK
    }

    /** [player] abandonne (même quand ce n'est pas son tour). Faux si la partie est finie ou la place inconnue. */
    fun resign(player: PlayerId, nowMs: Long): Boolean {
        if (over || player !in players) return false
        advance(nowMs)
        forfeit(player, EndReason.RESIGNATION, nowMs)
        return true
    }

    /** Arrête la partie sans perdant (salle fermée). */
    fun abandon(nowMs: Long) {
        if (over) return
        advance(nowMs)
        end(GameResult(EndReason.ABANDONED, Outcome.Draw), nowMs)
    }

    // ---------------------------------------------------------------- pendule

    /** Arrête la pendule (menu de la TV). Faux si elle l'est déjà ou si la partie est finie. */
    fun pause(nowMs: Long): Boolean {
        if (over || paused) return false
        advance(nowMs)
        paused = true
        return true
    }

    /** Relance la pendule là où elle s'était arrêtée. */
    fun resume(nowMs: Long): Boolean {
        if (!paused) return false
        advance(nowMs)
        paused = false
        return true
    }

    /**
     * Temps qu'il reste à [player] pour jouer son coup, null s'il n'y a pas de pendule. Un joueur qui n'a pas la main garde son compte plein ; jamais négatif ni supérieur au
     * compte plein ; 0 une fois la partie finie. Ne modifie rien.
     */
    fun remainingMs(player: PlayerId, nowMs: Long): Long? {
        val c = clock ?: return null
        if (over) return 0
        if (player !in toMove()) return c.perMoveMs
        val extra = if (running() && nowMs > lastAt) nowMs - lastAt else 0L
        return (c.perMoveMs - elapsed - extra).coerceIn(0, c.perMoveMs)
    }

    /** Vrai tant que la pendule du coup en cours avance. */
    private fun running(): Boolean = result == null && !paused && clock != null && toMove().none { it in silentSince }

    private fun advance(now: Long) {
        if (now <= lastAt) return          // une horloge qui recule (ou ne bouge pas) n'ajoute jamais de temps
        if (running()) elapsed += now - lastAt
        lastAt = now
    }

    // ---------------------------------------------------------------- déconnexion

    /** [player] est-il là ? Toujours vrai pour l'ordinateur. */
    fun connected(player: PlayerId): Boolean = player !in silentSince

    /**
     * La salle signale qu'une place humaine est joignable ([connected]) ou ne l'est plus. [silentSinceMs] = le dernier signe de vie connu (la salle ne s'en aperçoit qu'après son délai de
     * présence) : les [graceMs] de reprise courent depuis cet instant, jamais depuis un instant futur. Revenir après l'échéance ne sauve plus la place : le forfait est prononcé d'abord.
     */
    fun setConnected(player: PlayerId, connected: Boolean, nowMs: Long, silentSinceMs: Long = nowMs) {
        if (over || player !in players || player in ai) return
        advance(nowMs)
        if (!connected) {
            if (player !in silentSince) {
                val silent = minOf(silentSinceMs, nowMs)
                val wasTicking = running() && player in toMove()
                silentSince[player] = silent
                // la pendule du joueur qui avait la main s'est arrêtée à son DERNIER SIGNE DE VIE, pas quand l'hôte s'en est aperçu : le silence lui est rendu
                if (wasTicking) elapsed = (elapsed - (nowMs - silent)).coerceAtLeast(0)
            }
            return
        }
        val since = silentSince[player] ?: return
        if (nowMs - since >= graceMs) { expireDisconnections(nowMs); return }
        silentSince.remove(player)
    }

    /** Ce qu'il reste à [player] pour revenir avant le forfait ; null s'il est là (ou si la partie est finie). */
    fun graceLeftMs(player: PlayerId, nowMs: Long): Long? {
        if (over) return null
        val since = silentSince[player] ?: return null
        return (graceMs - (nowMs - since)).coerceAtLeast(0)
    }

    // ---------------------------------------------------------------- horloge de l'autorité

    /** À appeler souvent (la salle le fait toutes les 200 ms) : applique les échéances de la pendule et de la déconnexion. Vrai si quelque chose a changé. */
    fun tick(nowMs: Long): Boolean {
        if (over) return false
        advance(nowMs)
        return expireDisconnections(nowMs) || expireClock(nowMs)
    }

    private fun expireDisconnections(now: Long): Boolean {
        val expired = players.filter { p -> silentSince[p]?.let { now - it >= graceMs } == true }   // dans l'ordre des places : déterministe
        if (expired.isEmpty()) return false
        // plus aucun humain à la table : personne ne perd, la partie est abandonnée
        if (players.filter { it !in ai }.all { it in silentSince }) end(GameResult(EndReason.ABANDONED, Outcome.Draw), now)
        else forfeit(expired.first(), EndReason.DISCONNECTED, now)
        return true
    }

    private fun expireClock(now: Long): Boolean {
        val c = clock ?: return false
        if (!running() || elapsed < c.perMoveMs) return false
        val p = toMove().firstOrNull() ?: return false         // des règles où personne n'a la main (état transitoire) : rien à sanctionner
        when (c.onTimeout) {
            TimeoutPolicy.FORFEIT -> forfeit(p, EndReason.TIMEOUT, now)
            TimeoutPolicy.AUTO_MOVE -> rules.fallbackMove(state, p)?.let { play(it, now, auto = true) } ?: forfeit(p, EndReason.TIMEOUT, now)
        }
        return true
    }

    // ---------------------------------------------------------------- fin

    private fun play(move: M, now: Long, auto: Boolean) {
        state = rules.apply(state, move)
        moveNo++
        moves += LoggedMove(move, (now - startMs).coerceAtLeast(0), auto)
        elapsed = 0
        checkRulesEnd(now)
    }

    private fun checkRulesEnd(now: Long) {
        val o = rules.outcome(state)
        if (o.over) end(GameResult(EndReason.RULES, o), now)
    }

    private fun forfeit(player: PlayerId, reason: EndReason, now: Long) {
        val others = players.filter { it != player }
        end(GameResult(reason, if (others.isEmpty()) Outcome.Draw else Outcome.Winners(others), by = player), now)
    }

    private fun end(r: GameResult, now: Long) {
        result = r
        endedAtMs = now
        silentSince.clear()
    }

    companion object {
        /** Silence permis à un joueur déconnecté avant le forfait (comme le Quiz en ligne : docs/PLAY-PROTOCOL.md). */
        const val DEFAULT_GRACE_MS = 60_000L
    }
}
