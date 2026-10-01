package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The licence vectors of tools/activation/test-vectors.json (the same registries replayed by the Kotlin core and the Python reference) played through the
 * server's own import (policy AUTO = the format's resolution): the seats, the transfers, the merged duplicates, the rejections, the warnings, the revoked seats
 * and the re-activation plans must be those the vectors expect.
 */
class LicenceVectorsTest extends LicenseTestBase {

    private void reset() {
        for (String t : List.of("lic_conflict", "lic_event", "lic_ledger_import", "lic_seat_alias", "lic_revocation", "lic_transfer", "lic_issuance", "lic_seat", "lic_license_product", "lic_license", "lic_client")) {
            jdbc.update("delete from " + t);
        }
    }

    private static Map<DeviceIdentity.Factor, String> fp(JsonNode device) {
        Map<DeviceIdentity.Factor, String> m = new EnumMap<>(DeviceIdentity.Factor.class);
        device.get("fingerprints").fields().forEachRemaining(e -> m.put(DeviceIdentity.Factor.valueOf(e.getKey()), e.getValue().asText()));
        return m;
    }

    @Test
    void everyLicenceVectorGivesTheSameStateThroughTheServerImport() throws Exception {
        JsonNode vectors = json.readTree(Files.readString(Path.of("..", "tools", "activation", "test-vectors.json")));
        Map<String, JsonNode> devices = new HashMap<>();
        vectors.get("devices").forEach(d -> devices.put(d.get("name").asText(), d));
        int n = 0;
        for (JsonNode c : vectors.get("cases")) {
            if (!c.get("type").asText().equals("licence")) continue;
            n++;
            String id = c.get("id").asText();
            reset();
            ObjectNode root = json.createObjectNode();
            root.put("format", LedgerService.FORMAT);
            ArrayNode events = root.putArray("events");
            c.get("events").forEach(events::add);
            LedgerService.ImportReport report = ledger.importLedger(OWNER, json.writeValueAsBytes(root), false, true);
            JsonNode e = c.get("expect");

            List<String> seats = jdbc.queryForList("select concat(l.license_id, '|', s.seat_id) from lic_seat s join lic_license l on l.id = s.license_pk where s.state = 'ACTIVE' order by 1", String.class);
            List<String> expectedSeats = new ArrayList<>();
            e.get("seats").forEach(x -> expectedSeats.add(x.asText()));
            assertThat(seats).as(id + " : postes").isEqualTo(expectedSeats.stream().sorted().toList());

            Map<String, Integer> used = new TreeMap<>();
            jdbc.query("select l.license_id, count(s.id) as n from lic_license l left join lic_seat s on s.license_pk = l.id and s.state = 'ACTIVE' group by l.license_id", rs -> {
                used.put(rs.getString(1), rs.getInt(2));
            });
            Map<String, Integer> expectedUsed = new TreeMap<>();
            e.get("used").fields().forEachRemaining(x -> expectedUsed.put(x.getKey(), x.getValue().asInt()));
            assertThat(used).as(id + " : postes utilisés par licence").isEqualTo(expectedUsed);

            assertThat(jdbc.queryForObject("select count(*) from lic_transfer where accepted = TRUE", Integer.class)).as(id + " : transferts").isEqualTo(e.get("transfers").asInt());
            assertThat(jdbc.queryForObject("select count(*) from lic_seat_alias", Integer.class)).as(id + " : doublons de matériel fusionnés").isEqualTo(e.get("duplicates").asInt());
            List<String> rejected = new ArrayList<>(report.rejections().stream().map(LedgerService.Rejection::reason).toList());
            java.util.Collections.sort(rejected);
            List<String> expectedRejected = new ArrayList<>();
            e.get("rejected").forEach(x -> expectedRejected.add(x.asText()));
            assertThat(rejected).as(id + " : événements refusés").isEqualTo(expectedRejected);
            assertThat(report.warnings()).as(id + " : avertissements").hasSize(e.get("warnings").asInt());
            assertThat(report.conflicts()).as(id + " : la politique auto ne laisse aucun conflit").isEmpty();

            Map<String, Long> revoked = new TreeMap<>();
            jdbc.query("select license_id, seat_id, max(revoked_at) as at from lic_revocation where seat_id is not null group by license_id, seat_id", rs -> {
                revoked.put(rs.getString(1) + "|" + rs.getString(2), rs.getTimestamp(3).getTime());
            });
            Map<String, Long> expectedRevoked = new TreeMap<>();
            e.get("revokedSeats").fields().forEachRemaining(x -> expectedRevoked.put(x.getKey(), x.getValue().asLong()));
            assertThat(revoked).as(id + " : postes révoqués").isEqualTo(expectedRevoked);

            // the re-activation plans: same hardware = same seat, no seat consumed; else a new seat if one is left; else a refusal
            for (JsonNode p : c.get("plans")) {
                JsonNode pe = p.get("expect");
                String lic = p.get("license").asText();
                var rows = jdbc.queryForList("select id from lic_license where license_id = ?", Long.class, lic);
                String got;
                if (rows.isEmpty()) {
                    got = "refused:UNKNOWN_LICENSE";
                } else {
                    var l = licenses.get(lic);
                    var dev = devices.get(p.get("device").asText());
                    var request = new DeviceIdentity.Request(fp(dev), DeviceIdentity.code(fp(dev)), DeviceIdentity.kFor(fp(dev).size()));
                    var match = licenses.findMatchingSeat(l, p.get("subject").asText(), request);
                    int left = l.seatsAllowed() - l.seatsUsed();
                    if (match != null) got = "reuse:" + match.seatId();
                    else if (left <= 0) got = "refused:NO_SEAT_LEFT";
                    else got = "new:" + WireActivation.defaultSeat(lic, fp(dev)) + ":" + (left - 1);
                }
                String want = switch (pe.get("plan").asText()) {
                    case "reuse" -> "reuse:" + pe.get("seat").asText();
                    case "new" -> "new:" + pe.get("seat").asText() + ":" + pe.get("left").asInt();
                    default -> "refused:" + pe.get("reason").asText();
                };
                assertThat(got).as(id + " : plan de ré-activation de " + p.get("device").asText()).isEqualTo(want);
            }
        }
        assertThat(n).isEqualTo(10);
    }
}
