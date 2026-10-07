package castbridge.core.relay

import castbridge.core.connect.NetState

/** Un téléphone synchronisé, tel que la TV le connaît. [relayCapable] : vrai = vu avec la capacité « tuyau à la demande », faux = vu sans (ancien CastBridge), null = pas encore vu. */
data class PhoneInfo(val address: String, val relayCapable: Boolean?, val lastSeenMs: Long)

/** Ce dont le courtier a besoin de la TV (Android dans l'application, un faux dans les tests). */
interface PipeEnv {
    fun now(): Long
    fun sleep(ms: Long)
    fun net(): NetState
    fun phones(): List<PhoneInfo>
    /** Réveille le téléphone [address] (sans attendre : l'application le fait sur son propre fil). */
    fun knock(address: String)
}

enum class PipeWhy { NO_PHONE, OLD_PHONE, REFUSED, TIMEOUT, PAUSED }

sealed class PipeOutcome {
    data class Up(val net: NetState) : PipeOutcome()
    /** [text] est dit à l'écran tel quel ; [reason] = le motif donné par le téléphone quand il a refusé. */
    data class Failed(val why: PipeWhy, val text: String, val reason: RelayReason? = null) : PipeOutcome()
}

/**
 * La demande de tuyau par la TV (relay-R1 § 2, DESIGN-RELAIS § 2.4). Quand une opération « vivante » (jeu en ligne, portefeuille, mise à jour immédiate, assistance)
 * a besoin d'Internet et que [NetState] est `none`, elle appelle [need] : la TV réveille les téléphones synchronisés (un coup à la porte, [PipeEnv.knock]) et lève le drapeau
 * `pipeWanted` ([wanted]) que ceux-ci lisent dans l'état échangé (HELLO, `/api/info`, `RELAY_STATE`). Le téléphone ouvre alors le tuyau de lui-même.
 *
 * Règles : une première demande tout de suite, puis une toutes les [askEveryMs] tant que l'opération rafraîchit sa demande, au plus [maxAsks] par épisode, puis une pause
 * de [pauseMs] (une action de l'utilisateur la lève : `force`) ; un bail de [leaseMs] que l'opération doit renouveler (une partie finie ne laisse rien derrière elle) ;
 * jamais plus de trois téléphones par tour, le plus récemment vu d'abord ; un téléphone qui a dit non n'est pas relancé avant l'échéance de son motif ; un téléphone
 * vu SANS la capacité (ancien CastBridge) n'est pas sollicité : « Mettez CastBridge à jour pour l'Internet par relais ». Pur : horloge et attente injectées.
 */
class PipeBroker(
    private val env: PipeEnv,
    private val leaseMs: Long = 120_000,
    private val askEveryMs: Long = 15_000,
    private val maxAsks: Int = 6,
    private val pauseMs: Long = 5 * 60_000,
    private val pollMs: Long = 500,
) {
    private class Refusal(val reason: RelayReason, val until: Long)

    private var leaseUntil = 0L
    private val needs = LinkedHashSet<PipeNeed>()
    private var asks = 0
    private var lastAskAt = 0L
    private var exhaustedUntil = 0L
    private val refused = HashMap<String, Refusal>()

    /** Le drapeau lu par les téléphones : une opération attend Internet, le tuyau n'est pas ouvert, la TV n'a pas renoncé. */
    @Synchronized fun wanted(): Boolean {
        val now = env.now()
        return now < leaseUntil && now >= exhaustedUntil && !env.net().up      // the cheap checks first: this is read at every GET /api/info (once a second per client)
    }

    /** Ce que la demande en cours dit vouloir faire (un « bulk » est refusé sur données mobiles). */
    @Synchronized fun currentNeeds(): List<PipeNeed> = if (env.now() < leaseUntil) needs.toList() else emptyList()

    /**
     * Enregistre ou renouvelle la demande, et réveille les téléphones si c'est le moment. Sans attendre. Faux : Internet est déjà là, ou la TV a renoncé pour un temps.
     * [force] : l'utilisateur vient d'appuyer (reprend un épisode épuisé).
     */
    @Synchronized fun need(n: PipeNeed, force: Boolean = false): Boolean {
        val now = env.now()
        if (env.net().up) { clear(); return false }
        if (force) resetEpisode()
        if (exhaustedUntil != 0L) { if (now < exhaustedUntil) return false; resetEpisode() }
        if (now >= leaseUntil) needs.clear()
        needs += n
        leaseUntil = now + leaseMs
        pump(now)
        return true
    }

    private fun pump(now: Long) {
        if (asks >= maxAsks) { if (now - lastAskAt >= askEveryMs) exhaustedUntil = now + pauseMs; return }
        if (asks > 0 && now - lastAskAt < askEveryMs) return
        val targets = eligible(now).take(MAX_PER_ROUND)
        if (targets.isEmpty()) return
        for (p in targets) env.knock(p.address)
        asks++; lastAskAt = now
    }

    private fun eligible(now: Long): List<PhoneInfo> =
        env.phones().filter { it.relayCapable != false && (refused[it.address]?.until ?: 0L) <= now }.sortedByDescending { it.lastSeenMs }

    private fun resetEpisode() { asks = 0; lastAskAt = 0L; exhaustedUntil = 0L }

    /** Internet est là (ou la demande n'a plus lieu d'être). */
    @Synchronized fun clear() { needs.clear(); leaseUntil = 0L; resetEpisode() }

    /** Le téléphone [address] a dit pourquoi il ne relaie pas ([reason]), ou qu'il relaie (null). */
    @Synchronized fun report(address: String, reason: RelayReason?) {
        if (reason == null) refused.remove(address)
        else refused[address] = Refusal(reason, env.now() + backoffMs(reason))
    }

    /** Pourquoi la demande ne peut pas aboutir MAINTENANT, ou null si l'on peut encore espérer. */
    @Synchronized private fun failure(now: Long): PipeOutcome.Failed? {
        val phones = env.phones()
        if (phones.isEmpty()) return PipeOutcome.Failed(PipeWhy.NO_PHONE, RelayText.NO_PHONE)
        if (phones.all { it.relayCapable == false }) return PipeOutcome.Failed(PipeWhy.OLD_PHONE, RelayText.OLD_PHONE)
        if (eligible(now).isEmpty()) {
            val r = phones.filter { it.relayCapable != false }.mapNotNull { refused[it.address] }.filter { it.until > now }.maxByOrNull { it.until }?.reason
            if (r != null) return PipeOutcome.Failed(PipeWhy.REFUSED, RelayText.refusal(r), r)
        }
        if (exhaustedUntil != 0L && now < exhaustedUntil) return PipeOutcome.Failed(PipeWhy.PAUSED, RelayText.PAUSED)
        return null
    }

    /**
     * Attend (bloque : jamais sur le fil principal) que le tuyau s'ouvre, [timeoutMs] au plus, en demandant au rythme de la règle. Rend [PipeOutcome.Up] dès qu'Internet
     * est là (par n'importe quelle voie), ou la raison de l'échec en français.
     */
    fun ensure(n: PipeNeed, timeoutMs: Long, force: Boolean = false): PipeOutcome {
        val deadline = env.now() + timeoutMs
        if (force) synchronized(this) { resetEpisode() }
        while (true) {
            env.net().takeIf { it.up }?.let { synchronized(this) { clear() }; return PipeOutcome.Up(it) }
            synchronized(this) { failure(env.now()) }?.let { return it }
            need(n)
            val now = env.now()
            if (now >= deadline) return PipeOutcome.Failed(PipeWhy.TIMEOUT, RelayText.TIMEOUT)
            env.sleep(minOf(pollMs, deadline - now))
        }
    }

    /** La ligne d'état de la TV pendant la demande, ou null quand rien ne se passe (ou que le tuyau est ouvert). */
    @Synchronized fun text(): String? {
        val now = env.now()
        if (env.net().up || now >= leaseUntil) return null
        failure(now)?.let { return it.text }
        return RelayText.ASKING
    }

    private fun backoffMs(r: RelayReason): Long = when (r) {
        RelayReason.OPTED_OUT, RelayReason.NOT_SYNCED -> 60 * 60_000L
        RelayReason.CAP -> 3 * 60 * 60_000L
        RelayReason.METERED_BULK -> 30 * 60_000L
        RelayReason.OFFLINE -> 2 * 60_000L
        RelayReason.BACKGROUND, RelayReason.BUSY -> 30_000L
    }

    companion object { const val MAX_PER_ROUND = 3 }
}
