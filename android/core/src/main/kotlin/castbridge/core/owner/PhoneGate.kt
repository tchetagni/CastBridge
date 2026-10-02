package castbridge.core.owner

/**
 * Phone gate (docs/coordination/DESIGN-W6-PARENTAL-PHONE-GATE.md § 3): the phone mirror of [FeatureGate]/[Feature]. Without a valid proof of a production TV the phone offers only the
 * minimal whitelist ([MINIMAL_WHITELIST]); a test pins both whitelists. Pure Kotlin, no Android.
 *
 * [minimalAllowed]: reachable in [PhoneGateState.Minimal]. [agentAllowed]: reachable in [PhoneGateState.Agent] (focal point). [tvFacing]: the function acts on a TV, so its matrix cell
 * is judged on the TARGET TV (family (i) of § 3.7); the others are phone-own functions (family (ii)).
 */
enum class PhoneFeature(val minimalAllowed: Boolean = false, val agentAllowed: Boolean = false, val tvFacing: Boolean = false) {
    // ---- minimal surface (also reachable by an agent) ----
    USAGE_NOTICE(true, true), PRIVACY_SCREEN(true, true), DISPLAY_LANGUAGE(true, true), TV_PAIRING(true, true), SHARE_DEVICE_CODE(true, true),
    CARRY_ACTIVATION_FOR_TV(true, true), FREE_CONTENT_DOWNLOAD(true, true),
    CAST_TO_LINKED_TV(true, true, tvFacing = true), LEARN_REMOTE(true, true, tvFacing = true), TRIAL_LOTS_SYNC(true, true, tvFacing = true),
    INTERNET_GATEWAY_FOR_TV(true, true, tvFacing = true),
    TELEMETRY_CONSENT(true, true), HELP(true, true), UPDATES_PHONE(true, true), SUPER_ADMIN_ENTRY(true, true), FOCAL_ENTRY(true, true), SHOP_BROWSE(true, true),

    // ---- closed without proof ----
    PHONE_LIBRARY_PLAYER, SEND_FILES_TO_TV(tvFacing = true), TV_LIBRARY_BROWSE(tvFacing = true), TV_LIBRARY_MANAGE(tvFacing = true), TV_ADMIN(tvFacing = true),
    LEARN_PHONE, QUIZ_PHONE, CHESS_PHONE, GAMES_PHONE, DOWNLOADS, LOTS_SYNC_FULL(tvFacing = true), SHOP_ORDER, TOKENS, PARENTAL_DASHBOARD,
    PARENTAL_RULES(tvFacing = true), REMOTE_TUNNEL_GATEWAY(tvFacing = true), ASSISTANT_IA, TRANSFER_MULTIPATH(tvFacing = true),

    // ---- agent (focal point) only ----
    AGENT_SELL_KEYS(agentAllowed = true), AGENT_SELL_VOUCHERS(agentAllowed = true), AGENT_CONFIRM_ORDERS(agentAllowed = true), AGENT_READ_TV_REQUEST(agentAllowed = true)
}

val MINIMAL_WHITELIST: Set<PhoneFeature> = PhoneFeature.values().filter { it.minimalAllowed }.toSet()
val AGENT_WHITELIST: Set<PhoneFeature> = PhoneFeature.values().filter { it.agentAllowed }.toSet()

/** Same shape as [ActivationRequirement]; the compile switch `REQUIRE_TV_PROOF` (off by default, wired by w6-11) feeds [required]. [FleetMigration] is reused as is for the absolute grace. */
data class ProofRequirement(val required: Boolean = false, val graceDays: Int = 14) {
    init { require(graceDays in 0..365) }
    /** Same data as the TV requirement, so [FleetMigration.graceUntil] can be reused unchanged. */
    internal fun asActivation() = ActivationRequirement(required, graceDays)
}

/**
 * What the phone knows of one TV proof (plain data: the real proof is w6-03). Valid while `now < min(validUntil, endsAt)`; never valid again once [validUntil] has passed.
 */
data class ProofSummary(val tvCode: String, val tvName: String, val verifiedAt: Long, val validUntil: Long, val endsAt: Long? = null) {
    fun validAt(nowMs: Long): Boolean = nowMs < minOf(validUntil, endsAt ?: Long.MAX_VALUE)
}

enum class MinimalReason { NO_PROOF, PROOF_EXPIRED }

sealed class PhoneGateState {
    /** The requirement is off (development, owner's own devices). */
    object NotRequired : PhoneGateState()
    /** An existing install inside its absolute grace period: everything is open for the phone (the TV stays judge). */
    data class Grace(val untilMs: Long) : PhoneGateState()
    data class Minimal(val reason: MinimalReason) : PhoneGateState()
    /** At least one valid proof of a production TV. */
    data class Linked(val proofs: List<ProofSummary>) : PhoneGateState()
    /** Verified focal-point delegation. */
    object Agent : PhoneGateState()
    /** Active super-administrator session. */
    object Super : PhoneGateState()
}

/** What the phone knows about a TV (the matrix columns are derived from it, see [PhoneGate.columnOf]). */
enum class TvEditionState { NONE_PAIRED, NEVER_SYNCED, TRIAL, GRACE, LOCKED, PRODUCTION, DEGRADED, SUSPENDED }

/** Freshness of the last proof of a TV. UNREACHABLE = the TV does not answer (a valid cached proof, if any, stays valid). */
enum class SyncClass { FRESH, STALE, EXPIRED, UNREACHABLE }

/** Message identifiers of the catalogue (§ 3.8). Constants only: the French sentences live in PhoneGateTexts (w6-02). */
object PhoneMessages {
    const val NO_TV = "M-NO-TV"
    const val SYNC_FIRST = "M-SYNC-FIRST"
    const val SYNCING = "M-SYNCING"
    const val TV_TRIAL = "M-TV-TRIAL"
    const val TV_GRACE = "M-TV-GRACE"
    const val TV_LOCKED = "M-TV-LOCKED"
    const val TV_ENDED = "M-TV-ENDED"
    const val TV_SUSPENDED = "M-TV-SUSPENDED"
    const val PROOF_EXPIRED = "M-PROOF-EXPIRED"
    const val PROOF_STALE = "M-PROOF-STALE"
    const val TV_UNREACHABLE = "M-TV-UNREACHABLE"
    const val BT_OFF = "M-BT-OFF"
    const val WRONG_NETWORK = "M-WRONG-NETWORK"
    const val CLOCK_DOUBT = "M-CLOCK-DOUBT"
    const val PROOF_REJECTED = "M-PROOF-REJECTED"
    const val IDENTITY_CHANGED = "M-IDENTITY-CHANGED"
    const val TV_OLD_VERSION = "M-TV-OLD-VERSION"
    const val TV_REFUSED = "M-TV-REFUSED"
    const val PARENTAL_BLOCKED = "M-PARENTAL-BLOCKED"
    const val TRIAL_LOTS_ONLY = "M-TRIAL-LOTS-ONLY"
    const val SHOP_READ_ONLY = "M-SHOP-READ-ONLY"
}

/** Columns A-H of the matrix of § 3.7. */
enum class PhoneColumn { A, B, C, D, E, F, G, H }

/** One cell of the matrix: open, closed (message id), partial (message id), or open with the TV deciding live (its refusal is forwarded as is). */
sealed class Cell {
    object OPEN : Cell()
    object TV_DECIDES : Cell()
    data class CLOSED(val messageId: String) : Cell()
    data class PARTIAL(val messageId: String) : Cell()
}

object PhoneGate {
    /** Order: not required, super, a valid proof, agent, absolute grace, minimal. */
    fun state(req: ProofRequirement, proofs: List<ProofSummary>, nowMs: Long, migration: FleetMigration?, superActive: Boolean, agentActive: Boolean): PhoneGateState {
        if (!req.required) return PhoneGateState.NotRequired
        if (superActive) return PhoneGateState.Super
        val valid = proofs.filter { it.validAt(nowMs) }
        if (valid.isNotEmpty()) return PhoneGateState.Linked(valid)
        if (agentActive) return PhoneGateState.Agent
        val until = migration?.graceUntil(req.asActivation())
        if (until != null && nowMs < until) return PhoneGateState.Grace(until)
        return PhoneGateState.Minimal(if (proofs.isEmpty()) MinimalReason.NO_PROOF else MinimalReason.PROOF_EXPIRED)
    }

    fun canUse(feature: PhoneFeature, state: PhoneGateState): Boolean = when (state) {
        PhoneGateState.NotRequired, PhoneGateState.Super, is PhoneGateState.Grace, is PhoneGateState.Linked -> true
        PhoneGateState.Agent -> feature.agentAllowed
        is PhoneGateState.Minimal -> feature.minimalAllowed
    }

    /**
     * Which column of § 3.7 a TV falls into. Super session = H. A production TV is D (fresh or stale proof), F (unreachable, cached proof valid) or G (proof expired);
     * a TV that is not in production keeps the column of its edition (C trial/grace, E ended/locked/degraded/suspended): an unreachable trial TV stays C, not F.
     */
    fun columnOf(tv: TvEditionState, sync: SyncClass, superSession: Boolean): PhoneColumn = when {
        superSession -> PhoneColumn.H
        tv == TvEditionState.NONE_PAIRED -> PhoneColumn.A
        tv == TvEditionState.NEVER_SYNCED -> PhoneColumn.B
        tv == TvEditionState.TRIAL || tv == TvEditionState.GRACE -> PhoneColumn.C
        tv == TvEditionState.PRODUCTION -> when (sync) {
            SyncClass.FRESH, SyncClass.STALE -> PhoneColumn.D
            SyncClass.UNREACHABLE -> PhoneColumn.F
            SyncClass.EXPIRED -> PhoneColumn.G
        }
        else -> PhoneColumn.E
    }

    /**
     * The matrix of § 3.7 for [feature], on the TV described by [tv] and [sync]. [phoneGrace] = the phone is in its own absolute grace: columns read as D (the TV stays judge:
     * a TV-facing OPEN becomes [Cell.TV_DECIDES]). Column H never closes, except a TV-facing function whose TV is unreachable (then the TV cannot decide).
     */
    fun cell(feature: PhoneFeature, tv: TvEditionState, sync: SyncClass, superSession: Boolean, phoneGrace: Boolean = false): Cell {
        if (phoneGrace && !superSession) {
            val d = ROWS.getValue(feature)[PhoneColumn.D.ordinal]
            return if (feature.tvFacing && d == Cell.OPEN) Cell.TV_DECIDES else d
        }
        val col = columnOf(tv, sync, superSession)
        val base = ROWS.getValue(feature)[col.ordinal]
        if (col == PhoneColumn.H) {
            val f = ROWS.getValue(feature)[PhoneColumn.F.ordinal]
            if (feature.tvFacing && sync == SyncClass.UNREACHABLE && tv != TvEditionState.NONE_PAIRED && f is Cell.CLOSED) return f
            return base
        }
        return when {
            col == PhoneColumn.C && tv == TvEditionState.GRACE -> swap(base, PhoneMessages.TV_TRIAL, PhoneMessages.TV_GRACE)
            col == PhoneColumn.E && tv == TvEditionState.LOCKED -> swap(base, PhoneMessages.TV_ENDED, PhoneMessages.TV_LOCKED)
            col == PhoneColumn.E && tv == TvEditionState.SUSPENDED -> swap(base, PhoneMessages.TV_ENDED, PhoneMessages.TV_SUSPENDED)
            else -> base
        }
    }

    private fun swap(c: Cell, from: String, to: String): Cell = when {
        c is Cell.CLOSED && c.messageId == from -> Cell.CLOSED(to)
        c is Cell.PARTIAL && c.messageId == from -> Cell.PARTIAL(to)
        else -> c
    }

    /**
     * The cell that applies to an action: a TV-facing [feature] is judged on the TV at index [active] (no TV, or an invalid index: column A). A phone-own feature is open as soon as
     * ANY TV opens it (a household with a production TV and a trial TV keeps the phone player); otherwise the least bad cell (TV decides, then partial, then closed) with its message.
     */
    fun targetRule(feature: PhoneFeature, tvs: List<Pair<TvEditionState, SyncClass>>, active: Int?, superSession: Boolean = false): Cell {
        if (feature.tvFacing) {
            val t = active?.let { tvs.getOrNull(it) } ?: Pair(TvEditionState.NONE_PAIRED, SyncClass.UNREACHABLE)
            return cell(feature, t.first, t.second, superSession)
        }
        val cells = if (tvs.isEmpty()) listOf(cell(feature, TvEditionState.NONE_PAIRED, SyncClass.UNREACHABLE, superSession)) else tvs.map { cell(feature, it.first, it.second, superSession) }
        return cells.firstOrNull { it == Cell.OPEN } ?: cells.firstOrNull { it == Cell.TV_DECIDES } ?: cells.firstOrNull { it is Cell.PARTIAL } ?: cells.first()
    }

    // ---- the matrix, one row per feature, columns A..H ----
    private val O: Cell = Cell.OPEN
    private val T: Cell = Cell.TV_DECIDES
    private fun x(id: String): Cell = Cell.CLOSED(id)
    private fun p(id: String): Cell = Cell.PARTIAL(id)
    private val NT = PhoneMessages.NO_TV
    private val SF = PhoneMessages.SYNC_FIRST
    private val TR = PhoneMessages.TV_TRIAL
    private val EN = PhoneMessages.TV_ENDED
    private val UN = PhoneMessages.TV_UNREACHABLE
    private val PE = PhoneMessages.PROOF_EXPIRED
    private val ALWAYS = List(8) { O }
    /** Phone-own function with value: closed until a production TV (D, F), also kept open by nothing else. */
    private val OWN_VALUE = listOf(x(NT), x(SF), x(TR), O, x(EN), O, x(PE), O)
    private val SEND = listOf(x(NT), x(SF), x(TR), O, x(EN), x(UN), x(PE), T)
    private val LIVE = listOf(x(NT), T, T, O, T, x(UN), T, O)

    private val ROWS: Map<PhoneFeature, List<Cell>> = mapOf(
        PhoneFeature.USAGE_NOTICE to ALWAYS, PhoneFeature.PRIVACY_SCREEN to ALWAYS, PhoneFeature.DISPLAY_LANGUAGE to ALWAYS, PhoneFeature.TV_PAIRING to ALWAYS,
        PhoneFeature.SHARE_DEVICE_CODE to ALWAYS, PhoneFeature.CARRY_ACTIVATION_FOR_TV to ALWAYS, PhoneFeature.FREE_CONTENT_DOWNLOAD to ALWAYS,
        PhoneFeature.TELEMETRY_CONSENT to ALWAYS, PhoneFeature.HELP to ALWAYS, PhoneFeature.UPDATES_PHONE to ALWAYS, PhoneFeature.SUPER_ADMIN_ENTRY to ALWAYS,
        PhoneFeature.FOCAL_ENTRY to ALWAYS,
        PhoneFeature.AGENT_SELL_KEYS to ALWAYS, PhoneFeature.AGENT_SELL_VOUCHERS to ALWAYS, PhoneFeature.AGENT_CONFIRM_ORDERS to ALWAYS, PhoneFeature.AGENT_READ_TV_REQUEST to ALWAYS,
        PhoneFeature.INTERNET_GATEWAY_FOR_TV to listOf(x(NT), O, O, O, O, x(UN), O, O),
        PhoneFeature.CAST_TO_LINKED_TV to LIVE, PhoneFeature.LEARN_REMOTE to LIVE,
        PhoneFeature.SEND_FILES_TO_TV to SEND, PhoneFeature.TRANSFER_MULTIPATH to SEND,
        PhoneFeature.TV_LIBRARY_BROWSE to listOf(x(NT), x(SF), x(TR), O, O, x(UN), x(PE), T),
        PhoneFeature.TV_LIBRARY_MANAGE to SEND,
        PhoneFeature.TRIAL_LOTS_SYNC to listOf(x(NT), O, O, O, x(EN), O, O, O),
        PhoneFeature.LOTS_SYNC_FULL to listOf(x(NT), p(PhoneMessages.TRIAL_LOTS_ONLY), p(PhoneMessages.TRIAL_LOTS_ONLY), O, x(EN), O, p(PE), O),
        PhoneFeature.SHOP_BROWSE to listOf(x(NT), p(PhoneMessages.SHOP_READ_ONLY), p(PhoneMessages.SHOP_READ_ONLY), O, p(PhoneMessages.SHOP_READ_ONLY), O, p(PhoneMessages.SHOP_READ_ONLY), O),
        PhoneFeature.SHOP_ORDER to OWN_VALUE, PhoneFeature.TOKENS to OWN_VALUE, PhoneFeature.DOWNLOADS to OWN_VALUE, PhoneFeature.PHONE_LIBRARY_PLAYER to OWN_VALUE,
        PhoneFeature.LEARN_PHONE to OWN_VALUE, PhoneFeature.QUIZ_PHONE to OWN_VALUE, PhoneFeature.CHESS_PHONE to OWN_VALUE, PhoneFeature.GAMES_PHONE to OWN_VALUE,
        PhoneFeature.ASSISTANT_IA to OWN_VALUE,
        PhoneFeature.PARENTAL_DASHBOARD to listOf(x(NT), x(SF), x(TR), O, O, O, p(PE), O),
        PhoneFeature.PARENTAL_RULES to listOf(x(NT), x(SF), x(TR), O, O, x(UN), x(PE), T),
        PhoneFeature.TV_ADMIN to listOf(x(NT), x(SF), x(TR), O, p(EN), x(UN), x(PE), T),
        PhoneFeature.REMOTE_TUNNEL_GATEWAY to listOf(x(NT), x(SF), x(TR), O, O, x(UN), x(PE), O)
    ).also { m -> check(m.keys.size == PhoneFeature.values().size) { "PhoneGate matrix: a feature has no row" } }
}
