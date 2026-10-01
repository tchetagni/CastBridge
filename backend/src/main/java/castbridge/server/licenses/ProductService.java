package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue of bouquets: an identifier, a type (à la carte = permanent / abonnement = while valid), the lots it covers, a
 * duration, and tariff fields that stay EMPTY (price and payment provider are the owner's decisions, out of scope).
 */
@Service
public class ProductService {
    private final JdbcTemplate jdbc;
    private final AuditLog audit;
    private final LicenseProperties props;

    public ProductService(JdbcTemplate jdbc, AuditLog audit, LicenseProperties props) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.props = props;
    }

    public record ProductRow(long id, String productId, String title, String kind, Integer durationDays, String tariffRef, String tariffNote,
                             boolean active, List<String> lots, Instant createdAt) {}

    public record NewProduct(String productId, String title, String kind, Integer durationDays, List<String> lots, String tariffRef, String tariffNote) {}

    public List<ProductRow> list() {
        return jdbc.query("SELECT * FROM lic_product ORDER BY product_id LIMIT 500", (rs, i) -> map(rs));
    }

    public ProductRow get(String productId) {
        List<ProductRow> r = jdbc.query("SELECT * FROM lic_product WHERE product_id = ?", (rs, i) -> map(rs), productId);
        if (r.isEmpty()) throw ApiException.notFound("Bouquet introuvable : " + productId);
        return r.get(0);
    }

    private ProductRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long id = rs.getLong("id");
        List<String> lots = jdbc.queryForList("SELECT lot_id FROM lic_product_lot WHERE product_pk = ? ORDER BY lot_id", String.class, id);
        return new ProductRow(id, rs.getString("product_id"), rs.getString("title"), rs.getString("kind"), (Integer) rs.getObject("duration_days"),
                rs.getString("tariff_ref"), rs.getString("tariff_note"), rs.getBoolean("active"), lots, LicenseService.inst(rs, "created_at"));
    }

    @Transactional
    public ProductRow create(Actor actor, NewProduct n) {
        actor.require(Role.Permission.PRODUCT_WRITE, props.requireTotp());
        String pid = Validate.productId(n.productId());
        String title = Validate.text(n.title(), "Titre", 160, true);
        String kind = n.kind() == null ? "" : n.kind().trim().toUpperCase(java.util.Locale.ROOT);
        if (!kind.equals("A_LA_CARTE") && !kind.equals("ABONNEMENT")) throw ApiException.badRequest("Type de bouquet : A_LA_CARTE ou ABONNEMENT");
        Integer days = n.durationDays();
        if (kind.equals("ABONNEMENT")) Validate.range(days, "Durée de l'abonnement (jours)", 1, 3660);
        else if (days != null) Validate.range(days, "Durée (jours)", 1, 3660);
        List<String> lots = new ArrayList<>(new TreeSet<>(n.lots() == null ? List.<String>of() : n.lots().stream().map(Validate::lotId).toList()));
        if (lots.size() > 300) throw ApiException.badRequest("300 lots au maximum par bouquet");
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                var ps = con.prepareStatement("INSERT INTO lic_product (product_id, title, kind, duration_days, tariff_ref, tariff_note, active, created_at) VALUES (?,?,?,?,?,?,TRUE,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, pid);
                ps.setString(2, title);
                ps.setString(3, kind);
                ps.setObject(4, days);
                ps.setString(5, Validate.text(n.tariffRef(), "Référence tarifaire", 64, false));
                ps.setString(6, Validate.text(n.tariffNote(), "Note tarifaire", 200, false));
                ps.setTimestamp(7, LicenseService.ts(Instant.now()));
                return ps;
            }, keys);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("Ce bouquet existe déjà : " + pid);
        }
        long id = keys.getKey().longValue();
        for (String lot : lots) jdbc.update("INSERT INTO lic_product_lot (product_pk, lot_id) VALUES (?,?)", id, lot);
        audit.record(actor, "PRODUCT_CREATE", "PRODUCT", pid, null, java.util.Map.of("kind", kind, "lots", lots.size()));
        return get(pid);
    }

    /** Replaces title and lots, or switches the bouquet off (a bouquet already in a licence stays there). */
    @Transactional
    public ProductRow update(Actor actor, String productId, String title, List<String> lots, Boolean active, String reason) {
        actor.require(Role.Permission.PRODUCT_WRITE, props.requireTotp());
        ProductRow p = get(productId);
        if (Boolean.FALSE.equals(active) && p.active()) Validate.reason(reason);
        if (title != null) jdbc.update("UPDATE lic_product SET title = ? WHERE id = ?", Validate.text(title, "Titre", 160, true), p.id());
        if (active != null) jdbc.update("UPDATE lic_product SET active = ? WHERE id = ?", active, p.id());
        if (lots != null) {
            List<String> clean = new TreeSet<>(lots.stream().map(Validate::lotId).toList()).stream().toList();
            if (clean.size() > 300) throw ApiException.badRequest("300 lots au maximum par bouquet");
            jdbc.update("DELETE FROM lic_product_lot WHERE product_pk = ?", p.id());
            for (String lot : clean) jdbc.update("INSERT INTO lic_product_lot (product_pk, lot_id) VALUES (?,?)", p.id(), lot);
        }
        audit.record(actor, "PRODUCT_UPDATE", "PRODUCT", productId, Validate.text(reason, "Motif", 500, false), null);
        return get(productId);
    }
}
