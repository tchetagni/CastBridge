package castbridge.server.wallet.ops;

import castbridge.server.wallet.JdbcLedger;
import castbridge.server.wallet.WalletModuleConfig;
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
 */
@Service
@WalletModuleConfig.Enabled
public class SettleService {
    private static final Logger log = LoggerFactory.getLogger(SettleService.class);
    private final PlayResultKeys keys;
    private final JdbcLedger ledger;
    private final JdbcTemplate jdbc;
    private final WalletModuleConfig.WalletClock clock;

    public SettleService(PlayResultKeys keys, JdbcLedger ledger, JdbcTemplate jdbc, WalletModuleConfig.WalletClock clock) {
        this.keys = keys;
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private record Row(String eid, String holder, Currency cur, Long per, Long k, long amount, String state, String settledRid) {}

    public Map<String, Object> settle(String token) {
        if (!keys.configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Règlement indisponible : aucune clé de service de jeu configurée");
        PlayResultKeys.Result r = keys.verify(token);
        // 1. rejeu : même rid et même contenu = même réponse ; même rid et autre contenu = conflit
        List<String[]> prior = jdbc.query("SELECT sha, outcome FROM wallet_result WHERE rid = ?", (rs, i) -> new String[] {rs.getString("sha"), rs.getString("outcome")}, r.rid());
        if (!prior.isEmpty()) {
            if (!prior.get(0)[0].equals(r.sha())) throw new LedgerException(WalletReason.IDEM_CONFLICT, "Ce résultat existe déjà avec un autre contenu : " + r.rid());
            if ("AFTER_REFUND".equals(prior.get(0)[1])) throw afterRefund(r);
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
            if (!row.holder().equals(l.id())) throw new LedgerException(WalletReason.BAD_TXN, "Titulaire différent de celui du blocage : " + l.eid());
            if (row.per() != null ? row.per() != r.per() : row.amount() % r.per() != 0) throw new LedgerException(WalletReason.BAD_TXN, "Mise par siège différente de celle du blocage : " + l.eid());
            long k = row.amount() / r.per();
            if (k < 1 || k > Txn.MAX_SEATS || row.amount() != r.per() * k) throw new LedgerException(WalletReason.BAD_TXN, "Blocage incohérent avec la mise par siège : " + l.eid());
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
            escrows.add(new Settlement.Escrow(l.eid(), row.holder(), (int) (row.amount() / r.per()), row.amount()));
            lines.add(new Settlement.Line(l.eid(), l.id(), row.amount(), l.used(), l.pay()));
        }
        Settlement.check(lines, escrows);
        // 5. UNE transaction : le grand livre revérifie tout sous verrou (blocages ouverts, conservation) ; un rejeu concurrent y devient « replayed »
        try {
            ledger.post(Txn.settle(r.rid(), lines, r.cur()), "play", null, null);
        } catch (LedgerException e) {
            if (e.reason() == WalletReason.ESCROW_CLOSED) {
                for (Settlement.Line l : r.lines()) {
                    Row row = load(l.eid());
                    if (row != null && "REFUNDED".equals(row.state())) throw recordAfterRefund(r);
                }
            }
            throw e;
        }
        remember(r, r.kind() == Settlement.Kind.ABORT ? "ABORT" : "SETTLED");
        return response(r);
    }

    private Row load(String eid) {
        List<Row> l = jdbc.query("SELECT eid, holder, cur, per, k, amount, state, settled_rid FROM wallet_escrow WHERE eid = ?",
                (rs, i) -> new Row(rs.getString("eid"), rs.getString("holder"), Currency.valueOf(rs.getString("cur")), nullableLong(rs, "per"), nullableLong(rs, "k"),
                        rs.getLong("amount"), rs.getString("state"), rs.getString("settled_rid")), eid);
        return l.isEmpty() ? null : l.get(0);
    }

    private static Long nullableLong(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private void remember(PlayResultKeys.Result r, String outcome) {
        try {
            jdbc.update("INSERT INTO wallet_result (rid, sha, kind, received_at, outcome) VALUES (?, ?, ?, ?, ?)", r.rid(), r.sha(), r.kind().name(), Timestamp.from(clock.now()), outcome);
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

    private static Map<String, Object> response(PlayResultKeys.Result r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rid", r.rid());
        out.put("kind", r.kind().name());
        out.put("cur", r.cur().name());
        out.put("status", "SETTLED");
        List<Map<String, Object>> ls = new ArrayList<>();
        for (Settlement.Line l : r.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eid", l.eid());
            m.put("id", l.id());
            m.put("used", l.used());
            m.put("pay", l.pay());
            ls.add(m);
        }
        out.put("lines", ls);
        return out;
    }
}
