package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.LinkPlanner
import java.net.URLDecoder
import java.net.URLEncoder

/** Which side of the link went away: said to the user ("la TV ne répond plus" is not "votre Wi-Fi est coupé"). */
enum class LossSide { TV, PHONE_BLUETOOTH, PHONE_NETWORK }

enum class RouteKind {
    LAN, DIRECT, BLUETOOTH;
    companion object {
        fun of(r: LinkPlanner.Route) = when (r) { is LinkPlanner.Route.Lan -> LAN; is LinkPlanner.Route.Direct -> DIRECT; else -> BLUETOOTH }
    }
}

enum class Tone { GOOD, WARN, BAD, NEUTRAL }

/** What the phone shows about its link to the TV. Each state has a stable [key] (used for hysteresis) and a French [LinkView]. */
sealed class LinkState {
    abstract val key: String
    object NoTv : LinkState() { override val key = "notv" }
    object Connecting : LinkState() { override val key = "connecting" }
    data class BtBlocked(val reason: BtUnavailable.Reason) : LinkState() { override val key get() = "bt:$reason" }
    object NotBonded : LinkState() { override val key = "notbonded" }
    object Bonding : LinkState() { override val key = "bonding" }
    object StaleBond : LinkState() { override val key = "stalebond" }
    data class TvUnreachable(val kind: AbsentKind) : LinkState() { override val key get() = "unreachable:$kind" }
    data class TvForgotMe(val hint: Int) : LinkState() { override val key get() = "forgot:$hint" }
    data class WaitingOwner(val code: Int) : LinkState() { override val key get() = "owner:$code" }
    object Denied : LinkState() { override val key = "denied" }
    object CredentialExpired : LinkState() { override val key = "credential" }
    object TvTooOld : LinkState() { override val key = "tooold" }
    data class TvError(val code: Int) : LinkState() { override val key get() = "tverror:$code" }
    data class Connected(val route: RouteKind, val tvName: String) : LinkState() { override val key get() = "ok:$route" }
    data class Degraded(val tvName: String) : LinkState() { override val key = "degraded" }
    /** The link was good and dropped: the last good state stays on screen with this title until [LinkMachine.Config.lostGraceMs] has passed. */
    data class Reconnecting(val side: LossSide, val last: LinkState?) : LinkState() { override val key get() = "reconnecting:$side" }

    val isGood get() = this is Connected || this is Degraded
}

/** What a screen draws: no logic left to the UI. [hint] is a quiet line ("Liaison perdue, reconnexion…") shown over a state that is still the last good one. */
data class LinkView(val state: LinkState, val title: String, val detail: String, val action: LinkAction, val tone: Tone, val busy: Boolean, val hint: String? = null)

/** What one attempt or one observation found. The machine turns a stream of these into a steady state. */
sealed class Outcome {
    object NoTv : Outcome()
    data class Bluetooth(val reason: BtUnavailable.Reason) : Outcome()
    object NotBonded : Outcome()
    object Bonding : Outcome()
    data class Absent(val kind: AbsentKind) : Outcome()
    data class Refused(val code: Int, val hint: Int = BtProtocol.HINT_NONE) : Outcome()
    /** [wifiExpected]: the TV advertised a Wi-Fi address, so a Bluetooth-only route means a reduced link. */
    data class Connected(val route: RouteKind, val tvName: String, val wifiExpected: Boolean) : Outcome()
    data class Lost(val side: LossSide) : Outcome()
    /** The TV's API refused the token (expired or revoked): never retried with the same token, a new HELLO is the answer. */
    object TokenRejected : Outcome()
    /** The keep-alive probe worked: the link is fine. */
    object Alive : Outcome()
}

/** When to try again. [Never]: no automatic attempt; wait for the user or a system event (Bluetooth broadcast, app opened). */
sealed class Retry {
    data class After(val ms: Long) : Retry()
    data class Never(val why: String) : Retry()
}

/**
 * The phone's connection state machine: pure (state in, outcome in, state out), driven by [LinkDriver] and exhaustively tested.
 *
 * - Hysteresis: a state replaces the shown one only after [Config.confirmations] identical outcomes in a row AND [Config.minShowMs] of display;
 *   good news from a steady state, "no TV", Bluetooth switched off and "pairing in progress" are shown at once (they are certain).
 * - A transient failure keeps the last good state on screen with [LinkView.hint] ("reconnexion…"); the failure state appears only after
 *   the confirmations and [Config.lostGraceMs].
 * - [nextAttempt] says when to try again (backoff with jitter, slow and battery-friendly in the background) and which states are never retried
 *   by themselves: the TV forgot this phone, the owner denied it, a credential was refused.
 * - Backoff only resets after [Config.stableMs] of continuous success: a link that flaps keeps its long delays.
 */
class LinkMachine(val cfg: Config = Config()) {
    data class Config(
        val confirmations: Int = 2, val minShowMs: Long = 3_000, val lostGraceMs: Long = 40_000, val stableMs: Long = 30_000,
        val staleBondAfter: Int = 3,
        val foregroundSteps: LongArray = longArrayOf(2_000, 4_000, 8_000, 15_000, 30_000, 60_000),
        val backgroundSteps: LongArray = longArrayOf(60_000, 120_000, 300_000, 600_000, 900_000),
        val confirmDelayMs: Long = 1_500, val keepAliveMs: Long = 15_000, val backgroundKeepAliveMs: Long = 300_000,
    )

    data class Model(
        val shown: LinkState = LinkState.NoTv, val since: Long = 0, val tvName: String = "TV",
        val pending: LinkState? = null, val pendingCount: Int = 0,
        /** Failed attempts since the last STABLE connection (drives the backoff). */
        val failures: Int = 0, val quickCloses: Int = 0, val tokenRejects: Int = 0,
        val lastGood: LinkState? = null, val lostSince: Long? = null, val connectedSince: Long? = null,
    ) {
        val reconnectingHint get() = shown.isGood && pending != null

        /** Text kept across a process death or a reboot: enough to show the right card at once and not to hammer the TV, never a credential. */
        fun encode(): String = listOf("v1", enc(shown.key), since, enc(tvName), failures, (lastGood as? LinkState.Connected)?.route?.name.orEmpty(), shownExtra(shown)).joinToString("|")

        private fun shownExtra(s: LinkState) = when (s) { is LinkState.TvForgotMe -> "h${s.hint}"; is LinkState.BtBlocked -> ""; else -> "" }

        companion object {
            private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
            /** Rebuilds a model after the process died: a good state becomes "reconnecting" (nobody knows if the link survived); definitive refusals stay. */
            fun decode(text: String?, now: Long): Model? = runCatching {
                val f = text!!.split('|')
                if (f[0] != "v1") return null
                val key = URLDecoder.decode(f[1], "UTF-8"); val name = URLDecoder.decode(f[3], "UTF-8"); val failures = f[4].toInt().coerceIn(0, 1000)
                val good = f[5].takeIf { it.isNotEmpty() }?.let { LinkState.Connected(RouteKind.valueOf(it), name) }
                val shown: LinkState = when {
                    key.startsWith("forgot:") -> LinkState.TvForgotMe(f[6].removePrefix("h").toIntOrNull() ?: 0)
                    key == "denied" -> LinkState.Denied
                    key == "tooold" -> LinkState.TvTooOld
                    key == "stalebond" -> LinkState.StaleBond
                    key == "notv" -> LinkState.NoTv
                    good != null && (key.startsWith("ok:") || key == "degraded" || key.startsWith("reconnecting")) -> LinkState.Reconnecting(LossSide.TV, good)
                    else -> LinkState.Connecting
                }
                Model(shown, now, name, failures = failures, lastGood = good, lostSince = if (shown is LinkState.Reconnecting) now else null)
            }.getOrNull()
        }
    }

    fun initial(hasTv: Boolean, tvName: String = "TV") = Model(shown = if (hasTv) LinkState.Connecting else LinkState.NoTv, tvName = tvName)

    fun reduce(m0: Model, o: Outcome, now: Long): Model {
        var m = m0
        // ---- counters
        val failing = o !is Outcome.Connected && o !is Outcome.Alive && o !is Outcome.NoTv
        m = when (o) {
            is Outcome.Connected -> m.copy(connectedSince = m.connectedSince ?: now, tokenRejects = 0, quickCloses = 0, lostSince = null,
                lastGood = if (o.route == RouteKind.BLUETOOTH && o.wifiExpected) LinkState.Degraded(o.tvName) else LinkState.Connected(o.route, o.tvName), tvName = o.tvName)
            is Outcome.Alive -> m.copy(connectedSince = m.connectedSince ?: now, lostSince = null)
            else -> m.copy(connectedSince = null, failures = if (failing) m.failures + 1 else m.failures)
        }
        if (m.connectedSince != null && now - m.connectedSince!! >= cfg.stableMs) m = m.copy(failures = 0)
        m = m.copy(quickCloses = if (o is Outcome.Absent && o.kind == AbsentKind.CLOSED_AT_ONCE) m.quickCloses + 1 else if (o is Outcome.Connected || o is Outcome.Alive) 0 else if (o is Outcome.Absent) 0 else m.quickCloses)
        if (o is Outcome.TokenRejected) m = m.copy(tokenRejects = m.tokenRejects + 1)
        // definitive answers invalidate what was good; so does leaving the pairing
        if (o is Outcome.Refused || o is Outcome.NoTv || o is Outcome.NotBonded) m = m.copy(lastGood = null, lostSince = null)
        if ((o is Outcome.Absent || o is Outcome.Lost || o is Outcome.TokenRejected) && m.lostSince == null && m.lastGood != null) m = m.copy(lostSince = now)

        val target = target(m, o, now) ?: return m.copy(pending = null, pendingCount = 0)
        return apply(m, target, o, now)
    }

    private fun target(m: Model, o: Outcome, now: Long): LinkState? {
        val good = m.lastGood
        val inGrace = good != null && m.lostSince != null && now - m.lostSince < cfg.lostGraceMs
        fun lost(side: LossSide, fallback: LinkState) = if (good != null && (inGrace || m.lostSince == null)) LinkState.Reconnecting(side, good) else fallback
        return when (o) {
            Outcome.NoTv -> LinkState.NoTv
            is Outcome.Bluetooth -> LinkState.BtBlocked(o.reason)
            Outcome.NotBonded -> LinkState.NotBonded
            Outcome.Bonding -> LinkState.Bonding
            is Outcome.Absent ->
                if (o.kind == AbsentKind.CLOSED_AT_ONCE && m.quickCloses >= cfg.staleBondAfter) LinkState.StaleBond
                else lost(LossSide.TV, LinkState.TvUnreachable(o.kind))
            is Outcome.Lost -> lost(o.side, LinkState.TvUnreachable(AbsentKind.NO_ANSWER))
            Outcome.TokenRejected -> if (m.tokenRejects >= 2) LinkState.CredentialExpired else lost(LossSide.TV, LinkState.CredentialExpired)
            is Outcome.Refused -> when (o.code) {
                BtProtocol.ERR_UNTRUSTED -> LinkState.TvForgotMe(o.hint)
                BtProtocol.ERR_DENIED -> LinkState.Denied
                BtProtocol.ERR_NOT_OPEN, BtProtocol.ERR_BUSY, BtProtocol.ERR_TIMEOUT -> LinkState.WaitingOwner(o.code)
                BtProtocol.ERR_MAGIC -> LinkState.TvTooOld
                else -> LinkState.TvError(o.code)
            }
            is Outcome.Connected -> if (o.route == RouteKind.BLUETOOTH && o.wifiExpected) LinkState.Degraded(o.tvName) else LinkState.Connected(o.route, o.tvName)
            Outcome.Alive -> if (m.shown is LinkState.Reconnecting || m.shown is LinkState.CredentialExpired) good else null     // nothing to say while all is well
        }
    }

    private fun apply(m: Model, target: LinkState, o: Outcome, now: Long): Model {
        val shown = m.shown
        if (target.key == shown.key) return m.copy(shown = target, pending = null, pendingCount = 0)     // same state: refresh its details silently
        val certain = target is LinkState.NoTv || target is LinkState.BtBlocked || target is LinkState.Bonding || (target.isGood && shown !is LinkState.Reconnecting)
        val shownLongEnough = now - m.since >= cfg.minShowMs
        if (certain) return m.copy(shown = target, since = now, pending = null, pendingCount = 0)
        // good news after a "reconnecting" shows only for a minimum time so a link that flaps does not blink; no confirmations needed
        if (target.isGood) return if (shownLongEnough) m.copy(shown = target, since = now, pending = null, pendingCount = 0) else m
        val count = if (m.pending?.key == target.key) m.pendingCount + 1 else 1
        // a refusal from the TV itself is decisive after the confirmations; a failure to answer also waits for the grace period of a lost link
        val graceOver = target !is LinkState.TvUnreachable || m.lastGood == null || m.lostSince == null || now - m.lostSince >= cfg.lostGraceMs
        return if (count >= cfg.confirmations && shownLongEnough && graceOver) m.copy(shown = target, since = now, pending = null, pendingCount = 0)
        else m.copy(pending = target, pendingCount = count)
    }

    // ------------------------------------------------------------------------------------------------ retry policy

    /** When the loop should try again. Deterministic given [random] (a value in [0,1) used for the jitter). */
    fun nextAttempt(m: Model, foreground: Boolean, random: () -> Double = Math::random): Retry {
        fun jit(ms: Long) = Retry.After(Jitter.around(ms, 0.25, random))
        fun backoff(): Retry {
            val steps = if (foreground) cfg.foregroundSteps else cfg.backgroundSteps
            return jit(steps[(m.failures - 1).coerceIn(0, steps.size - 1)])
        }
        return when (m.shown) {
            is LinkState.NoTv -> Retry.Never("aucune TV enregistrée")
            is LinkState.TvForgotMe -> Retry.Never("la TV ne reconnaît pas ce téléphone : action de l'utilisateur")
            is LinkState.Denied -> Retry.Never("refusé par le propriétaire : jamais de nouvel essai automatique")
            is LinkState.WaitingOwner -> Retry.Never("l'approbation se demande depuis l'écran d'association")
            is LinkState.TvTooOld -> if (foreground) jit(10 * 60_000L) else Retry.Never("TV trop ancienne")
            is LinkState.BtBlocked -> if (foreground) jit(15_000) else jit(15 * 60_000L)       // the Bluetooth broadcast wakes the loop earlier
            is LinkState.NotBonded -> if (foreground) jit(5_000) else Retry.Never("pas d'association : attendre le signal d'association")
            is LinkState.Bonding -> jit(2_000)
            is LinkState.StaleBond -> if (foreground) jit(3_000) else Retry.Never("association périmée : attendre qu'elle soit supprimée")
            is LinkState.TvError -> if (foreground) jit(60_000) else jit(15 * 60_000L)
            is LinkState.Connected, is LinkState.Degraded ->
                if (m.pending != null) jit(cfg.confirmDelayMs)                      // a failure to confirm: look again soon, not at the next keep-alive
                else jit(if (foreground) cfg.keepAliveMs else cfg.backgroundKeepAliveMs)
            is LinkState.Reconnecting, is LinkState.TvUnreachable, is LinkState.CredentialExpired, is LinkState.Connecting -> if (m.failures <= 1) jit(cfg.confirmDelayMs) else backoff()   // the first miss is confirmed quickly
        }
    }

    // ------------------------------------------------------------------------------------------------ text

    fun view(m: Model): LinkView {
        val tv = m.tvName
        val s = m.shown
        fun v(a: Advice, tone: Tone, busy: Boolean = false, hint: String? = null) = LinkView(s, a.title, a.detail, a.action, tone, busy, hint)
        return when (s) {
            LinkState.NoTv -> v(LinkText.noTv, Tone.NEUTRAL)
            LinkState.Connecting -> LinkView(s, "Connexion à $tv…", "Recherche de la TV par Bluetooth", LinkAction.NONE, Tone.WARN, true)
            is LinkState.BtBlocked -> v(LinkText.bluetooth(s.reason), Tone.BAD)
            LinkState.NotBonded -> v(LinkText.notBonded, Tone.BAD)
            LinkState.Bonding -> v(LinkText.bonding, Tone.WARN, busy = true)
            LinkState.StaleBond -> v(LinkText.staleBond, Tone.BAD)
            is LinkState.TvUnreachable -> v(LinkText.absent(s.kind, tv), Tone.NEUTRAL, busy = false)
            is LinkState.TvForgotMe -> v(LinkText.untrustedAdvice(s.hint), Tone.BAD)
            is LinkState.WaitingOwner -> v(LinkText.refused(s.code), Tone.WARN)
            LinkState.Denied -> v(LinkText.refused(BtProtocol.ERR_DENIED), Tone.BAD)
            LinkState.CredentialExpired -> v(LinkText.credentialExpired, Tone.WARN, busy = true)
            LinkState.TvTooOld -> v(LinkText.refused(BtProtocol.ERR_MAGIC), Tone.BAD)
            is LinkState.TvError -> v(LinkText.refused(s.code), Tone.BAD)
            is LinkState.Connected -> v(LinkText.connected(s.tvName, s.route), Tone.GOOD, hint = if (m.reconnectingHint) LinkText.lost(LossSide.TV).title else null)
            is LinkState.Degraded -> v(LinkText.degraded(s.tvName), Tone.WARN, hint = if (m.reconnectingHint) LinkText.lost(LossSide.TV).title else null)
            is LinkState.Reconnecting -> v(LinkText.lost(s.side), Tone.WARN, busy = true)
        }
    }
}
