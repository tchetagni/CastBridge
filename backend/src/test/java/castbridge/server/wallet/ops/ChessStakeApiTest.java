package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.wallet.core.Currency;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Les échecs en ligne avec mise côté API (games-G2) : le blocage {@code cbe1} d'un jeu suit l'échelle du jeu, un seul siège, l'essai n'y mise pas, les plafonds de parties gagnées par identité ;
 * le résultat {@code cbr1} d'un duel règle les deux mises (gagnant, nulle, interruption), écrit le journal dans la même transaction, applique les frais de la politique (0 au lancement) et ne
 * règle jamais une forme impossible. Réutilise le matériel de {@link OpsTestBase} (TV réelles, licences, clé de résultat de test).
 */
class ChessStakeApiTest extends OpsTestBase {
    private static final String ROOM_A = "0123456789abcdef0123456789abcdef";

    // ---- matériel ----

    private Reply chessEscrow(Tv tv, String cur, long per, int k, String idem) throws Exception {
        ObjectNode body = req(tv).put("cur", cur).put("per", per).put("k", k).put("idem", idem).put("game", "chess");
        ArrayNode acts = body.putArray("activations");
        tv.activations().forEach(acts::add);
        return postJson(tv.auth(), "/api/v1/wallet/escrow", body);
    }

    /** Une TV de production avec assez de NDEM et de MBOKO pour toute l'échelle. */
    private Tv richTv() throws Exception {
        Tv tv = productionTv();
        adminGrant(tv.code(), "NDEM", 5_000);
        adminGrant(tv.code(), "MBOKO", 50);
        return tv;
    }

    private String eid(Reply r) { return r.json().get("eid").asText(); }

    /** Un {@code cbr1} d'échecs : même format que le service de jeu (jeu « chess », la salle du service), signé par la clé de résultat de test. */
    private static String chessResult(String rid, String room, String cur, long per, String kind, List<Line> lines) {
        StringBuilder sb = new StringBuilder("{\"kid\":\"").append(castbridge.server.licenses.LicenseKeyring.kidOf(rawPublicBytes(RESULT))).append("\",\"rid\":\"").append(rid).append("\",\"room\":\"").append(room)
                .append("\",\"game\":\"chess\",\"cur\":\"").append(cur).append("\",\"per\":").append(per).append(",\"kind\":\"").append(kind).append("\",\"at\":").append(NOW).append(",\"lines\":[");
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            sb.append(i == 0 ? "" : ",").append("[\"").append(l.eid()).append("\",\"").append(l.id()).append("\",").append(l.used()).append(',').append(l.pay()).append(']');
        }
        return sign(RESULT, sb.append("]}").toString());
    }

    private record Duel(Tv a, Tv b, String eidA, String eidB) {}

    private Duel duel(String cur, long per, String idem) throws Exception {
        Tv a = richTv(), b = richTv();
        Reply ra = chessEscrow(a, cur, per, 1, idem + "-a"), rb = chessEscrow(b, cur, per, 1, idem + "-b");
        assertEquals(200, ra.status(), ra.json().toString());
        assertEquals(200, rb.status(), rb.json().toString());
        return new Duel(a, b, eid(ra), eid(rb));
    }

    private List<Line> lines(Duel d, long per, long payA, long payB) { return List.of(new Line(d.eidA(), d.a().code(), per, payA), new Line(d.eidB(), d.b().code(), per, payB)); }

    private long journalRows(String rid) { return count("SELECT COUNT(*) FROM wallet_game_log WHERE rid = ?", rid); }

    // ---- le blocage ----

    @Test
    void theEscrowOfAChessGameFollowsTheScaleAndRecordsTheGame() throws Exception {
        Tv tv = richTv();
        for (long per : new long[] {10, 20, 50, 100, 200}) {
            Reply r = chessEscrow(tv, "NDEM", per, 1, "scale-ndem-" + per);
            assertEquals(200, r.status(), per + " NDEM : " + r.json());
            assertEquals("chess", jdbc.queryForObject("SELECT game FROM wallet_escrow WHERE eid = ?", String.class, eid(r)), "le jeu est inscrit avec le blocage");
        }
        for (long per : new long[] {1, 2, 5, 10}) assertEquals(200, chessEscrow(tv, "MBOKO", per, 1, "scale-mboko-" + per).status(), per + " MBOKO");
        long before = bal(tv, Currency.NDEM);
        for (long per : new long[] {30, 1, 15, 201, 1000}) {
            Reply r = chessEscrow(tv, "NDEM", per, 1, "off-scale-" + per);
            assertEquals(409, r.status(), per + " NDEM n'est pas dans l'échelle");
            assertEquals("STAKE_NOT_OFFERED", r.reason());
            assertTrue(r.message().contains("10, 20, 50, 100, 200 NDEM"), r.message());
        }
        assertEquals(409, chessEscrow(tv, "MBOKO", 3, 1, "off-scale-m3").status());
        assertEquals(before, bal(tv, Currency.NDEM), "un refus ne bloque rien");
        assertReconciled();
    }

    @Test
    void aChessGameHasOneSeatPerTv() throws Exception {
        Tv tv = richTv();
        Reply r = chessEscrow(tv, "NDEM", 20, 2, "two-seats");
        assertEquals(400, r.status());
        assertEquals("BAD_TXN", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_escrow WHERE holder = ?", tv.code()));
    }

    @Test
    void aTrialTvDoesNotStakeAtChessNotEvenNdemButStillStakesAtTheQuiz() throws Exception {
        Tv tv = trialTv();
        long before = bal(tv, Currency.NDEM);
        Reply r = chessEscrow(tv, "NDEM", 10, 1, "trial-chess");
        assertEquals(409, r.status());
        assertEquals("TRIAL_FREE_ONLY", r.reason());
        assertEquals("Version d'essai : parties libres seulement, sans mise", r.message());
        assertEquals(before, bal(tv, Currency.NDEM));
        assertEquals(200, escrow(tv, "NDEM", 10, 1, "trial-quiz").status(), "le Quiz en ligne garde ses règles : une TV d'essai mise en NDEM");
    }

    @Test
    void anUnknownGameAndABadGameNameAreRefusedAndNoGameMeansTheQuiz() throws Exception {
        Tv tv = richTv();
        ObjectNode body = req(tv).put("cur", "NDEM").put("per", 20).put("k", 1).put("idem", "game-unknown").put("game", "poker");
        tv.activations().forEach(body.putArray("activations")::add);
        assertEquals(400, postJson(tv.auth(), "/api/v1/wallet/escrow", body).status());
        body.put("game", "Chess!").put("idem", "game-bad");
        assertEquals(400, postJson(tv.auth(), "/api/v1/wallet/escrow", body).status());
        Reply quiz = escrow(tv, "NDEM", 7, 2, "no-game");
        assertEquals(200, quiz.status());
        assertNull(jdbc.queryForObject("SELECT game FROM wallet_escrow WHERE eid = ?", String.class, eid(quiz)));
    }

    @Test
    void theChessSwitchStopsNewStakesAndASuspendedLicenseToo() throws Exception {
        Tv tv = richTv();
        setPolicy("game.chess.switch", 0);
        Reply r = chessEscrow(tv, "NDEM", 20, 1, "switch-off");
        assertEquals(409, r.status());
        assertEquals("STAKES_SUSPENDED", r.reason());
        setPolicy("game.chess.switch", 1);
        assertEquals(200, chessEscrow(tv, "NDEM", 20, 1, "switch-on").status());
        setPolicy("switch.stakes.NDEM", 0);
        assertEquals("STAKES_SUSPENDED", chessEscrow(tv, "NDEM", 20, 1, "switch-global").reason(), "l'interrupteur général des mises NDEM s'applique aussi");
        setPolicy("switch.stakes.NDEM", 1);
    }

    @Test
    void theScaleIsReadFromThePolicyAtEveryEscrow() throws Exception {
        Tv tv = richTv();
        setPolicy("game.chess.tier.NDEM.6", 30);
        assertEquals(200, chessEscrow(tv, "NDEM", 30, 1, "tier-new").status(), "un palier ajouté par l'exploitant est offert sans redéploiement");
        setPolicy("game.chess.tier.NDEM.1", 0);
        assertEquals("STAKE_NOT_OFFERED", chessEscrow(tv, "NDEM", 10, 1, "tier-gone").reason(), "un palier retiré n'est plus offert");
        setPolicy("game.chess.tier.NDEM.6", 0);
        setPolicy("game.chess.tier.NDEM.1", 10);
    }

    @Test
    void aReplayOfTheSameEscrowGivesTheSameCbe1AndAnotherGameIsAConflict() throws Exception {
        Tv tv = richTv();
        Reply first = chessEscrow(tv, "NDEM", 20, 1, "replay-1");
        Reply again = chessEscrow(tv, "NDEM", 20, 1, "replay-1");
        assertEquals(first.json().get("cbe1").asText(), again.json().get("cbe1").asText());
        assertTrue(again.json().get("replayed").asBoolean());
        // même clé, mais cette fois pour le Quiz : conflit, jamais un second cbe1 qui contredirait le blocage
        Reply other = escrow(tv, "NDEM", 20, 1, "replay-1");
        assertEquals(409, other.status());
        assertEquals("IDEM_CONFLICT", other.reason());
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_escrow WHERE holder = ?", tv.code()));
    }

    // ---- plafonds de parties gagnées ----

    private void wins(Tv tv, int n, Instant at, String tag) {
        for (int i = 0; i < n; i++) {
            jdbc.update("INSERT INTO wallet_game_log (rid, room, game, holder, opponent, cur, per, used, pay, fee, outcome, settled_at) VALUES (?, ?, 'chess', ?, NULL, 'NDEM', 20, 20, 40, 0, 'WIN', ?)",
                    tag + "-" + i, ROOM_A, tv.code(), Timestamp.from(at));
        }
    }

    @Test
    void theFourthStakeOfTheDayIsRefusedBeforeAnyMoneyMovesAndTheNextDayReopens() throws Exception {
        Tv tv = richTv();
        wins(tv, 2, T0, "d-ok");
        assertEquals(200, chessEscrow(tv, "NDEM", 10, 1, "cap-day-0").status(), "2 parties gagnées aujourd'hui sur 3 : permis");
        wins(tv, 1, T0, "d-3");
        long before = bal(tv, Currency.NDEM);
        Reply r = chessEscrow(tv, "NDEM", 10, 1, "cap-day-1");
        assertEquals(409, r.status());
        assertEquals("STAKE_WIN_CAP", r.reason());
        assertEquals("Limite atteinte : 3 parties gagnées aujourd'hui. Prochaine partie avec mise possible demain à 00:00.", r.message());
        assertEquals(before, bal(tv, Currency.NDEM), "refusé avant que rien ne soit bloqué");
        // une autre identité n'est pas concernée ; le Quiz non plus
        assertEquals(200, chessEscrow(richTv(), "NDEM", 10, 1, "cap-other").status());
        assertEquals(200, escrow(tv, "NDEM", 10, 1, "cap-quiz").status());
        // le lendemain 00:00 à Douala (= 23:00 UTC la veille) : la fenêtre du jour est neuve ; la semaine, elle, a changé aussi (lundi) : tout rouvre
        clock.freezeAt(Instant.parse("2026-10-04T23:00:00Z"));
        assertEquals(200, chessEscrow(tv, "NDEM", 10, 1, "cap-day-2").status());
    }

    @Test
    void theWeeklyAndMonthlyCapsAreCalendarWindowsInDoualaTimeAndTheMostConstrainingWins() throws Exception {
        Tv w = richTv();
        wins(w, 10, Instant.parse("2026-09-29T10:00:00Z"), "w");      // mardi de la semaine du lundi 28/09 : dans la semaine, pas dans le mois d'octobre ni dans le jour
        Reply week = chessEscrow(w, "NDEM", 10, 1, "cap-week");
        assertEquals("STAKE_WIN_CAP", week.reason());
        assertEquals("Limite atteinte : 10 parties gagnées cette semaine. Prochaine partie avec mise possible lundi 05/10 à 00:00.", week.message());
        Tv m = richTv();
        wins(m, 15, Instant.parse("2026-10-01T10:00:00Z"), "m");      // jeudi : dans la semaine ET dans le mois : le mois rouvre plus tard
        Reply month = chessEscrow(m, "NDEM", 10, 1, "cap-month");
        assertEquals("Limite atteinte : 15 parties gagnées ce mois-ci. Prochaine partie avec mise possible le 01/11 à 00:00.", month.message());
        setPolicy("game.chess.cap.win.day", 0); setPolicy("game.chess.cap.win.week", 0);
        try {
            // le mois seul : 30/09 à 23:30 UTC = 00:30 le 1er octobre à Douala compte dans OCTOBRE (la fenêtre est celle de l'heure de Douala, pas d'UTC) ; 22:30 UTC = 23:30 le 30/09 n'y compte pas
            Tv edge = richTv();
            wins(edge, 15, Instant.parse("2026-09-30T23:30:00Z"), "e");
            assertEquals("STAKE_WIN_CAP", chessEscrow(edge, "NDEM", 10, 1, "cap-edge").reason());
            Tv before = richTv();
            wins(before, 15, Instant.parse("2026-09-30T22:30:00Z"), "b");
            assertEquals(200, chessEscrow(before, "NDEM", 10, 1, "cap-before").status(), "des victoires de septembre ne comptent pas dans le mois d'octobre");
        } finally {
            setPolicy("game.chess.cap.win.day", 3); setPolicy("game.chess.cap.win.week", 10);
        }
        // plafond 0 = sans plafond
        setPolicy("game.chess.cap.win.day", 0); setPolicy("game.chess.cap.win.week", 0); setPolicy("game.chess.cap.win.month", 0);
        try {
            assertEquals(200, chessEscrow(w, "NDEM", 10, 1, "cap-none").status());
        } finally {
            setPolicy("game.chess.cap.win.day", 3); setPolicy("game.chess.cap.win.week", 10); setPolicy("game.chess.cap.win.month", 15);
        }
    }

    // ---- le règlement ----

    @Test
    void aDecisiveGameGivesTheWinnerBothStakesAndWritesTheJournalInTheSameTransaction() throws Exception {
        Duel d = duel("NDEM", 20, "settle-1");
        long a0 = bal(d.a(), Currency.NDEM), b0 = bal(d.b(), Currency.NDEM);
        assertEquals(20, locked(d.a(), Currency.NDEM));
        String rid = rid(101);
        Reply r = settle(chessResult(rid, ROOM_A, "NDEM", 20, "END", lines(d, 20, 0, 40)));
        assertEquals(200, r.status(), r.json().toString());
        assertEquals("chess", r.json().get("game").asText());
        assertEquals(0, r.json().get("fee").asLong());
        assertEquals(a0, bal(d.a(), Currency.NDEM), "le perdant a perdu sa mise (déjà bloquée)");
        assertEquals(b0 + 40, bal(d.b(), Currency.NDEM), "le gagnant reçoit les deux mises");
        assertEquals(0, locked(d.a(), Currency.NDEM) + locked(d.b(), Currency.NDEM));
        // le journal : une ligne par TV, avec l'issue et l'adversaire
        assertEquals(2, journalRows(rid));
        assertEquals("WIN", jdbc.queryForObject("SELECT outcome FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid, d.b().code()));
        assertEquals("LOSS", jdbc.queryForObject("SELECT outcome FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid, d.a().code()));
        assertEquals(d.a().code(), jdbc.queryForObject("SELECT opponent FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid, d.b().code()));
        assertEquals(ROOM_A, jdbc.queryForObject("SELECT room FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid, d.b().code()));
        // idempotence par résultat (donc par salle : l'identifiant est dérivé de la salle) : cinq rejeux ne changent rien
        for (int i = 0; i < 5; i++) assertEquals(r.json(), settle(chessResult(rid, ROOM_A, "NDEM", 20, "END", lines(d, 20, 0, 40))).json());
        assertEquals(b0 + 40, bal(d.b(), Currency.NDEM));
        assertEquals(2, journalRows(rid));
        assertEquals(1, newTxns("SETTLE"));
        assertReconciled();
    }

    @Test
    void aDrawRefundsEachStakeAndAnAbortReleasesBothEscrows() throws Exception {
        Duel d = duel("MBOKO", 5, "settle-2");
        long a0 = bal(d.a(), Currency.MBOKO) + 5, b0 = bal(d.b(), Currency.MBOKO) + 5;    // avant le blocage
        Reply draw = settle(chessResult(rid(102), ROOM_A, "MBOKO", 5, "END", lines(d, 5, 5, 5)));
        assertEquals(200, draw.status(), draw.json().toString());
        assertEquals(a0, bal(d.a(), Currency.MBOKO)); assertEquals(b0, bal(d.b(), Currency.MBOKO));
        assertEquals("DRAW", jdbc.queryForObject("SELECT outcome FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid(102), d.a().code()));
        // interruption : une salle fermée au milieu d'une partie (nouveaux blocages)
        Duel e = duel("NDEM", 50, "settle-3");
        long ea = bal(e.a(), Currency.NDEM) + 50, eb = bal(e.b(), Currency.NDEM) + 50;
        Reply abort = settle(chessResult(rid(103), ROOM_A, "NDEM", 50, "ABORT", lines(e, 50, 0, 0).stream().map(l -> new Line(l.eid(), l.id(), 0, 0)).toList()));
        assertEquals(200, abort.status(), abort.json().toString());
        assertEquals(ea, bal(e.a(), Currency.NDEM)); assertEquals(eb, bal(e.b(), Currency.NDEM));
        assertEquals(0, locked(e.a(), Currency.NDEM));
        assertEquals("ABORT", jdbc.queryForObject("SELECT outcome FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid(103), e.b().code()));
        // une seule TV (l'adversaire n'est jamais venu) : son blocage est rendu
        Tv solo = richTv();
        Reply s = chessEscrow(solo, "NDEM", 20, 1, "settle-solo");
        long s0 = bal(solo, Currency.NDEM) + 20;
        assertEquals(200, settle(chessResult(rid(104), ROOM_A, "NDEM", 20, "ABORT", List.of(new Line(eid(s), solo.code(), 0, 0)))).status());
        assertEquals(s0, bal(solo, Currency.NDEM));
        assertNull(jdbc.queryForObject("SELECT opponent FROM wallet_game_log WHERE rid = ?", String.class, rid(104)));
        assertReconciled();
    }

    @Test
    void anImpossibleDuelShapeIsRefusedWhateverTheSignatureSays() throws Exception {
        Duel d = duel("NDEM", 20, "shape");
        // 30 / 10 : la somme est juste, la répartition n'existe pas aux échecs
        assertEquals("BAD_TXN", settle(chessResult(rid(110), ROOM_A, "NDEM", 20, "END", lines(d, 20, 30, 10))).reason());
        // un gagnant qui n'a pas engagé sa mise entière
        assertEquals("BAD_TXN", settle(chessResult(rid(111), ROOM_A, "NDEM", 20, "END", List.of(new Line(d.eidA(), d.a().code(), 10, 20), new Line(d.eidB(), d.b().code(), 10, 0)))).reason());
        // une partie terminée avec une seule TV
        assertEquals("BAD_TXN", settle(chessResult(rid(112), ROOM_A, "NDEM", 20, "END", List.of(new Line(d.eidA(), d.a().code(), 20, 20)))).reason());
        // la même TV des deux côtés (deux blocages d'une même identité)
        Reply second = chessEscrow(d.a(), "NDEM", 20, 1, "shape-second");
        assertEquals("BAD_TXN", settle(chessResult(rid(113), ROOM_A, "NDEM", 20, "END", List.of(new Line(d.eidA(), d.a().code(), 20, 40), new Line(eid(second), d.a().code(), 20, 0)))).reason());
        // un blocage de Quiz (sans jeu) ne se règle pas par un résultat d'échecs, et inversement
        Tv q = trialTv(), q2 = trialTv();
        String qa = escrow(q, "NDEM", 20, 1, "shape-quiz-a").json().get("eid").asText(), qb = escrow(q2, "NDEM", 20, 1, "shape-quiz-b").json().get("eid").asText();
        assertEquals("BAD_TXN", settle(chessResult(rid(114), ROOM_A, "NDEM", 20, "END", List.of(new Line(qa, q.code(), 20, 40), new Line(qb, q2.code(), 20, 0)))).reason());
        assertEquals("BAD_TXN", settle(cbr1(RESULT, rid(115), "NDEM", 20, "END", List.of(new Line(d.eidA(), d.a().code(), 20, 40), new Line(d.eidB(), d.b().code(), 20, 0)))).reason(), "résultat de Quiz sur des blocages d'échecs");
        // rien n'a bougé, tous les blocages sont encore ouverts
        assertEquals(40, locked(d.a(), Currency.NDEM), "les deux blocages de A (le premier et le second) restent ouverts");
        assertEquals(20, locked(d.b(), Currency.NDEM));
        assertEquals(0, newTxns("SETTLE"));
        assertEquals(0, journalRows(rid(110)) + journalRows(rid(111)) + journalRows(rid(112)) + journalRows(rid(113)));
        // la partie d'origine se règle ensuite normalement
        assertEquals(200, settle(chessResult(rid(116), ROOM_A, "NDEM", 20, "END", lines(d, 20, 40, 0))).status());
        assertEquals(20, locked(d.a(), Currency.NDEM), "il ne reste que le second blocage de A (jamais employé : rendu à son échéance)");
    }

    @Test
    void theJournalAndTheCapsCountOnlyDecisiveWins() throws Exception {
        Duel d = duel("NDEM", 20, "count");
        assertEquals(200, settle(chessResult(rid(120), ROOM_A, "NDEM", 20, "END", lines(d, 20, 40, 0))).status());
        Instant day = T0;
        assertEquals(1, ledgerWins(d.a(), day));
        assertEquals(0, ledgerWins(d.b(), day));
        Duel e = duel("NDEM", 20, "count-2");
        assertEquals(200, settle(chessResult(rid(121), ROOM_A, "NDEM", 20, "END", lines(e, 20, 20, 20))).status());
        assertEquals(0, ledgerWins(e.a(), day), "une nulle n'est pas une partie gagnée");
    }

    private long ledgerWins(Tv tv, Instant since) { return count("SELECT COUNT(*) FROM wallet_game_log WHERE holder = ? AND game = 'chess' AND outcome = 'WIN' AND settled_at >= ?", tv.code(), Timestamp.from(since.minusSeconds(86_400))); }

    // ---- les frais de la politique ----

    @Test
    void platformFeesAreTakenFromTheWinnersPotOnlyAndOnlyWhenThePolicySaysSo() throws Exception {
        setPolicy("game.chess.feeBp", 500);   // 5 %
        try {
            Duel d = duel("NDEM", 100, "fee-1");
            long b0 = bal(d.b(), Currency.NDEM), feeBefore = sys("SYS:FEE", "NDEM");
            Reply r = settle(chessResult(rid(130), ROOM_A, "NDEM", 100, "END", lines(d, 100, 0, 200)));
            assertEquals(200, r.status(), r.json().toString());
            assertEquals(10, r.json().get("fee").asLong(), "5 % de la cagnotte de 200");
            assertEquals(b0 + 190, bal(d.b(), Currency.NDEM), "le gagnant reçoit les deux mises moins les frais");
            assertEquals(feeBefore + 10, sys("SYS:FEE", "NDEM"));
            assertEquals(10, jdbc.queryForObject("SELECT fee FROM wallet_game_log WHERE rid = ? AND holder = ?", Long.class, rid(130), d.b().code()));
            assertEquals(0, jdbc.queryForObject("SELECT fee FROM wallet_game_log WHERE rid = ? AND holder = ?", Long.class, rid(130), d.a().code()));
            assertEquals(0, sys("SYS:POT", "NDEM"), "la cagnotte revient à 0");
            assertEquals(10, r.json().get("lines").get(1).get("fee").asLong());
            // le rejeu rend la même réponse, frais compris, même si la politique a changé entre-temps
            setPolicy("game.chess.feeBp", 0);
            assertEquals(r.json(), settle(chessResult(rid(130), ROOM_A, "NDEM", 100, "END", lines(d, 100, 0, 200))).json());
            assertEquals(feeBefore + 10, sys("SYS:FEE", "NDEM"));
            // pas de frais sur une nulle ni sur une interruption, même quand la politique en prévoit
            setPolicy("game.chess.feeBp", 2000);
            Duel e = duel("NDEM", 100, "fee-2");
            long fee0 = sys("SYS:FEE", "NDEM");
            assertEquals(0, settle(chessResult(rid(131), ROOM_A, "NDEM", 100, "END", lines(e, 100, 100, 100))).json().get("fee").asLong());
            Duel f = duel("NDEM", 100, "fee-3");
            assertEquals(0, settle(chessResult(rid(132), ROOM_A, "NDEM", 100, "ABORT", List.of(new Line(f.eidA(), f.a().code(), 0, 0), new Line(f.eidB(), f.b().code(), 0, 0)))).json().get("fee").asLong());
            assertEquals(fee0, sys("SYS:FEE", "NDEM"));
            assertReconciled();
        } finally {
            setPolicy("game.chess.feeBp", 0);
        }
    }

    @Test
    void mbokoFeesAreAllowedAndTheReconciliationStaysGreen() throws Exception {
        setPolicy("game.chess.feeBp", 1000);   // 10 % de 10 MBOKO = 1
        try {
            Duel d = duel("MBOKO", 5, "fee-m");
            Reply r = settle(chessResult(rid(140), ROOM_A, "MBOKO", 5, "END", lines(d, 5, 10, 0)));
            assertEquals(200, r.status(), r.json().toString());
            assertEquals(1, r.json().get("fee").asLong());
            assertEquals(1, sys("SYS:FEE", "MBOKO"));
            assertReconciled();
        } finally {
            setPolicy("game.chess.feeBp", 0);
        }
    }

    @Test
    void withNoFeeNothingIsEverCreditedToTheFeeAccount() throws Exception {
        long n0 = sys("SYS:FEE", "NDEM"), m0 = sys("SYS:FEE", "MBOKO");
        Duel d = duel("NDEM", 20, "nofee");
        assertEquals(200, settle(chessResult(rid(150), ROOM_A, "NDEM", 20, "END", lines(d, 20, 40, 0))).status());
        assertEquals(n0, sys("SYS:FEE", "NDEM")); assertEquals(m0, sys("SYS:FEE", "MBOKO"));
    }

    // ---- ce que la TV et l'exploitant lisent ----

    @Test
    void thePolicyRouteTellsTheScaleTheFeeAndTheCaps() throws Exception {
        Tv tv = trialTv();
        JsonNode p = body(mvc.perform(get("/api/v1/wallet/policy").header("Authorization", tv.auth())).andExpect(status().isOk()).andReturn());
        JsonNode chess = p.get("games").get("chess");
        assertTrue(chess.get("enabled").asBoolean());
        assertEquals(List.of(10L, 20L, 50L, 100L, 200L), longs(chess.get("stakes").get("NDEM")));
        assertEquals(List.of(1L, 2L, 5L, 10L), longs(chess.get("stakes").get("MBOKO")));
        assertEquals(0, chess.get("feeBp").asInt());
        assertEquals(3, chess.get("winCaps").get("day").asInt()); assertEquals(10, chess.get("winCaps").get("week").asInt()); assertEquals(15, chess.get("winCaps").get("month").asInt());
        assertEquals(1, chess.get("seats").asInt());
        assertFalse(chess.get("trialStakes").asBoolean());
    }

    private static List<Long> longs(JsonNode a) { java.util.ArrayList<Long> l = new java.util.ArrayList<>(); a.forEach(n -> l.add(n.asLong())); return l; }

    @Test
    void theAdminGamesRouteListsTheJournalAndNeedsTheAdminToken() throws Exception {
        Duel d = duel("NDEM", 20, "admin-1");
        assertEquals(200, settle(chessResult(rid(160), ROOM_A, "NDEM", 20, "END", lines(d, 20, 40, 0))).status());
        mvc.perform(get("/api/v1/admin/wallet/games")).andExpect(status().isUnauthorized());
        JsonNode all = body(mvc.perform(get("/api/v1/admin/wallet/games").param("game", "chess").param("holder", d.a().code()).header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn());
        JsonNode row = all.get("games").get(0);
        assertEquals(rid(160), row.get("rid").asText());
        assertEquals("WIN", row.get("outcome").asText());
        assertEquals(d.b().code(), row.get("opponent").asText());
        assertEquals(40, row.get("pay").asLong());
        mvc.perform(get("/api/v1/admin/wallet/games").param("holder", "pas-une-identite").header("Authorization", ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/wallet/games").param("game", "Chess!").header("Authorization", ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void theMigrationLeavesTheTablesAndTheDefaultPolicyInPlace() {
        assertEquals(17, count("SELECT COUNT(*) FROM wallet_policy WHERE name LIKE 'game.chess.%'"), "interrupteur, 12 paliers, frais, 3 plafonds");
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_game_log WHERE rid = 'jamais'"));
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE UPPER(TABLE_NAME) = 'WALLET_ESCROW' AND UPPER(COLUMN_NAME) = 'GAME'"));
    }
}
