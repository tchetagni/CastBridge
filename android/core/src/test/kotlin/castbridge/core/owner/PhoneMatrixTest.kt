package castbridge.core.owner

import kotlin.test.*

/**
 * The matrix of docs/coordination/DESIGN-W6-PARENTAL-PHONE-GATE.md § 3.7, written out literally (message ids as plain strings, not the production constants):
 * a cell that changes, or a feature without a row, fails the build. Tokens: O open, T the TV decides, X:id closed, P:id partial.
 */
class PhoneMatrixTest {
    private val columns = PhoneColumn.values().toList()

    private fun tok(s: String): Cell = when {
        s == "O" -> Cell.OPEN
        s == "T" -> Cell.TV_DECIDES
        s.startsWith("X:") -> Cell.CLOSED(s.removePrefix("X:"))
        s.startsWith("P:") -> Cell.PARTIAL(s.removePrefix("P:"))
        else -> error(s)
    }

    private val rows = LinkedHashMap<PhoneFeature, List<String>>()
    private fun row(f: PhoneFeature, vararg c: String) { check(c.size == 8); check(rows.put(f, c.toList()) == null) { "duplicate row $f" } }

    private val NO = "X:M-NO-TV"; private val SF = "X:M-SYNC-FIRST"; private val TR = "X:M-TV-TRIAL"; private val EN = "X:M-TV-ENDED"; private val UN = "X:M-TV-UNREACHABLE"; private val PE = "X:M-PROOF-EXPIRED"
    private val SHOP = "P:M-SHOP-READ-ONLY"; private val LOTS = "P:M-TRIAL-LOTS-ONLY"

    private val expected: Map<Pair<PhoneFeature, PhoneColumn>, Cell> by lazy {
        row(PhoneFeature.USAGE_NOTICE, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.PRIVACY_SCREEN, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.DISPLAY_LANGUAGE, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.TV_PAIRING, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.SHARE_DEVICE_CODE, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.CARRY_ACTIVATION_FOR_TV, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.FREE_CONTENT_DOWNLOAD, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.TELEMETRY_CONSENT, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.HELP, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.UPDATES_PHONE, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.SUPER_ADMIN_ENTRY, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.FOCAL_ENTRY, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.AGENT_SELL_KEYS, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.AGENT_SELL_VOUCHERS, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.AGENT_CONFIRM_ORDERS, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.AGENT_READ_TV_REQUEST, "O", "O", "O", "O", "O", "O", "O", "O")
        row(PhoneFeature.INTERNET_GATEWAY_FOR_TV, NO, "O", "O", "O", "O", UN, "O", "O")
        row(PhoneFeature.CAST_TO_LINKED_TV, NO, "T", "T", "O", "T", UN, "T", "O")
        row(PhoneFeature.LEARN_REMOTE, NO, "T", "T", "O", "T", UN, "T", "O")
        row(PhoneFeature.SEND_FILES_TO_TV, NO, SF, TR, "O", EN, UN, PE, "T")
        row(PhoneFeature.TRANSFER_MULTIPATH, NO, SF, TR, "O", EN, UN, PE, "T")
        row(PhoneFeature.TV_LIBRARY_BROWSE, NO, SF, TR, "O", "O", UN, PE, "T")
        row(PhoneFeature.TV_LIBRARY_MANAGE, NO, SF, TR, "O", EN, UN, PE, "T")
        row(PhoneFeature.TRIAL_LOTS_SYNC, NO, "O", "O", "O", EN, "O", "O", "O")
        row(PhoneFeature.LOTS_SYNC_FULL, NO, LOTS, LOTS, "O", EN, "O", "P:M-PROOF-EXPIRED", "O")
        row(PhoneFeature.SHOP_BROWSE, NO, SHOP, SHOP, "O", SHOP, "O", SHOP, "O")
        row(PhoneFeature.SHOP_ORDER, NO, SF, TR, "O", EN, "O", PE, "O")
        row(PhoneFeature.TOKENS, NO, SF, TR, "O", EN, "O", PE, "O")
        row(PhoneFeature.DOWNLOADS, NO, SF, TR, "O", EN, "O", PE, "O")
        row(PhoneFeature.PHONE_LIBRARY_PLAYER, NO, SF, TR, "O", EN, "O", PE, "O")
        row(PhoneFeature.LEARN_PHONE, NO, SF, TR, "O", EN, "O", PE, "O")
        row(PhoneFeature.QUIZ_PHONE, NO, SF, TR, "O", EN, "O", PE, "O")
        row(PhoneFeature.CHESS_PHONE, NO, SF, TR, "O", EN, "O", PE, "O")
        row(PhoneFeature.GAMES_PHONE, NO, SF, TR, "O", EN, "O", PE, "O")
        row(PhoneFeature.ASSISTANT_IA, NO, SF, TR, "O", EN, "O", PE, "O")
        row(PhoneFeature.PARENTAL_DASHBOARD, NO, SF, TR, "O", "O", "O", "P:M-PROOF-EXPIRED", "O")
        row(PhoneFeature.PARENTAL_RULES, NO, SF, TR, "O", "O", UN, PE, "T")
        row(PhoneFeature.TV_ADMIN, NO, SF, TR, "O", "P:M-TV-ENDED", UN, PE, "T")
        row(PhoneFeature.REMOTE_TUNNEL_GATEWAY, NO, SF, TR, "O", "O", UN, PE, "O")
        rows.flatMap { (f, cs) -> columns.mapIndexed { i, c -> (f to c) to tok(cs[i]) } }.toMap()
    }

    /** One representative input per column. */
    private fun input(c: PhoneColumn): Triple<TvEditionState, SyncClass, Boolean> = when (c) {
        PhoneColumn.A -> Triple(TvEditionState.NONE_PAIRED, SyncClass.UNREACHABLE, false)
        PhoneColumn.B -> Triple(TvEditionState.NEVER_SYNCED, SyncClass.UNREACHABLE, false)
        PhoneColumn.C -> Triple(TvEditionState.TRIAL, SyncClass.FRESH, false)
        PhoneColumn.D -> Triple(TvEditionState.PRODUCTION, SyncClass.FRESH, false)
        PhoneColumn.E -> Triple(TvEditionState.DEGRADED, SyncClass.FRESH, false)
        PhoneColumn.F -> Triple(TvEditionState.PRODUCTION, SyncClass.UNREACHABLE, false)
        PhoneColumn.G -> Triple(TvEditionState.PRODUCTION, SyncClass.EXPIRED, false)
        PhoneColumn.H -> Triple(TvEditionState.PRODUCTION, SyncClass.FRESH, true)
    }

    @Test fun theMatrixHasACellForEveryFeatureAndColumn() {
        assertEquals(PhoneFeature.values().size * 8, expected.size)
        assertEquals(PhoneFeature.values().toSet(), rows.keys.toSet(), "every PhoneFeature needs a row")
    }

    @Test fun everyCellMatchesTheLiteralMatrix() {
        val bad = expected.filter { (k, v) ->
            val (tv, sync, sup) = input(k.second)
            PhoneGate.cell(k.first, tv, sync, sup) != v
        }
        assertTrue(bad.isEmpty(), "cells that diverge: " + bad.keys.joinToString { "${it.first}/${it.second}" })
    }

    @Test fun everyClosedOrPartialCellCarriesAMessageId() {
        expected.values.forEach { c ->
            when (c) { is Cell.CLOSED -> assertTrue(c.messageId.startsWith("M-")); is Cell.PARTIAL -> assertTrue(c.messageId.startsWith("M-")); else -> {} }
        }
    }

    @Test fun freeContentIsOpenInEveryColumnAndEveryTvState() {
        for (tv in TvEditionState.values()) for (s in SyncClass.values()) for (sup in listOf(false, true)) {
            assertEquals(Cell.OPEN, PhoneGate.cell(PhoneFeature.FREE_CONTENT_DOWNLOAD, tv, s, sup))
        }
    }

    @Test fun columnsFollowTheTvStateAndTheSync() {
        assertEquals(PhoneColumn.A, PhoneGate.columnOf(TvEditionState.NONE_PAIRED, SyncClass.FRESH, false))
        assertEquals(PhoneColumn.B, PhoneGate.columnOf(TvEditionState.NEVER_SYNCED, SyncClass.UNREACHABLE, false))
        assertEquals(PhoneColumn.C, PhoneGate.columnOf(TvEditionState.TRIAL, SyncClass.UNREACHABLE, false)) // a trial TV out of reach is not F
        assertEquals(PhoneColumn.C, PhoneGate.columnOf(TvEditionState.GRACE, SyncClass.FRESH, false))
        assertEquals(PhoneColumn.D, PhoneGate.columnOf(TvEditionState.PRODUCTION, SyncClass.STALE, false))
        assertEquals(PhoneColumn.F, PhoneGate.columnOf(TvEditionState.PRODUCTION, SyncClass.UNREACHABLE, false))
        assertEquals(PhoneColumn.G, PhoneGate.columnOf(TvEditionState.PRODUCTION, SyncClass.EXPIRED, false))
        for (s in listOf(TvEditionState.LOCKED, TvEditionState.DEGRADED, TvEditionState.SUSPENDED)) assertEquals(PhoneColumn.E, PhoneGate.columnOf(s, SyncClass.FRESH, false))
        assertEquals(PhoneColumn.H, PhoneGate.columnOf(TvEditionState.TRIAL, SyncClass.FRESH, true))
    }

    @Test fun lockedAndSuspendedTvsSwapTheEndedMessage() {
        assertEquals(Cell.CLOSED("M-TV-LOCKED"), PhoneGate.cell(PhoneFeature.SEND_FILES_TO_TV, TvEditionState.LOCKED, SyncClass.FRESH, false))
        assertEquals(Cell.CLOSED("M-TV-SUSPENDED"), PhoneGate.cell(PhoneFeature.SEND_FILES_TO_TV, TvEditionState.SUSPENDED, SyncClass.FRESH, false))
        assertEquals(Cell.CLOSED("M-TV-GRACE"), PhoneGate.cell(PhoneFeature.SEND_FILES_TO_TV, TvEditionState.GRACE, SyncClass.FRESH, false))
        assertEquals(Cell.CLOSED("M-TV-ENDED"), PhoneGate.cell(PhoneFeature.SEND_FILES_TO_TV, TvEditionState.DEGRADED, SyncClass.FRESH, false))
    }

    @Test fun superSessionNeverClosesExceptATvFacingFunctionWhoseTvIsUnreachable() {
        for (f in PhoneFeature.values()) {
            val c = PhoneGate.cell(f, TvEditionState.PRODUCTION, SyncClass.FRESH, true)
            assertTrue(c is Cell.OPEN || c is Cell.TV_DECIDES, "$f closed in H")
        }
        assertEquals(Cell.CLOSED("M-TV-UNREACHABLE"), PhoneGate.cell(PhoneFeature.SEND_FILES_TO_TV, TvEditionState.PRODUCTION, SyncClass.UNREACHABLE, true))
        assertEquals(Cell.OPEN, PhoneGate.cell(PhoneFeature.PHONE_LIBRARY_PLAYER, TvEditionState.PRODUCTION, SyncClass.UNREACHABLE, true))
        assertEquals(Cell.OPEN, PhoneGate.cell(PhoneFeature.LOTS_SYNC_FULL, TvEditionState.PRODUCTION, SyncClass.UNREACHABLE, true)) // delivery is deferred, not closed
    }

    @Test fun phoneGraceReadsAsDWithTheTvStillJudge() {
        assertEquals(Cell.OPEN, PhoneGate.cell(PhoneFeature.PHONE_LIBRARY_PLAYER, TvEditionState.NONE_PAIRED, SyncClass.UNREACHABLE, false, phoneGrace = true))
        assertEquals(Cell.TV_DECIDES, PhoneGate.cell(PhoneFeature.SEND_FILES_TO_TV, TvEditionState.TRIAL, SyncClass.FRESH, false, phoneGrace = true))
    }

    @Test fun targetRuleJudgesTvFacingFunctionsOnTheActiveTv() {
        val tvs = listOf(TvEditionState.TRIAL to SyncClass.FRESH, TvEditionState.PRODUCTION to SyncClass.FRESH)
        assertEquals(Cell.CLOSED("M-TV-TRIAL"), PhoneGate.targetRule(PhoneFeature.SEND_FILES_TO_TV, tvs, 0))
        assertEquals(Cell.OPEN, PhoneGate.targetRule(PhoneFeature.SEND_FILES_TO_TV, tvs, 1))
        assertEquals(Cell.CLOSED("M-NO-TV"), PhoneGate.targetRule(PhoneFeature.SEND_FILES_TO_TV, tvs, null))
        assertEquals(Cell.CLOSED("M-NO-TV"), PhoneGate.targetRule(PhoneFeature.SEND_FILES_TO_TV, tvs, 7))
    }

    @Test fun targetRuleOpensPhoneOwnFunctionsWhenAnyTvIsProven() {
        val trialAndProd = listOf(TvEditionState.TRIAL to SyncClass.FRESH, TvEditionState.PRODUCTION to SyncClass.STALE)
        assertEquals(Cell.OPEN, PhoneGate.targetRule(PhoneFeature.PHONE_LIBRARY_PLAYER, trialAndProd, 0))
        val unreachableProd = listOf(TvEditionState.TRIAL to SyncClass.FRESH, TvEditionState.PRODUCTION to SyncClass.UNREACHABLE)
        assertEquals(Cell.OPEN, PhoneGate.targetRule(PhoneFeature.GAMES_PHONE, unreachableProd, 0))
    }

    @Test fun targetRuleReturnsTheLeastBadCellWithItsMessage() {
        val trialOnly = listOf(TvEditionState.TRIAL to SyncClass.FRESH)
        assertEquals(Cell.CLOSED("M-TV-TRIAL"), PhoneGate.targetRule(PhoneFeature.PHONE_LIBRARY_PLAYER, trialOnly, 0))
        assertEquals(Cell.CLOSED("M-NO-TV"), PhoneGate.targetRule(PhoneFeature.PHONE_LIBRARY_PLAYER, emptyList(), null))
        // partial beats closed: a TV with an expired proof (read-only dashboard) next to a trial TV
        val mixed = listOf(TvEditionState.TRIAL to SyncClass.FRESH, TvEditionState.PRODUCTION to SyncClass.EXPIRED)
        assertEquals(Cell.PARTIAL("M-PROOF-EXPIRED"), PhoneGate.targetRule(PhoneFeature.PARENTAL_DASHBOARD, mixed, 0))
    }
}
