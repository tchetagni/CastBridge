package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.wallet.WalletPolicyService;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Settlement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Le Quiz en ligne avec mise côté API (games-G5, suite de games-G2) : {@code quiz} est un jeu misé connu du portefeuille. Le blocage {@code cbe1} d'une table suit l'échelle du jeu, de 1 à 8 sièges par TV,
 * l'essai n'y mise pas, les plafonds de parties gagnées se comptent PAR JEU ; le résultat {@code cbr1} d'une table règle une ligne par TV (cagnotte par siège agrégée par TV, non-utilisé rendu), écrit le journal
 * dans la même transaction, applique les frais de la politique (0 au lancement) sur la plus grosse part, et ne règle jamais une forme impossible ni les blocages d'un autre jeu. Réutilise le matériel de
 * {@link OpsTestBase} (TV réelles, licences, clé de résultat de test) ; mêmes conventions que {@link ChessStakeApiTest}.
 */
class QuizStakeApiTest extends OpsTestBase {
    private static final String ROOM_Q = "0123456789abcdef0123456789abcdef";

    @Autowired GameJournal journal;

    // ---- matériel ----

    private Reply gameEscrow(Tv tv, String game, String cur, long per, int k, String idem) throws Exception {
        ObjectNode body = req(tv).put("cur", cur).put("per", per).put("k", k).put("idem", idem).put("game", game);
        ArrayNode acts = body.putArray("activations");
        tv.activations().forEach(acts::add);
        return postJson(tv.auth(), "/api/v1/wallet/escrow", body);
    }

    private Reply quizEscrow(Tv tv, String cur, long per, int k, String idem) throws Exception { return gameEscrow(tv, "quiz", cur, per, k, idem); }

    private Reply chessEscrow(Tv tv, String cur, long per, int k, String idem) throws Exception { return gameEscrow(tv, "chess", cur, per, k, idem); }

    /** Une TV de production avec assez de NDEM et de MBOKO pour toute l'échelle, y compris huit sièges de la plus forte mise. */
    private Tv richTv() throws Exception {
        Tv tv = productionTv();
        adminGrant(tv.code(), "NDEM", 20_000);
        adminGrant(tv.code(), "MBOKO", 500);
        return tv;
    }

    private String eid(Reply r) { return r.json().get("eid").asText(); }

    /** Un {@code cbr1} d'un jeu misé : même format que le service de jeu (jeu « quiz » ou « chess », salle de 32 caractères au plus, rid de 32 hexadécimaux), signé par la clé de résultat de test. */
    private static String gameResult(String game, String rid, String cur, long per, String kind, List<Line> lines) {
        StringBuilder sb = new StringBuilder("{\"kid\":\"").append(castbridge.server.licenses.LicenseKeyring.kidOf(rawPublicBytes(RESULT))).append("\",\"rid\":\"").append(rid).append("\",\"room\":\"").append(ROOM_Q)
                .append("\",\"game\":\"").append(game).append("\",\"cur\":\"").append(cur).append("\",\"per\":").append(per).append(",\"kind\":\"").append(kind).append("\",\"at\":").append(NOW).append(",\"lines\":[");
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            sb.append(i == 0 ? "" : ",").append("[\"").append(l.eid()).append("\",\"").append(l.id()).append("\",").append(l.used()).append(',').append(l.pay()).append(']');
        }
        return sign(RESULT, sb.append("]}").toString());
    }

    private static String quizResult(String rid, String cur, long per, String kind, List<Line> lines) { return gameResult("quiz", rid, cur, per, kind, lines); }

    /** Une TV à la table : son blocage de {@code k} sièges. */
    private record Place(Tv tv, int k, String eid) {}

    /** Une table : une TV de production par entrée de {@code ks}, qui bloque {@code per × k} avec {@code game:"quiz"}. */
    private List<Place> table(String cur, long per, String idem, int... ks) throws Exception {
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < ks.length; i++) {
            Tv tv = richTv();
            Reply r = quizEscrow(tv, cur, per, ks[i], idem + "-" + i);
            assertEquals(200, r.status(), r.json().toString());
            out.add(new Place(tv, ks[i], eid(r)));
        }
        return out;
    }

    /** Les lignes d'un résultat telles que le service les calcule : cagnotte par siège ({@code Pot.split}) agrégée par TV ({@link Settlement#compute}). {@code scores[i]} = points des sièges PRÉSENTS de la TV i. */
    private static List<Line> served(String cur, long per, List<Place> table, int[][] scores) {
        List<Settlement.Escrow> escrows = new ArrayList<>();
        List<Settlement.Seat> seats = new ArrayList<>();
        for (int i = 0; i < table.size(); i++) {
            Place p = table.get(i);
            escrows.add(new Settlement.Escrow(p.eid(), p.tv().code(), p.k(), per * p.k()));
            for (int n = 0; n < scores[i].length; n++) seats.add(new Settlement.Seat(p.eid(), n, scores[i][n]));
        }
        return Settlement.compute(Currency.valueOf(cur), per, escrows, seats, Settlement.Kind.END).stream().map(l -> new Line(l.eid(), l.id(), l.used(), l.pay())).toList();
    }

    /** Un {@code ABORT} : rien n'est utilisé, rien n'est payé. */
    private static List<Line> nothing(List<Place> table) { return table.stream().map(p -> new Line(p.eid(), p.tv().code(), 0, 0)).toList(); }

    private long journalRows(String rid) { return count("SELECT COUNT(*) FROM wallet_game_log WHERE rid = ?", rid); }

    private String outcome(String rid, Tv tv) { return jdbc.queryForObject("SELECT outcome FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid, tv.code()); }

    private long journalFee(String rid, Tv tv) { return jdbc.queryForObject("SELECT fee FROM wallet_game_log WHERE rid = ? AND holder = ?", Long.class, rid, tv.code()); }

    private static void assertBad(Reply r) {
        assertEquals(400, r.status(), r.json().toString());
        assertEquals("BAD_TXN", r.reason(), r.json().toString());
    }

    private static void assertOtherGame(Reply r) {
        assertBad(r);
        assertTrue(r.message().startsWith("Jeu différent de celui du blocage"), r.message());
    }

    // ---- le blocage ----

    @Test
    void theEscrowOfAQuizTableFollowsTheScaleOfTheGameAndRecordsIt() throws Exception {
        Tv tv = richTv();
        for (long per : new long[] {10, 20, 50, 100, 200}) {
            Reply r = quizEscrow(tv, "NDEM", per, 1, "scale-ndem-" + per);
            assertEquals(200, r.status(), per + " NDEM : " + r.json());
            assertEquals("quiz", jdbc.queryForObject("SELECT game FROM wallet_escrow WHERE eid = ?", String.class, eid(r)), "le jeu est inscrit avec le blocage");
        }
        for (long per : new long[] {1, 2, 5, 10}) assertEquals(200, quizEscrow(tv, "MBOKO", per, 1, "scale-mboko-" + per).status(), per + " MBOKO");
        long before = bal(tv, Currency.NDEM);
        for (long per : new long[] {30, 1, 15, 201, 1000}) {
            Reply r = quizEscrow(tv, "NDEM", per, 1, "off-scale-" + per);
            assertEquals(409, r.status(), per + " NDEM n'est pas dans l'échelle");
            assertEquals("STAKE_NOT_OFFERED", r.reason());
            assertEquals("Cette mise n'est pas proposée au Quiz : 10, 20, 50, 100, 200 NDEM par joueur", r.message());
        }
        Reply m3 = quizEscrow(tv, "MBOKO", 3, 1, "off-scale-m3");
        assertEquals(409, m3.status());
        assertEquals("Cette mise n'est pas proposée au Quiz : 1, 2, 5, 10 MBOKO par joueur", m3.message());
        // aux échecs le texte ne change pas ; les deux commencent comme la TV le teste
        Reply chess = chessEscrow(tv, "NDEM", 30, 1, "off-scale-chess");
        assertEquals("Cette mise n'est pas proposée aux échecs : 10, 20, 50, 100, 200 NDEM par joueur", chess.message());
        assertTrue(chess.message().startsWith("Cette mise n'est pas proposée") && m3.message().startsWith("Cette mise n'est pas proposée"));
        assertEquals(before, bal(tv, Currency.NDEM), "un refus ne bloque rien");
        assertReconciled();
    }

    @Test
    void aQuizTvBlocksOneToEightSeatsWhileChessKeepsOneSeatPerTv() throws Exception {
        Tv tv = richTv();
        for (int k : new int[] {1, 3, 8}) {
            Reply r = quizEscrow(tv, "NDEM", 20, k, "seats-" + k);
            assertEquals(200, r.status(), k + " sièges : " + r.json());
            JsonNode c = verifyCbe1(json, r.json().get("cbe1").asText());
            assertEquals(k, c.get("k").asInt());
            assertEquals(20, c.get("per").asLong());
            assertEquals(20L * k, c.get("amt").asLong(), "le blocage vaut mise × sièges");
        }
        assertEquals(20L * (1 + 3 + 8), locked(tv, Currency.NDEM));
        long before = bal(tv, Currency.NDEM);
        for (int k : new int[] {0, 9, -1}) assertBad(quizEscrow(tv, "NDEM", 20, k, "seats-bad-" + k));
        assertEquals(before, bal(tv, Currency.NDEM), "un refus ne bloque rien");
        assertEquals(3, count("SELECT COUNT(*) FROM wallet_escrow WHERE holder = ?", tv.code()));
        // aux échecs : toujours une seule mise par TV, avec le même texte qu'avant
        Reply two = chessEscrow(tv, "NDEM", 20, 2, "seats-chess-2");
        assertBad(two);
        assertEquals("Aux échecs, une seule mise par TV (un siège)", two.message());
        assertEquals(200, chessEscrow(tv, "NDEM", 20, 1, "seats-chess-1").status());
        assertEquals(4, count("SELECT COUNT(*) FROM wallet_escrow WHERE holder = ?", tv.code()));
    }

    @Test
    void aTrialTvDoesNotStakeAtTheQuizInAnyCurrencyButStillBlocksWithoutGame() throws Exception {
        Tv tv = trialTv();
        long before = bal(tv, Currency.NDEM);
        Reply ndem = quizEscrow(tv, "NDEM", 10, 1, "trial-quiz");
        assertEquals(409, ndem.status());
        assertEquals("TRIAL_FREE_ONLY", ndem.reason());
        assertEquals("Version d'essai : parties libres seulement, sans mise", ndem.message());
        // en MBOKO la règle commune des mises passe avant celle du jeu (comme aux échecs) : une TV d'essai ne mise jamais de MBOKO
        Reply mboko = quizEscrow(tv, "MBOKO", 1, 1, "trial-quiz-m");
        assertEquals(409, mboko.status());
        assertEquals("TRIAL_NO_MBOKO", mboko.reason());
        assertEquals(before, bal(tv, Currency.NDEM));
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_escrow WHERE holder = ?", tv.code()));
        // sans jeu : les usages d'avant ne changent pas (une TV d'essai bloque en NDEM)
        assertEquals(200, escrow(tv, "NDEM", 10, 1, "trial-legacy").status());
    }

    @Test
    void anUnknownGameIsRefusedNamingBothGamesAndNoGameStillMeansNoGameRules() throws Exception {
        Tv tv = richTv();
        ObjectNode body = req(tv).put("cur", "NDEM").put("per", 20).put("k", 1).put("idem", "game-unknown").put("game", "poker");
        tv.activations().forEach(body.putArray("activations")::add);
        Reply r = postJson(tv.auth(), "/api/v1/wallet/escrow", body);
        assertEquals(400, r.status());
        assertTrue(r.message().contains("(chess)") && r.message().contains("(quiz)"), r.message());
        body.put("game", "Quiz!").put("idem", "game-bad");
        assertEquals(400, postJson(tv.auth(), "/api/v1/wallet/escrow", body).status());
        // sans `game` : ni échelle, ni plafond, ni limite de sièges propres à un jeu (une mise hors échelle sur plusieurs sièges passe)
        Reply legacy = escrow(tv, "NDEM", 7, 3, "no-game");
        assertEquals(200, legacy.status());
        assertNull(jdbc.queryForObject("SELECT game FROM wallet_escrow WHERE eid = ?", String.class, eid(legacy)));
    }

    @Test
    void theQuizSwitchStopsNewQuizStakesOnlyAndTheChessSwitchDoesNotCloseTheQuiz() throws Exception {
        Tv tv = richTv();
        setPolicy("game.quiz.switch", 0);
        try {
            Reply r = quizEscrow(tv, "NDEM", 20, 2, "switch-off");
            assertEquals(409, r.status());
            assertEquals("STAKES_SUSPENDED", r.reason());
            assertEquals(200, chessEscrow(tv, "NDEM", 20, 1, "switch-off-chess").status(), "les échecs ont leur propre interrupteur");
            assertEquals(200, escrow(tv, "NDEM", 20, 1, "switch-off-legacy").status(), "sans jeu : pas d'interrupteur de jeu");
        } finally {
            setPolicy("game.quiz.switch", 1);
        }
        assertEquals(200, quizEscrow(tv, "NDEM", 20, 2, "switch-on").status());
        setPolicy("game.chess.switch", 0);
        try {
            assertEquals(200, quizEscrow(tv, "NDEM", 20, 2, "switch-chess-off").status(), "l'interrupteur des échecs ne ferme pas le Quiz");
        } finally {
            setPolicy("game.chess.switch", 1);
        }
        setPolicy("switch.stakes.NDEM", 0);
        try {
            assertEquals("STAKES_SUSPENDED", quizEscrow(tv, "NDEM", 20, 2, "switch-global").reason(), "l'interrupteur général des mises NDEM s'applique aussi");
        } finally {
            setPolicy("switch.stakes.NDEM", 1);
        }
    }

    @Test
    void theQuizScaleIsReadFromItsOwnPolicyLinesAtEveryEscrow() throws Exception {
        Tv tv = richTv();
        setPolicy("game.quiz.tier.NDEM.6", 30);
        try {
            assertEquals(200, quizEscrow(tv, "NDEM", 30, 1, "tier-new").status(), "un palier ajouté par l'exploitant est offert sans redéploiement");
            assertEquals("STAKE_NOT_OFFERED", chessEscrow(tv, "NDEM", 30, 1, "tier-new-chess").reason(), "…au Quiz seulement : l'échelle des échecs est la sienne");
        } finally {
            setPolicy("game.quiz.tier.NDEM.6", 0);
        }
        setPolicy("game.quiz.tier.NDEM.1", 0);
        try {
            assertEquals("STAKE_NOT_OFFERED", quizEscrow(tv, "NDEM", 10, 1, "tier-gone").reason(), "un palier retiré n'est plus offert");
            assertEquals(200, chessEscrow(tv, "NDEM", 10, 1, "tier-gone-chess").status());
        } finally {
            setPolicy("game.quiz.tier.NDEM.1", 10);
        }
    }

    @Test
    void aReplayOfTheSameEscrowGivesTheSameCbe1AndAnotherGameOrSeatCountIsAConflict() throws Exception {
        Tv tv = richTv();
        Reply first = quizEscrow(tv, "NDEM", 20, 3, "replay-1");
        Reply again = quizEscrow(tv, "NDEM", 20, 3, "replay-1");
        assertEquals(200, again.status());
        assertEquals(first.json().get("cbe1").asText(), again.json().get("cbe1").asText(), "le rejeu rend le même cbe1");
        assertEquals(eid(first), eid(again));
        assertTrue(again.json().get("replayed").asBoolean());
        assertEquals(60, locked(tv, Currency.NDEM), "bloqué une seule fois");
        assertEquals(1, newTxns("ESCROW_LOCK"));
        // même clé pour un autre jeu, un autre nombre de sièges, ou sans jeu : conflit, jamais un second cbe1 qui contredirait le blocage
        Reply chess = chessEscrow(tv, "NDEM", 20, 1, "replay-1");
        assertEquals(409, chess.status());
        assertEquals("IDEM_CONFLICT", chess.reason());
        assertEquals("IDEM_CONFLICT", quizEscrow(tv, "NDEM", 20, 2, "replay-1").reason());
        assertEquals("IDEM_CONFLICT", escrow(tv, "NDEM", 20, 3, "replay-1").reason());
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_escrow WHERE holder = ?", tv.code()));
    }

    // ---- plafonds de parties gagnées, comptés par jeu ----

    private void wins(Tv tv, String game, int n, Instant at, String tag) {
        for (int i = 0; i < n; i++) {
            jdbc.update("INSERT INTO wallet_game_log (rid, room, game, holder, opponent, cur, per, used, pay, fee, outcome, settled_at) VALUES (?, ?, ?, ?, NULL, 'NDEM', 20, 20, 40, 0, 'WIN', ?)",
                    tag + "-" + i, ROOM_Q, game, tv.code(), Timestamp.from(at));
        }
    }

    @Test
    void theCapOfWonGamesIsCountedPerGameSoThreeQuizWinsCloseTheQuizButNotChessAndConversely() throws Exception {
        Tv q = richTv(), c = richTv();
        wins(q, "quiz", 2, T0, "q-ok");
        assertEquals(200, quizEscrow(q, "NDEM", 10, 1, "cap-q-0").status(), "2 parties gagnées au Quiz aujourd'hui sur 3 : permis");
        wins(q, "quiz", 1, T0, "q-3");
        long before = bal(q, Currency.NDEM);
        Reply r = quizEscrow(q, "NDEM", 10, 1, "cap-q-1");
        assertEquals(409, r.status());
        assertEquals("STAKE_WIN_CAP", r.reason());
        assertEquals("Limite atteinte : 3 parties gagnées aujourd'hui. Prochaine partie avec mise possible demain à 00:00.", r.message());
        assertEquals(before, bal(q, Currency.NDEM), "refusé avant que rien ne soit bloqué");
        assertEquals(200, chessEscrow(q, "NDEM", 10, 1, "cap-q-chess").status(), "les victoires du Quiz ne comptent pas aux échecs");
        // et inversement
        wins(c, "chess", 3, T0, "c-3");
        assertEquals("STAKE_WIN_CAP", chessEscrow(c, "NDEM", 10, 1, "cap-c-0").reason());
        assertEquals(200, quizEscrow(c, "NDEM", 10, 1, "cap-c-quiz").status(), "les victoires aux échecs ne comptent pas au Quiz");
        // une autre identité n'est pas concernée ; un blocage sans jeu non plus
        assertEquals(200, quizEscrow(richTv(), "NDEM", 10, 1, "cap-other").status());
        assertEquals(200, escrow(q, "NDEM", 10, 1, "cap-legacy").status());
        // le lendemain 00:00 à Douala (= 23:00 UTC la veille) : la fenêtre du jour est neuve (et la semaine a changé : lundi)
        clock.freezeAt(Instant.parse("2026-10-04T23:00:00Z"));
        assertEquals(200, quizEscrow(q, "NDEM", 10, 1, "cap-q-2").status());
    }

    @Test
    void theWeeklyAndMonthlyCapsAndTheirPolicyLinesAreThoseOfTheQuizOnly() throws Exception {
        Tv w = richTv();
        wins(w, "quiz", 10, Instant.parse("2026-09-29T10:00:00Z"), "w");      // mardi de la semaine du lundi 28/09 : dans la semaine, pas dans le mois d'octobre ni dans le jour
        Reply week = quizEscrow(w, "NDEM", 10, 1, "cap-week");
        assertEquals("STAKE_WIN_CAP", week.reason());
        assertEquals("Limite atteinte : 10 parties gagnées cette semaine. Prochaine partie avec mise possible lundi 05/10 à 00:00.", week.message());
        assertEquals(200, chessEscrow(w, "NDEM", 10, 1, "cap-week-chess").status());
        Tv m = richTv();
        wins(m, "quiz", 15, Instant.parse("2026-10-01T10:00:00Z"), "m");      // jeudi : dans la semaine ET dans le mois : le mois rouvre plus tard
        assertEquals("Limite atteinte : 15 parties gagnées ce mois-ci. Prochaine partie avec mise possible le 01/11 à 00:00.", quizEscrow(m, "NDEM", 10, 1, "cap-month").message());
        // un plafond journalier de 1 au Quiz ne touche pas les échecs (lignes de politique distinctes)
        Tv d = richTv();
        wins(d, "quiz", 1, T0, "d");
        setPolicy("game.quiz.cap.win.day", 1);
        try {
            assertEquals("Limite atteinte : 1 partie gagnée aujourd'hui. Prochaine partie avec mise possible demain à 00:00.", quizEscrow(d, "NDEM", 10, 1, "cap-day-1").message());
            assertEquals(200, chessEscrow(d, "NDEM", 10, 1, "cap-day-1-chess").status());
        } finally {
            setPolicy("game.quiz.cap.win.day", 3);
        }
        // plafond 0 = sans plafond
        for (String cap : List.of("day", "week", "month")) setPolicy("game.quiz.cap.win." + cap, 0);
        try {
            assertEquals(200, quizEscrow(w, "NDEM", 10, 1, "cap-none-w").status());
            assertEquals(200, quizEscrow(m, "NDEM", 10, 1, "cap-none-m").status());
        } finally {
            setPolicy("game.quiz.cap.win.day", 3); setPolicy("game.quiz.cap.win.week", 10); setPolicy("game.quiz.cap.win.month", 15);
        }
    }

    @Test
    void threeRealWinsCloseTheQuizForTheWinnerOnly() throws Exception {
        Tv w = richTv();
        Tv lastLoser = null;
        for (int i = 0; i < 3; i++) {
            Tv p = richTv();
            lastLoser = p;
            Reply ew = quizEscrow(w, "NDEM", 20, 1, "real-w-" + i), ep = quizEscrow(p, "NDEM", 20, 1, "real-p-" + i);
            assertEquals(200, ew.status(), "sous le plafond : " + ew.json());
            Reply s = settle(quizResult(rid(300 + i), "NDEM", 20, "END", List.of(new Line(eid(ew), w.code(), 20, 40), new Line(eid(ep), p.code(), 20, 0))));
            assertEquals(200, s.status(), s.json().toString());
            assertEquals(i + 1, journal.wins(w.code(), "quiz", T0.minusSeconds(86_400)));
            assertEquals(0, journal.wins(p.code(), "quiz", T0.minusSeconds(86_400)));
        }
        Reply closed = quizEscrow(w, "NDEM", 20, 1, "real-w-3");
        assertEquals(409, closed.status());
        assertEquals("STAKE_WIN_CAP", closed.reason());
        assertEquals(200, chessEscrow(w, "NDEM", 20, 1, "real-w-chess").status(), "trois victoires au Quiz ne ferment pas les échecs");
        assertEquals(200, quizEscrow(lastLoser, "NDEM", 20, 1, "real-p-next").status(), "un perdant n'est pas plafonné");
        assertReconciled();
    }

    // ---- le règlement d'une table ----

    @Test
    void threeSeatsOnTwoTvsShareThePotByRankAreAggregatedPerTvAndTheJournalHasOneRowPerTv() throws Exception {
        List<Place> t = table("NDEM", 20, "a1", 2, 1);   // A : 2 sièges (40 bloqués), B : 1 siège (20 bloqués)
        Place a = t.get(0), b = t.get(1);
        long a0 = bal(a.tv(), Currency.NDEM), b0 = bal(b.tv(), Currency.NDEM);   // après blocage
        assertEquals(40, locked(a.tv(), Currency.NDEM));
        assertEquals(20, locked(b.tv(), Currency.NDEM));
        // points : A#0 = 9, A#1 = 3, B#0 = 7 → classement A#0 (60 % de 60 = 36), B#0 (30 % = 18), A#1 (10 % = 6) → A reçoit 42, B reçoit 18
        List<Line> lines = served("NDEM", 20, t, new int[][] {{9, 3}, {7}});
        assertEquals(List.of(new Line(a.eid(), a.tv().code(), 40, 42), new Line(b.eid(), b.tv().code(), 20, 18)), lines, "le calcul du service : Pot.split par siège, agrégé par TV");
        String rid = rid(201);
        Reply r = settle(quizResult(rid, "NDEM", 20, "END", lines));
        assertEquals(200, r.status(), r.json().toString());
        assertEquals("quiz", r.json().get("game").asText());
        assertEquals(0, r.json().get("fee").asLong());
        assertEquals(a0 + 42, bal(a.tv(), Currency.NDEM));
        assertEquals(b0 + 18, bal(b.tv(), Currency.NDEM));
        assertEquals(0, locked(a.tv(), Currency.NDEM) + locked(b.tv(), Currency.NDEM));
        assertEquals(0, sys("SYS:POT", "NDEM"), "la cagnotte revient à 0");
        // le journal : une ligne par TV, avec l'issue (gagné : plus que sa mise utilisée ; perdu : moins) et l'adversaire (deux TV)
        assertEquals(2, journalRows(rid));
        assertEquals("WIN", outcome(rid, a.tv()));
        assertEquals("LOSS", outcome(rid, b.tv()));
        assertEquals(b.tv().code(), jdbc.queryForObject("SELECT opponent FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid, a.tv().code()));
        assertEquals(a.tv().code(), jdbc.queryForObject("SELECT opponent FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid, b.tv().code()));
        assertEquals(40, jdbc.queryForObject("SELECT used FROM wallet_game_log WHERE rid = ? AND holder = ?", Long.class, rid, a.tv().code()));
        assertEquals(ROOM_Q, jdbc.queryForObject("SELECT room FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid, a.tv().code()));
        // idempotence par résultat : cinq rejeux rendent la même réponse et ne changent rien (ni grand livre ni journal)
        for (int i = 0; i < 5; i++) assertEquals(r.json(), settle(quizResult(rid, "NDEM", 20, "END", lines)).json());
        assertEquals(a0 + 42, bal(a.tv(), Currency.NDEM));
        assertEquals(2, journalRows(rid));
        assertEquals(1, newTxns("SETTLE"));
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_result WHERE rid = ?", rid));
        // même identifiant, autre contenu : refusé, rien ne bouge
        Reply other = settle(quizResult(rid, "NDEM", 20, "END", List.of(new Line(a.eid(), a.tv().code(), 40, 0), new Line(b.eid(), b.tv().code(), 20, 60))));
        assertEquals(409, other.status());
        assertEquals("IDEM_CONFLICT", other.reason());
        assertEquals(a0 + 42, bal(a.tv(), Currency.NDEM));
        assertReconciled();
    }

    @Test
    void theUnusedPartOfAnEscrowIsGivenBackWhenFewerSeatsPlayThanWereBlocked() throws Exception {
        List<Place> t = table("NDEM", 20, "a2", 2, 1);   // A bloque 40 mais un seul de ses deux sièges est présent au départ
        Place a = t.get(0), b = t.get(1);
        long a0 = bal(a.tv(), Currency.NDEM), b0 = bal(b.tv(), Currency.NDEM);
        // deux sièges seulement : cagnotte de 40, partage 70/30 : le meilleur (A) 28, l'autre (B) 12
        List<Line> lines = served("NDEM", 20, t, new int[][] {{6}, {4}});
        assertEquals(List.of(new Line(a.eid(), a.tv().code(), 20, 28), new Line(b.eid(), b.tv().code(), 20, 12)), lines);
        String rid = rid(202);
        assertEquals(200, settle(quizResult(rid, "NDEM", 20, "END", lines)).status());
        assertEquals(a0 + 20 + 28, bal(a.tv(), Currency.NDEM), "les 20 non utilisés sont rendus, plus 28 gagnés");
        assertEquals(b0 + 12, bal(b.tv(), Currency.NDEM));
        assertEquals(0, locked(a.tv(), Currency.NDEM));
        assertEquals(20, jdbc.queryForObject("SELECT used FROM wallet_game_log WHERE rid = ? AND holder = ?", Long.class, rid, a.tv().code()), "le journal garde ce qui a été engagé, pas ce qui était bloqué");
        assertEquals("WIN", outcome(rid, a.tv()));
        assertEquals("LOSS", outcome(rid, b.tv()));
        assertReconciled();
    }

    @Test
    void eightSeatsOnEachOfTwoTvsAreSettledAndTheBestSeatsTakeTheShares() throws Exception {
        List<Place> t = table("NDEM", 10, "a3", 8, 8);
        Place a = t.get(0), b = t.get(1);
        long a0 = bal(a.tv(), Currency.NDEM), b0 = bal(b.tv(), Currency.NDEM);
        assertEquals(80, locked(a.tv(), Currency.NDEM));
        // 16 sièges, cagnotte 160 : A#0 (9) 96, B#0 (8) 48, A#1 (7) 16, les 13 autres 0 → A reçoit 112 pour 80 engagés, B reçoit 48 pour 80
        int[][] scores = {{9, 7, 1, 1, 1, 1, 1, 1}, {8, 1, 1, 1, 1, 1, 1, 1}};
        List<Line> lines = served("NDEM", 10, t, scores);
        assertEquals(112, lines.get(0).pay());
        assertEquals(48, lines.get(1).pay());
        // (les points 1 reçoivent 0 : seules les trois premières places paient, les ex æquo à 1 se partagent des places à 0 %)
        String rid = rid(203);
        assertEquals(200, settle(quizResult(rid, "NDEM", 10, "END", lines)).status());
        assertEquals(a0 + 112, bal(a.tv(), Currency.NDEM));
        assertEquals(b0 + 48, bal(b.tv(), Currency.NDEM));
        assertEquals("WIN", outcome(rid, a.tv()));
        assertEquals("LOSS", outcome(rid, b.tv()));
        assertReconciled();
    }

    @Test
    void aTieBetweenTwoTvsGivesEachItsStakeBackWithoutFeesAndIsADrawInTheJournal() throws Exception {
        setPolicy("game.quiz.feeBp", 500);   // même avec des frais en politique : une partie non décisive n'en paie pas
        try {
            List<Place> t = table("NDEM", 20, "b", 1, 1);
            Place a = t.get(0), b = t.get(1);
            long a0 = bal(a.tv(), Currency.NDEM) + 20, b0 = bal(b.tv(), Currency.NDEM) + 20;   // avant le blocage
            long feeBefore = sys("SYS:FEE", "NDEM");
            List<Line> lines = served("NDEM", 20, t, new int[][] {{5}, {5}});
            assertEquals(List.of(new Line(a.eid(), a.tv().code(), 20, 20), new Line(b.eid(), b.tv().code(), 20, 20)), lines, "ex æquo : parts égales");
            String rid = rid(210);
            Reply r = settle(quizResult(rid, "NDEM", 20, "END", lines));
            assertEquals(200, r.status(), r.json().toString());
            assertEquals(0, r.json().get("fee").asLong());
            assertEquals(a0, bal(a.tv(), Currency.NDEM));
            assertEquals(b0, bal(b.tv(), Currency.NDEM));
            assertEquals("DRAW", outcome(rid, a.tv()));
            assertEquals("DRAW", outcome(rid, b.tv()));
            assertEquals(feeBefore, sys("SYS:FEE", "NDEM"));
            assertEquals(0, journal.wins(a.tv().code(), "quiz", T0.minusSeconds(86_400)), "une égalité n'est pas une partie gagnée");
            assertReconciled();
        } finally {
            setPolicy("game.quiz.feeBp", 0);
        }
    }

    @Test
    void whenNobodyScoredEveryTvTakesItsUsedStakeBackAndNoWinIsCounted() throws Exception {
        List<Place> t = table("NDEM", 20, "c", 2, 1, 1);
        long[] before = new long[3];
        for (int i = 0; i < 3; i++) before[i] = bal(t.get(i).tv(), Currency.NDEM) + 20L * t.get(i).k();
        List<Line> lines = served("NDEM", 20, t, new int[][] {{0, 0}, {0}, {0}});
        assertEquals(List.of(new Line(t.get(0).eid(), t.get(0).tv().code(), 40, 40), new Line(t.get(1).eid(), t.get(1).tv().code(), 20, 20), new Line(t.get(2).eid(), t.get(2).tv().code(), 20, 20)), lines);
        String rid = rid(211);
        assertEquals(200, settle(quizResult(rid, "NDEM", 20, "END", lines)).status());
        for (int i = 0; i < 3; i++) {
            assertEquals(before[i], bal(t.get(i).tv(), Currency.NDEM), "chaque TV reprend sa mise");
            assertEquals(0, locked(t.get(i).tv(), Currency.NDEM));
            assertEquals("DRAW", outcome(rid, t.get(i).tv()));
            assertEquals(0, journal.wins(t.get(i).tv().code(), "quiz", T0.minusSeconds(86_400)));
        }
        assertEquals(3, journalRows(rid));
        assertNull(jdbc.queryForObject("SELECT opponent FROM wallet_game_log WHERE rid = ? AND holder = ?", String.class, rid, t.get(0).tv().code()), "l'adversaire n'est inscrit que pour deux TV");
        assertReconciled();
    }

    @Test
    void anAbortGivesEveryEscrowBackInFullAtAnyTableSizeWithNoFeeAndTheJournalSaysAbort() throws Exception {
        setPolicy("game.quiz.feeBp", 2000);
        try {
            List<Place> t = table("NDEM", 20, "d", 2, 1, 1);
            long[] before = new long[3];
            for (int i = 0; i < 3; i++) before[i] = bal(t.get(i).tv(), Currency.NDEM) + 20L * t.get(i).k();
            long feeBefore = sys("SYS:FEE", "NDEM");
            // un ABORT qui déplace de la valeur : refusé, rien ne bouge
            assertBad(settle(quizResult(rid(220), "NDEM", 20, "ABORT", List.of(new Line(t.get(0).eid(), t.get(0).tv().code(), 40, 40), new Line(t.get(1).eid(), t.get(1).tv().code(), 0, 0), new Line(t.get(2).eid(), t.get(2).tv().code(), 0, 0)))));
            assertEquals(40, locked(t.get(0).tv(), Currency.NDEM));
            String rid = rid(221);
            Reply r = settle(quizResult(rid, "NDEM", 20, "ABORT", nothing(t)));
            assertEquals(200, r.status(), r.json().toString());
            assertEquals(0, r.json().get("fee").asLong());
            for (int i = 0; i < 3; i++) {
                assertEquals(before[i], bal(t.get(i).tv(), Currency.NDEM), "tout est rendu");
                assertEquals(0, locked(t.get(i).tv(), Currency.NDEM));
                assertEquals("ABORT", outcome(rid, t.get(i).tv()));
            }
            assertEquals(feeBefore, sys("SYS:FEE", "NDEM"));
            // une seule TV (aucune autre n'est venue) : son blocage est rendu, sans adversaire ; en MBOKO aussi
            Place solo = table("MBOKO", 5, "d-solo", 3).get(0);
            long s0 = bal(solo.tv(), Currency.MBOKO) + 15;
            assertEquals(200, settle(quizResult(rid(222), "MBOKO", 5, "ABORT", nothing(List.of(solo)))).status());
            assertEquals(s0, bal(solo.tv(), Currency.MBOKO));
            assertNull(jdbc.queryForObject("SELECT opponent FROM wallet_game_log WHERE rid = ?", String.class, rid(222)));
            assertEquals("ABORT", outcome(rid(222), solo.tv()));
            assertReconciled();
        } finally {
            setPolicy("game.quiz.feeBp", 0);
        }
    }

    @Test
    void platformFeesAreTakenFromTheBiggestShareOfADecisiveTableOnlyAndShownInTheResponse() throws Exception {
        setPolicy("game.quiz.feeBp", 500);   // 5 %
        try {
            List<Place> t = table("NDEM", 100, "fee-1", 1, 1, 1);   // A, B, C (la plus grosse part est celle de B, au milieu de la liste)
            Place a = t.get(0), b = t.get(1), c = t.get(2);
            long a0 = bal(a.tv(), Currency.NDEM), b0 = bal(b.tv(), Currency.NDEM), c0 = bal(c.tv(), Currency.NDEM), feeBefore = sys("SYS:FEE", "NDEM");
            // A 5 points, B 9, C 1 : cagnotte 300, parts 60/30/10 → B 180, A 90, C 30
            List<Line> lines = served("NDEM", 100, t, new int[][] {{5}, {9}, {1}});
            assertEquals(List.of(new Line(a.eid(), a.tv().code(), 100, 90), new Line(b.eid(), b.tv().code(), 100, 180), new Line(c.eid(), c.tv().code(), 100, 30)), lines);
            String rid = rid(230);
            Reply r = settle(quizResult(rid, "NDEM", 100, "END", lines));
            assertEquals(200, r.status(), r.json().toString());
            assertEquals(15, r.json().get("fee").asLong(), "5 % de la cagnotte de 300");
            assertEquals(0, r.json().get("lines").get(0).get("fee").asLong());
            assertEquals(15, r.json().get("lines").get(1).get("fee").asLong(), "les frais sont sur la plus grosse part");
            assertEquals(0, r.json().get("lines").get(2).get("fee").asLong());
            assertEquals(180, r.json().get("lines").get(1).get("pay").asLong(), "le résultat garde la part avant frais ; la TV lit pay − fee");
            assertEquals(a0 + 90, bal(a.tv(), Currency.NDEM));
            assertEquals(b0 + 165, bal(b.tv(), Currency.NDEM), "180 − 15");
            assertEquals(c0 + 30, bal(c.tv(), Currency.NDEM));
            assertEquals(feeBefore + 15, sys("SYS:FEE", "NDEM"));
            assertEquals(0, sys("SYS:POT", "NDEM"), "la cagnotte revient à 0");
            assertEquals(15, journalFee(rid, b.tv()));
            assertEquals(0, journalFee(rid, a.tv()) + journalFee(rid, c.tv()));
            assertEquals(180, jdbc.queryForObject("SELECT pay FROM wallet_game_log WHERE rid = ? AND holder = ?", Long.class, rid, b.tv().code()));
            // le rejeu rend la même réponse, frais compris, même si la politique a changé entre-temps
            setPolicy("game.quiz.feeBp", 0);
            assertEquals(r.json(), settle(quizResult(rid, "NDEM", 100, "END", lines)).json());
            assertEquals(feeBefore + 15, sys("SYS:FEE", "NDEM"));
            assertReconciled();
        } finally {
            setPolicy("game.quiz.feeBp", 0);
        }
    }

    @Test
    void withNoFeeNothingIsEverCreditedToTheFeeAccountAtTheQuiz() throws Exception {
        long n0 = sys("SYS:FEE", "NDEM"), m0 = sys("SYS:FEE", "MBOKO");
        List<Place> t = table("NDEM", 20, "nofee", 1, 1);
        assertEquals(200, settle(quizResult(rid(240), "NDEM", 20, "END", served("NDEM", 20, t, new int[][] {{7}, {3}}))).status());
        assertEquals(n0, sys("SYS:FEE", "NDEM"));
        assertEquals(m0, sys("SYS:FEE", "MBOKO"));
    }

    @Test
    void aMbokoTableSettlesTheSameWayWithFeesAllowed() throws Exception {
        setPolicy("game.quiz.feeBp", 1000);   // 10 % de 10 MBOKO = 1
        try {
            List<Place> t = table("MBOKO", 5, "m", 1, 1);
            long fee0 = sys("SYS:FEE", "MBOKO");
            Reply r = settle(quizResult(rid(245), "MBOKO", 5, "END", served("MBOKO", 5, t, new int[][] {{4}, {0}})));
            assertEquals(200, r.status(), r.json().toString());
            assertEquals(1, r.json().get("fee").asLong());
            assertEquals(fee0 + 1, sys("SYS:FEE", "MBOKO"));
            assertEquals("WIN", outcome(rid(245), t.get(0).tv()));
            assertEquals("LOSS", outcome(rid(245), t.get(1).tv()));
            assertReconciled();
        } finally {
            setPolicy("game.quiz.feeBp", 0);
        }
    }

    // ---- les refus : une forme impossible ne se règle jamais, quoi que dise la signature ----

    @Test
    void impossibleTableShapesAreRefusedWhateverTheSignatureSaysAndNothingMoves() throws Exception {
        List<Place> t = table("NDEM", 20, "shape", 2, 1);   // A : 40 bloqués, B : 20 bloqués
        Place a = t.get(0), b = t.get(1);
        String ac = a.tv().code(), bc = b.tv().code();
        // une partie terminée avec une seule TV : aucun jeton ne circule
        assertBad(settle(quizResult(rid(250), "NDEM", 20, "END", List.of(new Line(a.eid(), ac, 40, 40)))));
        // deux TV listées mais une seule a engagé une mise (l'autre n'a aucun siège présent)
        assertBad(settle(quizResult(rid(251), "NDEM", 20, "END", List.of(new Line(a.eid(), ac, 40, 40), new Line(b.eid(), bc, 0, 0)))));
        // un utilisé qui n'est pas un nombre entier de mises (30 pour des mises de 20)
        assertBad(settle(quizResult(rid(252), "NDEM", 20, "END", List.of(new Line(a.eid(), ac, 30, 30), new Line(b.eid(), bc, 20, 20)))));
        // un gain sans aucune mise utilisée (B n'a rien engagé mais reçoit 20)
        assertBad(settle(quizResult(rid(253), "NDEM", 20, "END", List.of(new Line(a.eid(), ac, 40, 20), new Line(b.eid(), bc, 0, 20)))));
        // un utilisé supérieur au blocage
        assertBad(settle(quizResult(rid(254), "NDEM", 20, "END", List.of(new Line(a.eid(), ac, 40, 40), new Line(b.eid(), bc, 40, 40)))));
        // la conservation : payé ≠ utilisé
        Reply unbalanced = settle(quizResult(rid(255), "NDEM", 20, "END", List.of(new Line(a.eid(), ac, 40, 41), new Line(b.eid(), bc, 20, 20))));
        assertEquals(400, unbalanced.status());
        assertEquals("UNBALANCED", unbalanced.reason());
        // rien n'a bougé, les deux blocages sont encore ouverts, aucune ligne de journal
        assertEquals(40, locked(a.tv(), Currency.NDEM));
        assertEquals(20, locked(b.tv(), Currency.NDEM));
        assertEquals(0, newTxns("SETTLE"));
        long rows = 0;
        for (int i = 250; i <= 255; i++) rows += journalRows(rid(i));
        assertEquals(0, rows);
        // la partie d'origine se règle ensuite normalement
        assertEquals(200, settle(quizResult(rid(259), "NDEM", 20, "END", List.of(new Line(a.eid(), ac, 40, 42), new Line(b.eid(), bc, 20, 18)))).status());
        assertEquals(0, locked(a.tv(), Currency.NDEM) + locked(b.tv(), Currency.NDEM));
    }

    @Test
    void aTvDoesNotPlayAgainstItselfWhateverTheNumberOfItsEscrows() throws Exception {
        Tv a = richTv(), other = richTv();
        String e1 = eid(quizEscrow(a, "NDEM", 20, 1, "same-1")), e2 = eid(quizEscrow(a, "NDEM", 20, 1, "same-2")), eo = eid(quizEscrow(other, "NDEM", 20, 1, "same-o"));
        // deux blocages d'une même identité dans le même résultat : une TV ne joue pas contre elle-même (partie terminée comme interrompue)
        Reply end = settle(quizResult(rid(260), "NDEM", 20, "END", List.of(new Line(e1, a.code(), 20, 40), new Line(e2, a.code(), 20, 0))));
        assertBad(end);
        assertEquals("Une TV ne joue pas contre elle-même", end.message());
        assertBad(settle(quizResult(rid(261), "NDEM", 20, "ABORT", List.of(new Line(e1, a.code(), 0, 0), new Line(e2, a.code(), 0, 0)))));
        // même avec une troisième TV autour de la table
        assertBad(settle(quizResult(rid(262), "NDEM", 20, "END", List.of(new Line(e1, a.code(), 20, 20), new Line(eo, other.code(), 20, 20), new Line(e2, a.code(), 20, 20)))));
        assertEquals(0, newTxns("SETTLE"));
        assertEquals(40, locked(a, Currency.NDEM));
        // le même blocage deux fois (déjà refusé par le règlement)
        assertBad(settle(quizResult(rid(263), "NDEM", 20, "END", List.of(new Line(e1, a.code(), 20, 20), new Line(e1, a.code(), 20, 20)))));
    }

    @Test
    void aTableHasAtMostEightTvs() throws Exception {
        List<Place> nine = table("NDEM", 20, "nine", 1, 1, 1, 1, 1, 1, 1, 1, 1);
        // neuf TV : refusé, rien ne bouge (même pour une interruption)
        int[][] nineScores = {{9}, {8}, {7}, {6}, {5}, {4}, {3}, {2}, {1}};
        Reply refused = settle(quizResult(rid(270), "NDEM", 20, "END", served("NDEM", 20, nine, nineScores)));
        assertBad(refused);
        assertEquals("Une table du Quiz n'a pas plus de 8 TV", refused.message());
        assertBad(settle(quizResult(rid(271), "NDEM", 20, "ABORT", nothing(nine))));
        assertEquals(0, newTxns("SETTLE"));
        // huit TV : réglé ; cagnotte 160 : 96 / 48 / 16 aux trois premières places, rien aux cinq autres
        List<Place> eight = nine.subList(0, 8);
        long[] before = new long[8];
        long heldBefore = 0;
        for (int i = 0; i < 8; i++) {
            before[i] = bal(eight.get(i).tv(), Currency.NDEM);
            heldBefore += before[i] + locked(eight.get(i).tv(), Currency.NDEM);
        }
        List<Line> lines = served("NDEM", 20, eight, new int[][] {{9}, {8}, {7}, {6}, {5}, {4}, {3}, {2}});
        assertEquals(List.of(96L, 48L, 16L, 0L, 0L, 0L, 0L, 0L), lines.stream().map(Line::pay).toList());
        String rid = rid(272);
        Reply r = settle(quizResult(rid, "NDEM", 20, "END", lines));
        assertEquals(200, r.status(), r.json().toString());
        long heldAfter = 0;
        for (int i = 0; i < 8; i++) {
            heldAfter += bal(eight.get(i).tv(), Currency.NDEM) + locked(eight.get(i).tv(), Currency.NDEM);
            assertEquals(before[i] + lines.get(i).pay(), bal(eight.get(i).tv(), Currency.NDEM));
        }
        assertEquals(heldBefore, heldAfter, "la conservation tient à huit TV");
        assertEquals(8, journalRows(rid), "une ligne par TV");
        assertEquals(2, count("SELECT COUNT(*) FROM wallet_game_log WHERE rid = ? AND outcome = 'WIN'", rid));
        assertEquals(6, count("SELECT COUNT(*) FROM wallet_game_log WHERE rid = ? AND outcome = 'LOSS'", rid));
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_game_log WHERE rid = ? AND opponent IS NOT NULL", rid), "pas d'adversaire inscrit au-delà de deux TV");
        assertReconciled();
    }

    @Test
    void aResultOfOneGameNeverSettlesTheEscrowsOfAnotherGameOrOfNoGameAndANoGameResultStillSettlesNoGameEscrows() throws Exception {
        List<Place> quiz = table("NDEM", 20, "x-quiz", 1, 1);
        Tv ca = richTv(), cb = richTv(), la = richTv(), lb = richTv();
        String ea = eid(chessEscrow(ca, "NDEM", 20, 1, "x-chess-a")), eb = eid(chessEscrow(cb, "NDEM", 20, 1, "x-chess-b"));   // blocages d'échecs
        String xa = eid(escrow(la, "NDEM", 20, 1, "x-legacy-a")), xb = eid(escrow(lb, "NDEM", 20, 1, "x-legacy-b"));            // blocages sans jeu (usages d'avant)
        List<Line> onQuiz = List.of(new Line(quiz.get(0).eid(), quiz.get(0).tv().code(), 20, 40), new Line(quiz.get(1).eid(), quiz.get(1).tv().code(), 20, 0));
        // un résultat du Quiz ne règle pas des blocages d'échecs
        assertOtherGame(settle(quizResult(rid(280), "NDEM", 20, "END", List.of(new Line(ea, ca.code(), 20, 40), new Line(eb, cb.code(), 20, 0)))));
        // un résultat d'échecs ne règle pas des blocages du Quiz
        assertOtherGame(settle(gameResult("chess", rid(281), "NDEM", 20, "END", onQuiz)));
        // un résultat du Quiz ne règle pas des blocages sans jeu : sinon bloquer sans `game` contournerait l'échelle, l'essai et les plafonds du jeu
        assertOtherGame(settle(quizResult(rid(282), "NDEM", 20, "END", List.of(new Line(xa, la.code(), 20, 40), new Line(xb, lb.code(), 20, 0)))));
        // …ni un mélange de blocages du Quiz et de blocages sans jeu
        assertOtherGame(settle(quizResult(rid(283), "NDEM", 20, "END", List.of(onQuiz.get(0), new Line(xa, la.code(), 20, 0)))));
        // un résultat qui n'est celui d'aucun jeu misé ne règle pas des blocages du Quiz
        assertOtherGame(settle(cbr1(RESULT, rid(284), "NDEM", 20, "END", onQuiz)));
        assertEquals(0, newTxns("SETTLE"));
        assertEquals(20, locked(quiz.get(0).tv(), Currency.NDEM));
        assertEquals(20, locked(ca, Currency.NDEM));
        assertEquals(20, locked(la, Currency.NDEM));
        // les usages d'avant : un résultat sans jeu règle toujours des blocages sans jeu, sans forme de jeu ni journal
        assertEquals(200, settle(cbr1(RESULT, rid(285), "NDEM", 20, "END", List.of(new Line(xa, la.code(), 20, 40), new Line(xb, lb.code(), 20, 0)))).status());
        assertEquals(0, journalRows(rid(285)));
        // et la table du Quiz se règle normalement ensuite
        assertEquals(200, settle(quizResult(rid(286), "NDEM", 20, "END", onQuiz)).status());
        assertEquals(2, journalRows(rid(286)));
        assertReconciled();
    }

    // ---- la conservation, au hasard ----

    private long held(Tv tv, Currency c) { return bal(tv, c) + locked(tv, c); }

    @Test
    void randomQuizTablesKeepTheLedgerConservativeTheFeeExactAndTheJournalOneRowPerTv() throws Exception {
        Random rnd = new Random(20261007L);
        for (String cap : List.of("day", "week", "month")) setPolicy("game.quiz.cap.win." + cap, 0);   // des dizaines de victoires par TV : les plafonds ont leur propre test
        try {
            List<Tv> tvs = new ArrayList<>();
            for (int i = 0; i < 6; i++) tvs.add(richTv());
            long[] ndem = {10, 20, 50, 100, 200}, mboko = {1, 2, 5, 10};
            int rid = 4000, idem = 0, settled = 0, refused = 0, aborted = 0, withFee = 0, withSeveralWinners = 0;
            for (int round = 0; round < 90; round++) {
                boolean m = rnd.nextInt(3) == 0;
                String cur = m ? "MBOKO" : "NDEM";
                Currency c = Currency.valueOf(cur);
                long per = m ? mboko[rnd.nextInt(mboko.length)] : ndem[rnd.nextInt(ndem.length)];
                long feeBp = new long[] {0, 0, 250, 500, 1000}[rnd.nextInt(5)];
                setPolicy("game.quiz.feeBp", feeBp);
                List<Tv> pool = new ArrayList<>(tvs);
                Collections.shuffle(pool, rnd);
                List<Place> table = new ArrayList<>();
                for (Tv tv : pool.subList(0, 2 + rnd.nextInt(4))) {
                    int k = 1 + rnd.nextInt(4);
                    Reply e = quizEscrow(tv, cur, per, k, "r-" + idem++);
                    if (e.status() != 200) {
                        assertEquals(409, e.status(), e.json().toString());
                        assertEquals("INSUFFICIENT", e.reason());
                        continue;
                    }
                    table.add(new Place(tv, k, eid(e)));
                }
                if (table.isEmpty()) continue;
                int[][] scores = new int[table.size()][];
                int playing = 0;
                for (int i = 0; i < table.size(); i++) {
                    scores[i] = new int[rnd.nextInt(table.get(i).k() + 1)];   // de 0 (la TV n'est pas venue) à k sièges présents
                    for (int j = 0; j < scores[i].length; j++) scores[i][j] = rnd.nextInt(5);   // beaucoup d'ex æquo et de zéros
                    if (scores[i].length > 0) playing++;
                }
                boolean abort = rnd.nextInt(6) == 0;
                List<Line> lines = abort ? nothing(table) : served(cur, per, table, scores);
                long[] heldBefore = new long[table.size()];
                for (int i = 0; i < table.size(); i++) heldBefore[i] = held(table.get(i).tv(), c);
                long feeBefore = sys("SYS:FEE", cur);
                String ridHex = rid(rid++);
                String token = quizResult(ridHex, cur, per, abort ? "ABORT" : "END", lines);
                Reply s = settle(token);
                if (!abort && playing < 2) {
                    // la forme refuse : une TV seule ne joue contre personne ; rien ne bouge, puis le service interrompt la partie et tout est rendu
                    assertBad(s);
                    for (int i = 0; i < table.size(); i++) assertEquals(heldBefore[i], held(table.get(i).tv(), c));
                    assertEquals(200, settle(quizResult(rid(rid++), cur, per, "ABORT", nothing(table))).status());
                    refused++;
                    continue;
                }
                assertEquals(200, s.status(), s.json().toString());
                long fee = s.json().get("fee").asLong();
                boolean decisive = !abort && lines.stream().anyMatch(l -> l.pay() > l.used());
                long totalUsed = lines.stream().mapToLong(Line::used).sum();
                assertEquals(decisive ? totalUsed * feeBp / 10_000L : 0L, fee, "frais = feeBp × Σ utilisé d'une partie décisive, sinon 0");
                int top = 0;
                for (int i = 1; i < lines.size(); i++) if (lines.get(i).pay() > lines.get(top).pay()) top = i;
                long sumBefore = 0, sumAfter = 0;
                int winners = 0;
                for (int i = 0; i < table.size(); i++) {
                    Tv tv = table.get(i).tv();
                    Line l = lines.get(i);
                    long fi = s.json().get("lines").get(i).get("fee").asLong();
                    assertEquals(i == top ? fee : 0L, fi, "les frais sont sur la plus grosse part (la première en cas d'égalité)");
                    assertEquals(heldBefore[i] - l.used() + l.pay() - fi, held(tv, c), "ce que détient la TV : − utilisé + part − frais");
                    assertEquals(0, locked(tv, c), "plus rien de bloqué");
                    assertTrue(bal(tv, c) >= 0);
                    String expected = abort ? "ABORT" : l.pay() > l.used() ? "WIN" : l.pay() == l.used() ? "DRAW" : "LOSS";
                    assertEquals(expected, outcome(ridHex, tv));
                    assertEquals(fi, journalFee(ridHex, tv));
                    if (expected.equals("WIN")) winners++;
                    sumBefore += heldBefore[i];
                    sumAfter += held(tv, c);
                }
                assertEquals(sumBefore - fee, sumAfter, "conservation : la somme détenue par la table ne change que des frais");
                assertEquals(feeBefore + fee, sys("SYS:FEE", cur));
                assertEquals(table.size(), journalRows(ridHex), "une ligne de journal par TV");
                if (rnd.nextInt(3) == 0) assertEquals(s.json(), settle(token).json(), "rejeu : même réponse");
                if (abort) aborted++; else settled++;
                if (fee > 0) withFee++;
                if (winners > 1) withSeveralWinners++;
                if (round % 15 == 14) assertReconciled();
            }
            assertReconciled();
            assertEquals(0, sys("SYS:POT", "NDEM"));
            assertEquals(0, sys("SYS:POT", "MBOKO"));
            assertEquals(0, ledger.sum(Currency.NDEM));
            assertEquals(0, ledger.sum(Currency.MBOKO));
            assertTrue(settled > 30 && refused > 0 && aborted > 0 && withFee > 0 && withSeveralWinners > 0,
                    "la propriété a vraiment tout exercé : réglées " + settled + ", refusées " + refused + ", interrompues " + aborted + ", avec frais " + withFee + ", à plusieurs gagnants " + withSeveralWinners);
            for (Tv tv : tvs) assertEquals(0, locked(tv, Currency.NDEM) + locked(tv, Currency.MBOKO), "aucun blocage du Quiz n'est resté ouvert");
        } finally {
            setPolicy("game.quiz.feeBp", 0);
            setPolicy("game.quiz.cap.win.day", 3); setPolicy("game.quiz.cap.win.week", 10); setPolicy("game.quiz.cap.win.month", 15);
        }
    }

    // ---- ce que la TV et l'exploitant lisent ----

    private static List<Long> longs(JsonNode a) { List<Long> l = new ArrayList<>(); a.forEach(n -> l.add(n.asLong())); return l; }

    @Test
    void thePolicyRouteTellsBothGamesTheirScaleTheFeeTheCapsAndTheSeats() throws Exception {
        Tv tv = trialTv();
        JsonNode p = body(mvc.perform(get("/api/v1/wallet/policy").header("Authorization", tv.auth())).andExpect(status().isOk()).andReturn());
        JsonNode games = p.get("games");
        Set<String> names = new HashSet<>();
        for (Iterator<String> it = games.fieldNames(); it.hasNext(); ) names.add(it.next());
        assertEquals(Set.of("chess", "quiz"), names, "les échecs ET le Quiz");
        JsonNode quiz = games.get("quiz");
        assertTrue(quiz.get("enabled").asBoolean());
        assertEquals(List.of(10L, 20L, 50L, 100L, 200L), longs(quiz.get("stakes").get("NDEM")));
        assertEquals(List.of(1L, 2L, 5L, 10L), longs(quiz.get("stakes").get("MBOKO")));
        assertEquals(0, quiz.get("feeBp").asInt());
        assertEquals(3, quiz.get("winCaps").get("day").asInt()); assertEquals(10, quiz.get("winCaps").get("week").asInt()); assertEquals(15, quiz.get("winCaps").get("month").asInt());
        assertEquals(8, quiz.get("seats").asInt(), "jusqu'à 8 sièges par TV au Quiz");
        assertFalse(quiz.get("trialStakes").asBoolean());
        assertEquals(1, games.get("chess").get("seats").asInt(), "une mise par TV aux échecs");
        assertEquals(List.of(10L, 20L, 50L, 100L, 200L), longs(games.get("chess").get("stakes").get("NDEM")));
        // l'interrupteur d'un jeu ne change que ce jeu
        setPolicy("game.quiz.switch", 0);
        try {
            JsonNode off = body(mvc.perform(get("/api/v1/wallet/policy").header("Authorization", tv.auth())).andExpect(status().isOk()).andReturn()).get("games");
            assertFalse(off.get("quiz").get("enabled").asBoolean());
            assertTrue(off.get("chess").get("enabled").asBoolean());
        } finally {
            setPolicy("game.quiz.switch", 1);
        }
    }

    @Test
    void theAdminGamesRouteListsTheQuizJournalOneRowPerTvAndFiltersByGame() throws Exception {
        List<Place> t = table("NDEM", 20, "admin-1", 1, 1, 1);
        String rid = rid(290);
        assertEquals(200, settle(quizResult(rid, "NDEM", 20, "END", served("NDEM", 20, t, new int[][] {{9}, {5}, {1}}))).status());
        String winner = t.get(0).tv().code();
        JsonNode mine = body(mvc.perform(get("/api/v1/admin/wallet/games").param("game", "quiz").param("holder", winner).header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn());
        JsonNode row = mine.get("games").get(0);
        assertEquals(rid, row.get("rid").asText());
        assertEquals("quiz", row.get("game").asText());
        assertEquals("WIN", row.get("outcome").asText());
        assertTrue(row.get("opponent").isNull(), "trois TV : pas d'adversaire inscrit");
        assertEquals(36, row.get("pay").asLong(), "60 % de 60");
        assertEquals(20, row.get("used").asLong());
        JsonNode all = body(mvc.perform(get("/api/v1/admin/wallet/games").param("game", "quiz").param("limit", "200").header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn());
        int rows = 0;
        for (JsonNode g : all.get("games")) if (rid.equals(g.get("rid").asText())) rows++;
        assertEquals(3, rows, "une ligne par TV");
        JsonNode chess = body(mvc.perform(get("/api/v1/admin/wallet/games").param("game", "chess").param("holder", winner).header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn());
        assertEquals(0, chess.get("games").size(), "le filtre par jeu ne mêle pas les échecs et le Quiz");
        mvc.perform(get("/api/v1/admin/wallet/games").param("game", "quiz")).andExpect(status().isUnauthorized());
    }

    @Test
    void theMigrationLeavesTheQuizPolicyWithTheSameValuesAndBoundsAsTheChessOneAndTheCodeKnowsBothGames() throws Exception {
        assertEquals(17, count("SELECT COUNT(*) FROM wallet_policy WHERE name LIKE 'game.quiz.%'"), "interrupteur, 12 paliers, frais, 3 plafonds");
        assertEquals(Set.of("chess", "quiz"), WalletPolicyService.GAMES);
        // V69 reprend V68 ligne pour ligne : mêmes valeurs de lancement, mêmes bornes (seul le nom du jeu change)
        Path dir = Path.of("src", "main", "resources", "db", "migration");
        List<String> chess = policyTuples(Files.readString(dir.resolve("V68__chess_stakes.sql")), "chess");
        List<String> quiz = policyTuples(Files.readString(dir.resolve("V69__quiz_stakes.sql")), "quiz");
        assertEquals(17, chess.size());
        assertEquals(chess.stream().map(s -> s.replace("game.chess.", "game.quiz.")).toList(), quiz);
        // le retour arrière ne retire que les lignes du Quiz et l'entrée Flyway de V69 (aucun schéma n'a changé)
        String rollback = Files.readString(Path.of("src", "main", "resources", "db", "rollback", "U69__quiz_stakes_rollback.sql"));
        assertTrue(rollback.contains("DELETE FROM wallet_policy WHERE name LIKE 'game.quiz.%';"));
        assertTrue(rollback.contains("DELETE FROM flyway_schema_history WHERE version = '69';"));
        assertFalse(rollback.contains("DROP ") || rollback.contains("ALTER ") || rollback.contains("game.chess"), "U69 ne touche ni au schéma ni à la politique des échecs");
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_game_log WHERE rid = 'jamais'"));
    }

    /** Les lignes d'INSERT de la politique d'un jeu : « ('game.<jeu>.…', valeur, min, max, …) » sans la virgule ni le point-virgule de fin. */
    private static List<String> policyTuples(String sql, String game) {
        List<String> out = new ArrayList<>();
        for (String line : sql.split("\n")) {
            String s = line.strip();
            if (s.startsWith("('game." + game + ".")) out.add(s.replaceAll("[,;]$", ""));
        }
        return out;
    }
}
