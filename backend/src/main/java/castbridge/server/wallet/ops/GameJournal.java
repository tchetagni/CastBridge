package castbridge.server.wallet.ops;

import castbridge.server.wallet.WalletModuleConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Le journal des parties avec mise (W22 § 5, games-G2) : une ligne par TV et par résultat réglé, écrite DANS la transaction du règlement (la garde de {@code JdbcLedger.post}) : jamais un règlement
 * sans sa ligne, jamais une ligne sans règlement. Il sert (1) aux plafonds de parties GAGNÉES par identité, relus au blocage d'une mise ; (2) à la surveillance des gains entre deux mêmes identités
 * (R-E9 : alerte, jamais de blocage automatique) ; (3) à l'audit (qui a joué qui, pour quelle mise, avec quelle issue). Il ne garde ni coup ni partie : le service de jeu les a, pas l'API. Aucune
 * écriture ailleurs que dans {@code wallet_game_log}.
 */
@Service
@WalletModuleConfig.Enabled
public class GameJournal {
    private final JdbcTemplate jdbc;

    public GameJournal(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Issue d'une partie pour une TV : gagnée, perdue, nulle ou interrompue (mise rendue en entier). */
    public enum Outcome { WIN, LOSS, DRAW, ABORT }

    /** Une ligne du journal. {@code pay} : ce que le résultat du service attribue (avant frais) ; {@code fee} : frais prélevés sur ce titulaire. */
    public record Row(String rid, String room, String game, String holder, String opponent, String cur, long per, long used, long pay, long fee, Outcome outcome, Instant at) {}

    /**
     * Inscrit les lignes d'un règlement avec le {@code JdbcTemplate} de la TRANSACTION de ce règlement. Idempotent par (rid, titulaire) : un rejeu qui repasserait ici n'ajoute rien.
     */
    public void record(JdbcTemplate tx, List<Row> rows) {
        for (Row r : rows) {
            Integer already = tx.queryForObject("SELECT COUNT(*) FROM wallet_game_log WHERE rid = ? AND holder = ?", Integer.class, r.rid(), r.holder());
            if (already != null && already > 0) continue;
            tx.update("INSERT INTO wallet_game_log (rid, room, game, holder, opponent, cur, per, used, pay, fee, outcome, settled_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    r.rid(), r.room(), r.game(), r.holder(), r.opponent(), r.cur(), r.per(), r.used(), r.pay(), r.fee(), r.outcome().name(), Timestamp.from(r.at()));
        }
    }

    /** Parties GAGNÉES (issue WIN) de cette identité, pour ce jeu, réglées depuis {@code since} (compris). */
    public int wins(String holder, String game, Instant since) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_game_log WHERE holder = ? AND game = ? AND outcome = 'WIN' AND settled_at >= ?", Integer.class, holder, game, Timestamp.from(since));
        return n == null ? 0 : n;
    }

    /** Les lignes d'un résultat (réponse de règlement, audit). */
    public List<Row> byResult(String rid) {
        return jdbc.query("SELECT rid, room, game, holder, opponent, cur, per, used, pay, fee, outcome, settled_at FROM wallet_game_log WHERE rid = ? ORDER BY id", (rs, i) -> row(rs), rid);
    }

    /** Les dernières parties (plus récentes d'abord), toutes identités ou celles d'un titulaire ; au plus {@code limit} (≤ 200). */
    public List<Row> recent(String game, String holder, int limit) {
        int n = Math.max(1, Math.min(200, limit));
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT rid, room, game, holder, opponent, cur, per, used, pay, fee, outcome, settled_at FROM wallet_game_log WHERE 1 = 1");
        if (game != null) { sql.append(" AND game = ?"); args.add(game); }
        if (holder != null) { sql.append(" AND holder = ?"); args.add(holder); }
        sql.append(" ORDER BY id DESC LIMIT ").append(n);
        return jdbc.query(sql.toString(), (rs, i) -> row(rs), args.toArray());
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getString("rid"), rs.getString("room"), rs.getString("game"), rs.getString("holder"), rs.getString("opponent"), rs.getString("cur"), rs.getLong("per"), rs.getLong("used"),
                rs.getLong("pay"), rs.getLong("fee"), Outcome.valueOf(rs.getString("outcome")), rs.getTimestamp("settled_at").toInstant());
    }
}
