package castbridge.core.trust

import castbridge.core.tv.BtProtocol

/** What the pairing flow needs from Android's Bluetooth stack (a fake in the tests). Blocking calls: run on an IO thread. */
interface PairEnv {
    fun btProblem(): BtUnavailable.Reason?
    fun bond(address: String): BondState
    /** Starts Android's pairing (numeric comparison on both screens). False when Android refuses to start it. */
    fun createBond(address: String): Boolean
    /** Waits until the bond reaches [target] (events of BOND_STATE_CHANGED) or [timeoutMs] passes; returns the state seen last. */
    fun awaitBond(address: String, target: BondState, timeoutMs: Long): BondState
    fun sleep(ms: Long)
    fun now(): Long
}

/** What the screen shows during « Ajouter ma TV » / « Réassocier »: one [advice], with its single action when there is one. */
sealed class PairStep {
    abstract val advice: Advice
    object Bonding : PairStep() { override val advice = Advice("Association avec la TV", "Android affiche un code sur le téléphone et sur la TV. S'ils sont identiques, validez sur les deux.") }
    /** Android keeps a bond the TV no longer knows (TV reinstalled, Bluetooth reset): the user removes it in the system settings; the flow goes on by itself. */
    object StaleBond : PairStep() { override val advice = LinkText.staleBond }
    /** The TV is reachable but not showing « Ajouter un téléphone » (or its app is closed): polled every few seconds until the owner opens it. */
    data class WaitingTvWindow(val code: Int, val secondsLeft: Long) : PairStep() {
        override val advice get() = Advice("Ouvrez « Ajouter un téléphone » sur la TV",
            if (code == BtProtocol.ERR_MAGIC) "CastBridge-TV n'est pas ouvert sur la TV : ouvrez-le, puis « Ajouter un téléphone ». Nouvel essai automatique ($secondsLeft s)."
            else "Sur la TV : CastBridge-TV › « Ajouter un téléphone ». Nouvel essai automatique ($secondsLeft s).")
    }
    object WaitingOwner : PairStep() { override val advice = Advice("Validez sur la TV", "La TV demande « Autoriser ce téléphone à piloter cette TV ? ». Choisissez « Autoriser » avec la télécommande.") }
    data class Done(val session: LinkSession) : PairStep() { override val advice = Advice("${session.tv.name} est ajoutée", "Elle se connectera toute seule dès que vous ouvrirez l'app, sans code.") }
    data class Failed(override val advice: Advice, val canRetry: Boolean) : PairStep()
}

/**
 * The whole pairing / re-pairing flow, phone side, as one pure sequence over [PairEnv] and [PhoneLink]:
 * bond (Android) → ask the TV to trust this phone (the owner approves on the TV) → done. It carries the recoveries by itself:
 *
 * - the TV is not in « Ajouter un téléphone » yet: poll every [pollMs] until [windowMs] and say what to open;
 * - another phone is being asked: wait and retry a few times;
 * - the bond is stale (accepted then closed at once, repeatedly, or refused as unknown while paired): guide to the Bluetooth settings,
 *   notice the bond disappearing, create it again, and continue without the user coming back to this screen;
 * - the owner denied: no retry (the TV blocks that phone for 10 minutes after three refusals).
 */
class PairFlow(
    private val link: PhoneLink,
    private val env: PairEnv,
    private val windowMs: Long = 150_000,
    private val pollMs: Long = 3_000,
    private val bondTimeoutMs: Long = 90_000,
    private val staleBondWaitMs: Long = 5 * 60_000,
    private val busyRetries: Int = 4,
) {
    fun run(tv: SavedTv, onStep: (PairStep) -> Unit): PairStep {
        fun end(s: PairStep): PairStep { onStep(s); return s }
        env.btProblem()?.let { return end(PairStep.Failed(LinkText.bluetooth(it), canRetry = it != BtUnavailable.Reason.NO_ADAPTER)) }
        val address = TrustRegistry.norm(tv.address)
        if (env.bond(address) != BondState.BONDED) bond(address, onStep)?.let { return end(it) }
        var deadline = env.now() + windowMs
        var instant = 0; var busy = 0; var repairs = 0
        /** Stale bond: guide the user, wait for the bond to go, pair again, restart the poll. Null = go on, else the final failure. */
        fun repair(): PairStep? {
            if (++repairs > 1) return end(PairStep.Failed(LinkText.staleBond, canRetry = true))   // removed and paired again, still refused: say so, do not loop
            val failed = staleBond(address, onStep)
            instant = 0; busy = 0; deadline = env.now() + windowMs
            return failed?.let { end(it) }
        }
        while (true) {
            onStep(PairStep.WaitingOwner)
            when (val r = link.connect(tv, requestTrust = true)) {
                is PhoneLink.Result.Connected -> return end(PairStep.Done(r.session))
                is PhoneLink.Result.BluetoothProblem -> return end(PairStep.Failed(LinkText.bluetooth(r.reason), canRetry = r.reason != BtUnavailable.Reason.NO_ADAPTER))
                is PhoneLink.Result.Refused -> when (r.code) {
                    BtProtocol.ERR_NOT_OPEN -> if (!wait(deadline, BtProtocol.ERR_NOT_OPEN, onStep)) return end(PairStep.Failed(LinkText.refused(BtProtocol.ERR_NOT_OPEN), canRetry = true))
                    BtProtocol.ERR_BUSY -> if (++busy > busyRetries || !wait(deadline, BtProtocol.ERR_BUSY, onStep, 5_000)) return end(PairStep.Failed(LinkText.refused(BtProtocol.ERR_BUSY), canRetry = true))
                    BtProtocol.ERR_TIMEOUT -> return end(PairStep.Failed(LinkText.refused(BtProtocol.ERR_TIMEOUT), canRetry = true))
                    BtProtocol.ERR_DENIED -> return end(PairStep.Failed(LinkText.refused(BtProtocol.ERR_DENIED), canRetry = false))
                    BtProtocol.ERR_MAGIC -> return end(PairStep.Failed(LinkText.refused(BtProtocol.ERR_MAGIC), canRetry = true))
                    // paired on the phone but the TV says it has no pairing with us: the bond is one-sided, only removing it here helps
                    BtProtocol.ERR_UNTRUSTED -> repair()?.let { return it }
                    else -> return end(PairStep.Failed(LinkText.refused(r.code), canRetry = true))
                }
                is PhoneLink.Result.TvAbsent -> when (r.kind) {
                    AbsentKind.CLOSED_AT_ONCE -> if (++instant >= 2) { repair()?.let { return it } }
                        else if (!wait(deadline, BtProtocol.ERR_MAGIC, onStep, 1_000)) return end(PairStep.Failed(LinkText.absent(r.kind, tv.name), canRetry = true))
                    AbsentKind.SERVICE_ABSENT -> if (!wait(deadline, BtProtocol.ERR_MAGIC, onStep)) return end(PairStep.Failed(LinkText.absent(r.kind, tv.name), canRetry = true))
                    AbsentKind.NO_ANSWER -> if (!wait(deadline, BtProtocol.ERR_NOT_OPEN, onStep)) return end(PairStep.Failed(LinkText.absent(r.kind, tv.name), canRetry = true))
                }
            }
        }
    }

    /** Creates the bond and waits for it. Null = bonded; else the failure to show. */
    private fun bond(address: String, onStep: (PairStep) -> Unit): PairStep? {
        onStep(PairStep.Bonding)
        if (env.bond(address) == BondState.NONE && !env.createBond(address))
            return PairStep.Failed(Advice("Association impossible", "Android n'a pas pu lancer l'association Bluetooth. Vérifiez que la TV est à proximité et que son Bluetooth est allumé.", LinkAction.RETRY), canRetry = true)
        val s = env.awaitBond(address, BondState.BONDED, bondTimeoutMs)
        return if (s == BondState.BONDED) null
        else PairStep.Failed(Advice("Association non terminée", "L'association Bluetooth n'a pas abouti. Vérifiez que les codes affichés sur le téléphone et sur la TV sont identiques, puis réessayez.", LinkAction.RETRY), canRetry = true)
    }

    private fun wait(deadline: Long, code: Int, onStep: (PairStep) -> Unit, ms: Long = pollMs): Boolean {
        val left = deadline - env.now()
        if (left <= 0) return false
        onStep(PairStep.WaitingTvWindow(code, left / 1000))
        env.sleep(minOf(ms, left))
        return true
    }

    /** Guide to the system Bluetooth settings, notice the bond going away, pair again. Null = bonded again, else the failure to show. */
    private fun staleBond(address: String, onStep: (PairStep) -> Unit): PairStep? {
        onStep(PairStep.StaleBond)
        if (env.awaitBond(address, BondState.NONE, staleBondWaitMs) != BondState.NONE) return PairStep.Failed(LinkText.staleBond, canRetry = true)
        return bond(address, onStep)
    }
}
