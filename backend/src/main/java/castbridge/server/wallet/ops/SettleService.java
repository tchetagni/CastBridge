package castbridge.server.wallet.ops;

import castbridge.server.wallet.JdbcLedger;
import castbridge.server.wallet.WalletModuleConfig;
import castbridge.server.wallet.WalletPolicyService;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.Settlement;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.web.ApiException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Règlement d'une partie par résultat signé {@code cbr1} (conception § 3.3), SANS authentification (le résultat est signé : n'importe qui peut poster un résultat authentique). Tout ou rien,
 * en UNE transaction {@code SETTLE} (clé {@code settle:<rid>}) : chaque blocage listé doit exister, être ouvert, avoir la même monnaie, la même mise par siège et le même titulaire ;
 * {@link Settlement#check} impose Σ payé = Σ utilisé ; le non-utilisé est rendu. Un {@code ABORT} rend tout. Un blocage déjà rendu (échéance) refuse le résultat entier
 * ({@code RESULT_AFTER_REFUND}, journalisé). Idempotent : un rejeu du même résultat rend la même réponse sans rien reposer.
 * <p>Jeux misés ({@link WalletPolicyService#GAMES}) : le résultat d'un jeu ne règle que des blocages faits POUR CE JEU ; sa FORME est vérifiée (un duel aux échecs, une table au Quiz), les frais de
 * plateforme de sa politique sont prélevés sur la cagnotte d'une partie décisive, et une ligne par TV s'inscrit au journal des parties misées dans la transaction du règlement.
 */
@Service
@WalletModuleConfig.Enabled
public class SettleService {
    private static final Logger log = LoggerFactory.getLogger(SettleService.class);
    private final PlayResultKeys keys;
    private final JdbcLedger ledger;
    private final JdbcTemplate jdbc;
    private final WalletModuleConfig.WalletClock clock;
    private final WalletPolicyService policies;
    private final GameJournal journal;

    public SettleService(PlayResultKeys keys, JdbcLedger ledger, JdbcTemplate jdbc, WalletModuleConfig.WalletClock clock, WalletPolicyService policies, GameJournal journal) {
        this.policies = policies;
        this.journal = journal;
        this.keys = keys;
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private record Row(String eid, String holder, Currency cur, Long per, Long k, long amount, String state, String settledRid, String room, String game) {}

    public Map<String, Object> settle(String token) {
        if (!keys.configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Règlement indisponible : aucune clé de service de jeu configurée");
        // M3 : interrupteur à chaud (lu à chaque appel) : coupé, rien n'est réglé et le collecteur réessaie (503)
        if (!policies.switches().settle()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Règlement suspendu pour maintenance : le résultat sera réessayé");
        PlayResultKeys.Result r = keys.verify(token);
        // 1. rejeu : même rid et même contenu = même réponse ; même rid et autre contenu = conflit
        List<String[]> prior = jdbc.query("SELECT sha, outcome FROM wallet_result WHERE rid = ?", (rs, i) -> new String[] {rs.getString("sha"), rs.getString("outcome")}, r.rid());
        if (!prior.isEmpty()) {
            if (!prior.get(0)[0].equals(r.sha())) throw new LedgerException(WalletReason.IDEM_CONFLICT, "Ce résultat existe déjà avec un autre contenu : " + r.rid());
            if ("AFTER_REFUND".equals(prior.get(0)[1])) throw afterRefund(r);
            return response(r);
        }
        // une coupure entre la transaction du règlement et la mémorisation du résultat : le règlement existe déjà (même clé), on le rejoue sans rien recalculer (la politique de frais a pu changer entre-temps)
        if (!jdbc.queryForList("SELECT 1 FROM wallet_txn WHERE idem_key = ?", Integer.class, "settle:" + r.rid()).isEmpty()) {
            remember(r, r.kind() == Settlement.Kind.ABORT ? "ABORT" : "SETTLED", r.lines().stream().mapToLong(Settlement.Line::pay).sum());
            return response(r);
        }
        // 2. chaque blocage existe, avec la même monnaie, la même mise par siège et le même titulaire
        Map<String, Row> rows = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Settlement.Line l : r.lines()) {
            if (!seen.add(l.eid())) throw new LedgerException(WalletReason.BAD_TXN, "Blocage réglé deux fois dans le même règlement : " + l.eid());
            Row row = load(l.eid());
            if (row == null) throw new LedgerException(WalletReason.ESCROW_UNKNOWN, "Blocage inconnu dans le règlement : " + l.eid());
            rows.put(l.eid(), row);
        }
        for (Settlement.Line l : r.lines()) {
            Row row = rows.get(l.eid());
            if (row.cur() != r.cur()) throw new LedgerException(WalletReason.BAD_TXN, "Monnaie différente de celle du blocage : " + l.eid());
            if (row.room() != null && !row.room().equals(r.room())) throw new LedgerException(WalletReason.BAD_TXN, "Salle différente de celle du blocage : " + l.eid());
            if (!row.holder().equals(l.id())) throw new LedgerException(WalletReason.BAD_TXN, "Titulaire différent de celui du blocage : " + l.eid());
            if (row.per() != null ? row.per() != r.per() : row.amount() % r.per() != 0) throw new LedgerException(WalletReason.BAD_TXN, "Mise par siège différente de celle du blocage : " + l.eid());
            long k = row.amount() / r.per();
            if (k < 1 || k > Txn.MAX_SEATS || row.amount() != r.per() * k) throw new LedgerException(WalletReason.BAD_TXN, "Blocage incohérent avec la mise par siège : " + l.eid());
            // le JEU : un blocage fait pour un jeu ne se règle que par le résultat de CE jeu, et le résultat d'un jeu misé (échecs, Quiz) ne règle que des blocages faits pour lui (jamais ceux d'un autre jeu,
            // ni ceux d'avant, posés sans jeu donc sans les règles du jeu : sinon bloquer sans `game` contournerait l'échelle, l'essai et les plafonds)
            if (row.game() != null ? !row.game().equals(r.game()) : WalletPolicyService.GAMES.contains(r.game())) throw new LedgerException(WalletReason.BAD_TXN, "Jeu différent de celui du blocage : " + l.eid());
        }
        // 3. états : un rendu refuse tout ; un règlement par un autre résultat ferme le blocage
        for (Row row : rows.values()) if ("REFUNDED".equals(row.state())) throw recordAfterRefund(r);
        for (Row row : rows.values()) {
            if ("SETTLED".equals(row.state()) && !r.rid().equals(row.settledRid())) throw new LedgerException(WalletReason.ESCROW_CLOSED, "Blocage déjà réglé par un autre résultat : " + row.eid());
        }
        // 4. règles du résultat : ABORT ne déplace rien ; l'utilisé est un nombre entier de mises ; Σ payé = Σ utilisé
        List<Settlement.Escrow> escrows = new ArrayList<>();
        List<Settlement.Line> lines = new ArrayList<>();
        for (Settlement.Line l : r.lines()) {
            Row row = rows.get(l.eid());
            if (r.kind() == Settlement.Kind.ABORT && (l.used() != 0 || l.pay() != 0)) throw new LedgerException(WalletReason.BAD_TXN, "Un abandon ne déplace aucune valeur : " + l.eid());
            if (l.used() % r.per() != 0) throw new LedgerException(WalletReason.BAD_TXN, "Montant utilisé : un nombre entier de mises : " + l.eid());
            if (l.pay() > 0 && l.used() == 0) throw new LedgerException(WalletReason.BAD_TXN, "Aucun siège de ce blocage n'a joué : rien à gagner : " + l.eid());   // F1
            escrows.add(new Settlement.Escrow(l.eid(), row.holder(), (int) (row.amount() / r.per()), row.amount()));
            lines.add(new Settlement.Line(l.eid(), l.id(), row.amount(), l.used(), l.pay()));
        }
        Settlement.check(lines, escrows);
        // jeux misés (échecs, Quiz) : la FORME du résultat est vérifiée avant de payer (défense en profondeur : aux échecs un service compromis ne peut attribuer que les deux mises à l'une des deux TV, ou rendre
        // à chacune la sienne ; au Quiz il ne peut que redistribuer les mises utilisées entre les TV de la table, jamais en créer ni payer une TV qui n'a pas misé)
        WalletPolicyService.GamePolicy gp = policies.game(r.game()).orElse(null);
        if (gp != null) shape(gp, r, rows, lines);
        // frais de plateforme (politique du jeu, 0 au lancement) : prélevés sur la cagnotte d'une partie DÉCISIVE seulement, jamais sur une nulle ni sur une interruption
        long totalUsed = lines.stream().mapToLong(Settlement.Line::used).sum();
        boolean decisive = r.kind() == Settlement.Kind.END && lines.stream().anyMatch(l -> l.pay() > l.used());
        long fee = gp != null && decisive ? totalUsed * gp.feeBp() / 10_000L : 0L;
        List<Settlement.Line> ledgerLines = fee == 0 ? lines : afterFee(lines, fee);
        // M3 : plafonds du règlement (une clé « résultat » compromise ne vide pas le serveur) ; le refus laisse une alerte, le collecteur réessaie ou l'administrateur tranche
        long totalPay = 0;
        for (Settlement.Line l : lines) totalPay += l.pay();
        WalletPolicyService.SettleCaps caps = policies.settleCaps(r.cur());
        if (totalPay > caps.perSettle()) throw capRefusal(r, "règlement de " + totalPay + " " + r.cur() + " au-dessus du plafond par règlement (" + caps.perSettle() + ")");
        long paidToday = jdbc.queryForObject("SELECT COALESCE(SUM(paid), 0) FROM wallet_result WHERE cur = ? AND outcome = 'SETTLED' AND received_at >= ?", Long.class, r.cur().name(),
                Timestamp.from(clock.now().minus(java.time.Duration.ofHours(24))));
        if (paidToday + totalPay > caps.perDay()) throw capRefusal(r, "règlement de " + totalPay + " " + r.cur() + " : le plafond des dernières 24 h (" + caps.perDay() + ", déjà payé " + paidToday + ") serait dépassé");
        // 5. UNE transaction : le grand livre revérifie tout sous verrou (blocages ouverts, conservation) ; un rejeu concurrent y devient « replayed » ; le journal des parties s'écrit DANS cette transaction
        List<GameJournal.Row> journalRows = gp == null ? List.of() : journalRows(r, lines, fee, clock.now());
        try {
            ledger.post(Txn.settle(r.rid(), ledgerLines, r.cur(), fee), "play", null, null, tx -> journal.record(tx, journalRows));
        } catch (LedgerException e) {
            if (e.reason() == WalletReason.ESCROW_CLOSED) {
                for (Settlement.Line l : r.lines()) {
                    Row row = load(l.eid());
                    if (row != null && "REFUNDED".equals(row.state())) throw recordAfterRefund(r);
                }
            }
            throw e;
        }
        remember(r, r.kind() == Settlement.Kind.ABORT ? "ABORT" : "SETTLED", totalPay);
        for (Settlement.Line l : lines) {
            if (l.pay() > 2 * l.used() && l.pay() > caps.alert()) {
                alert("SETTLE_GAIN", r.rid(), "gain anormal : " + l.id() + " gagne " + l.pay() + " " + r.cur() + " pour une mise utilisée de " + l.used() + " (partie " + r.room() + ")");
            }
        }
        return response(r);
    }

    private Row load(String eid) {
        List<Row> l = jdbc.query("SELECT eid, holder, cur, per, k, amount, state, settled_rid, room, game FROM wallet_escrow WHERE eid = ?",
                (rs, i) -> new Row(rs.getString("eid"), rs.getString("holder"), Currency.valueOf(rs.getString("cur")), nullableLong(rs, "per"), nullableLong(rs, "k"),
                        rs.getLong("amount"), rs.getString("state"), rs.getString("settled_rid"), rs.getString("room"), rs.getString("game")), eid);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Au plus 8 TV autour d'une table du Quiz (une ligne par TV dans le résultat). */
    static final int MAX_TABLE_TVS = 8;

    /** La forme d'un résultat selon le jeu : un DUEL aux échecs, une TABLE au Quiz. Un jeu misé connu sans forme est un oubli de programmation : refusé, jamais réglé au hasard. */
    private static void shape(WalletPolicyService.GamePolicy gp, PlayResultKeys.Result r, Map<String, Row> rows, List<Settlement.Line> lines) {
        switch (gp.game()) {
            case "chess" -> duelShape(r, rows, lines);
            case "quiz" -> tableShape(gp, r, rows, lines);
            default -> throw new LedgerException(WalletReason.BAD_TXN, "Jeu sans forme de règlement : " + gp.game());
        }
    }

    /**
     * Forme d'un résultat de TABLE (Quiz) : UNE ligne par TV (une TV n'apparaît qu'une fois : elle ne joue pas contre elle-même), au plus {@link #MAX_TABLE_TVS} TV ; chaque blocage porte de 1 à {@code seats}
     * sièges (sa mise est un nombre entier de mises par siège : déjà imposé plus haut, revérifié ici contre le jeu) ; une partie interrompue ne déplace rien (imposé plus haut), avec une ou plusieurs TV ;
     * une partie TERMINÉE engage au moins DEUX TV distinctes (utilisé > 0) : une TV seule ne joue contre personne et aucun jeton ne circule. Le partage lui-même est celui du service (cagnotte par siège,
     * agrégée par TV) : l'API ne voit pas les points ; elle tient la conservation (Σ payé = Σ utilisé), l'entier de mises par ligne et « pas de gain sans mise utilisée » (contrôles génériques plus haut).
     */
    private static void tableShape(WalletPolicyService.GamePolicy gp, PlayResultKeys.Result r, Map<String, Row> rows, List<Settlement.Line> lines) {
        for (Row row : rows.values()) {
            long k = row.amount() / r.per();
            if (k < 1 || k > gp.seats()) throw new LedgerException(WalletReason.BAD_TXN, "Au Quiz, 1 à " + gp.seats() + " sièges par TV : " + row.eid());
        }
        if (lines.size() > MAX_TABLE_TVS) throw new LedgerException(WalletReason.BAD_TXN, "Une table du Quiz n'a pas plus de " + MAX_TABLE_TVS + " TV");
        Set<String> tvs = new HashSet<>();
        for (Settlement.Line l : lines) if (!tvs.add(l.id())) throw new LedgerException(WalletReason.BAD_TXN, "Une TV ne joue pas contre elle-même");
        if (r.kind() == Settlement.Kind.ABORT) return;
        if (lines.stream().filter(l -> l.used() > 0).count() < 2) {
            throw new LedgerException(WalletReason.BAD_TXN, "Une partie terminée engage au moins deux TV : aucun jeton ne circule dans une partie à une seule TV");
        }
    }

    /**
     * Forme d'un résultat de DUEL (échecs) : une mise par TV (un siège) ; une partie interrompue porte une ou deux TV (rien n'est utilisé) ; une partie terminée exactement deux TV DISTINCTES qui engagent
     * chacune leur mise entière, et la répartition est « les deux mises à l'une », « les deux mises à l'autre » ou « chacune reprend la sienne » : rien d'autre ne s'attribue.
     */
    private static void duelShape(PlayResultKeys.Result r, Map<String, Row> rows, List<Settlement.Line> lines) {
        for (Row row : rows.values()) if (row.amount() != r.per()) throw new LedgerException(WalletReason.BAD_TXN, "Une mise par TV aux échecs : " + row.eid());
        if (lines.size() > 2) throw new LedgerException(WalletReason.BAD_TXN, "Un duel n'a pas plus de deux TV");
        if (lines.size() == 2 && lines.get(0).id().equals(lines.get(1).id())) throw new LedgerException(WalletReason.BAD_TXN, "Une TV ne joue pas contre elle-même");
        if (r.kind() == Settlement.Kind.ABORT) return;
        if (lines.size() != 2) throw new LedgerException(WalletReason.BAD_TXN, "Une partie terminée a exactement deux TV");
        long per = r.per();
        for (Settlement.Line l : lines) if (l.used() != per) throw new LedgerException(WalletReason.BAD_TXN, "Chaque joueur engage sa mise entière : " + l.eid());
        long a = lines.get(0).pay(), b = lines.get(1).pay();
        if (!((a == 2 * per && b == 0) || (a == 0 && b == 2 * per) || (a == per && b == per))) {
            throw new LedgerException(WalletReason.BAD_TXN, "Répartition impossible aux échecs : le gagnant prend les deux mises, ou chacun reprend la sienne");
        }
    }

    /** Les lignes du grand livre après frais : le gagnant (la plus grosse part) reçoit {@code fee} de moins ; Σ payé + frais = Σ utilisé. */
    private static List<Settlement.Line> afterFee(List<Settlement.Line> lines, long fee) {
        int top = 0;
        for (int i = 1; i < lines.size(); i++) if (lines.get(i).pay() > lines.get(top).pay()) top = i;
        Settlement.Line w = lines.get(top);
        if (fee > w.pay()) throw new LedgerException(WalletReason.BAD_TXN, "Frais supérieurs au gain");
        List<Settlement.Line> out = new ArrayList<>(lines);
        out.set(top, new Settlement.Line(w.eid(), w.id(), w.amount(), w.used(), w.pay() - fee));
        return out;
    }

    /**
     * Les lignes du journal d'un résultat : une par TV, avec l'issue, l'adversaire (seulement quand la partie n'a que deux TV) et les frais (prélevés sur la plus grosse part, la même ligne que dans le grand
     * livre). Issue d'une ligne : {@code ABORT} si le résultat est une interruption ; sinon {@code WIN} si la TV reçoit plus que ce qu'elle a engagé, {@code DRAW} si elle reprend exactement sa mise utilisée,
     * {@code LOSS} si elle reçoit moins (aux échecs : gagnant 2 mises, nulle 1 mise, perdant 0).
     */
    private static List<GameJournal.Row> journalRows(PlayResultKeys.Result r, List<Settlement.Line> lines, long fee, java.time.Instant at) {
        int top = 0;
        for (int i = 1; i < lines.size(); i++) if (lines.get(i).pay() > lines.get(top).pay()) top = i;
        List<GameJournal.Row> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Settlement.Line l = lines.get(i);
            GameJournal.Outcome o = r.kind() == Settlement.Kind.ABORT ? GameJournal.Outcome.ABORT : l.pay() > l.used() ? GameJournal.Outcome.WIN : l.pay() == l.used() ? GameJournal.Outcome.DRAW : GameJournal.Outcome.LOSS;
            out.add(new GameJournal.Row(r.rid(), r.room(), r.game(), l.id(), lines.size() == 2 ? lines.get(1 - i).id() : null, r.cur().name(), r.per(), l.used(), l.pay(), i == top ? fee : 0L, o, at));
        }
        return out;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private ApiException capRefusal(PlayResultKeys.Result r, String detail) {
        alert("SETTLE_CAP", r.rid(), detail + " (partie " + r.room() + ")");
        return new ApiException(HttpStatus.CONFLICT, "Règlement refusé : plafond atteint, vérification de l'administrateur requise", List.of("SETTLE_CAP"));
    }

    /** Une entrée d'alerte pour l'administrateur (une seule par genre et par résultat : le collecteur peut réessayer sans la dupliquer) et une ligne de journal. */
    private void alert(String kind, String rid, String detail) {
        log.warn("wallet : ALERTE {} rid={} : {}", kind, rid, detail);
        try {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM wallet_alert WHERE kind = ? AND ref = ?", Long.class, kind, rid) > 0) return;
            jdbc.update("INSERT INTO wallet_alert (at, kind, ref, detail) VALUES (?, ?, ?, ?)", Timestamp.from(clock.now()), kind, rid, detail.length() > 400 ? detail.substring(0, 400) : detail);
        } catch (RuntimeException e) {
            log.error("wallet : alerte non inscrite ({})", e.toString());   // une alerte perdue ne doit pas défaire un règlement exact
        }
    }

    private void remember(PlayResultKeys.Result r, String outcome) { remember(r, outcome, 0); }

    private void remember(PlayResultKeys.Result r, String outcome, long paid) {
        try {
            jdbc.update("INSERT INTO wallet_result (rid, sha, kind, received_at, outcome, cur, paid) VALUES (?, ?, ?, ?, ?, ?, ?)", r.rid(), r.sha(), r.kind().name(), Timestamp.from(clock.now()), outcome,
                    r.cur().name(), paid);
        } catch (DuplicateKeyException e) {
            // un autre fil l'a déjà noté : même résultat
        }
    }

    private ApiException recordAfterRefund(PlayResultKeys.Result r) {
        log.warn("wallet : RESULT_AFTER_REFUND rid={} room={} game={} cur={} : au moins un blocage était déjà rendu, rien n'est réglé pour cette partie", r.rid(), r.room(), r.game(), r.cur());
        remember(r, "AFTER_REFUND");
        return afterRefund(r);
    }

    private static ApiException afterRefund(PlayResultKeys.Result r) {
        return new ApiException(HttpStatus.CONFLICT, "Résultat arrivé après le rendu des mises : rien n'est réglé pour cette partie", List.of("RESULT_AFTER_REFUND"));
    }

    /**
     * La réponse d'un règlement (aussi pour un rejeu) : le résultat tel que le service l'a signé ({@code used}, {@code pay}) et, pour un jeu misé, les FRAIS prélevés ({@code fee}, par ligne et au total,
     * lus du journal) : la TV montre ce qu'elle reçoit vraiment, {@code pay − fee}.
     */
    private Map<String, Object> response(PlayResultKeys.Result r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rid", r.rid());
        out.put("kind", r.kind().name());
        out.put("cur", r.cur().name());
        out.put("game", r.game());
        out.put("status", "SETTLED");
        Map<String, Long> fees = new java.util.HashMap<>();
        long totalFee = 0;
        for (GameJournal.Row j : journal.byResult(r.rid())) { fees.put(j.holder(), j.fee()); totalFee += j.fee(); }
        List<Map<String, Object>> ls = new ArrayList<>();
        for (Settlement.Line l : r.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eid", l.eid());
            m.put("id", l.id());
            m.put("used", l.used());
            m.put("pay", l.pay());
            m.put("fee", fees.getOrDefault(l.id(), 0L));
            ls.add(m);
        }
        out.put("fee", totalFee);
        out.put("lines", ls);
        return out;
    }
}
