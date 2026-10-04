package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import castbridge.server.licenses.ReportedActivationRegistrar.Presented;
import castbridge.server.licenses.ReportedActivationRegistrar.Via;
import castbridge.server.wallet.Acts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Propriété (conception W23-B § 3.6) : la notification d'une activation, le registre de l'outil (événements {@code license} et {@code issue}) et un second jeton du même poste donnent le MÊME
 * état {@code lic_*} dans tous les ordres, avec des rejeux. 200 ordres tirés au hasard (graine fixe), une licence neuve par ordre ; aucune double licence, aucun double poste, aucune double
 * émission. Sont exclus de la comparaison ce qui dépend légitimement de l'origine ou de l'horloge : {@code created_by}, le client, les horodatages d'observation et le numéro de version.
 */
class RegistrarOrderTest extends RegistrarTestBase {
    private static final String KEY = rawPublic(pair());

    private String canonical(String lic, Acts.Tv tv, String nonceA, String nonceB) {
        Map<String, Object> l = jdbc.queryForMap("SELECT id, seats_allowed, kind, state, transfer_cap, start_at, end_at FROM lic_license WHERE license_id = ?", lic);
        long pk = ((Number) l.get("id")).longValue();
        StringBuilder sb = new StringBuilder();
        sb.append("licence ").append(l.get("seats_allowed")).append('|').append(l.get("kind")).append('|').append(l.get("state")).append('|').append(l.get("transfer_cap")).append('|')
                .append(castbridge.server.common.Times.ms(l.get("start_at"))).append('|').append(castbridge.server.common.Times.ms(l.get("end_at"))).append('\n');
        for (Map<String, Object> s : jdbc.queryForList("SELECT seat_id, subject, state, slot_no, k, factors FROM lic_seat WHERE license_pk = ? ORDER BY seat_id", pk)) {
            // l'identifiant du poste et les empreintes sont propres à chaque tirage : on vérifie qu'ils sont ceux du jeton (poste par défaut, matériel de la TV)
            boolean seatOk = WireActivation.defaultSeat(lic, tv.factors()).equals(s.get("seat_id"));
            boolean factorsOk = new DeviceIdentity.Request(tv.factors(), tv.code(), DeviceIdentity.kFor(tv.factors().size())).factorsText().equals(s.get("factors"));
            sb.append("poste seatOk=").append(seatOk).append(" factorsOk=").append(factorsOk).append('|').append(s.get("subject")).append('|').append(s.get("state")).append('|').append(s.get("slot_no"))
                    .append('|').append(s.get("k")).append('\n');
        }
        sb.append("alias ").append(count("SELECT COUNT(*) FROM lic_seat_alias WHERE license_pk = ?", pk)).append('\n');
        Map<String, String> issuances = new TreeMap<>();
        for (Map<String, Object> i : jdbc.queryForList("SELECT nonce, kind, subject, source, kid FROM lic_issuance WHERE license_pk = ?", pk)) {
            String label = nonceA.equals(i.get("nonce")) ? "A" : nonceB.equals(i.get("nonce")) ? "B" : "?";
            issuances.put(label, i.get("kind") + "|" + i.get("subject") + "|" + i.get("source") + "|" + i.get("kid"));
        }
        sb.append("émissions ").append(issuances);
        return sb.toString();
    }

    @Test
    void theSameStateInEveryOrderOfNoticesRegistryAndReplays() throws Exception {
        Random rnd = new Random(20261004L);
        String expected = null;
        java.util.Set<String> orders = new java.util.HashSet<>();
        for (int it = 0; it < 200; it++) {
            jdbc.update("DELETE FROM lic_registration");
            Acts.Tv tv = tv();
            String lic = licenseId();
            long issuedA = NOW - 2 * HOUR, issuedB = NOW - HOUR;
            String nonceA = nonce16(), nonceB = nonce16();
            String seat = WireActivation.defaultSeat(lic, tv.factors());
            // même début de droit pour les deux clés (une renouvelée plus longue que l'autre) : le début de la licence ne dépend donc pas de l'ordre
            String tokA = production(ISSUER, tv, lic, null, issuedA, List.of("usage|duree|" + issuedA + "|" + (issuedA + 90 * DAY)), nonceA);
            String tokB = production(ISSUER, tv, lic, null, issuedB, List.of("usage|duree|" + issuedA + "|" + (issuedA + 120 * DAY)), nonceB);
            var events = List.of(licenseEvent(ISSUER, issuedA - 1000, lic, 1), issueEvent(ISSUER, issuedA, lic, seat, tv, nonceA));
            List<String> ops = new ArrayList<>(List.of("A", "A", "B", "B", "REG", "REG"));
            Collections.shuffle(ops, rnd);
            orders.add(String.join("", ops));
            for (String op : ops) {
                switch (op) {
                    case "A" -> assertNotNull(registrar.register(new Presented(tokA, tv.code(), rawPublic(installOf(tv)), true), Via.WALLET, T0));
                    case "B" -> assertNotNull(registrar.register(new Presented(tokB, tv.code(), rawPublic(installOf(tv)), true), Via.REPORT, T0));
                    default -> importRegistry(events);
                }
            }
            String state = canonical(lic, tv, nonceA, nonceB);
            assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic), "aucune double licence : " + ops);
            assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ?", lic), "aucun double poste : " + ops);
            assertEquals(2, count("SELECT COUNT(*) FROM lic_issuance i JOIN lic_license l ON l.id = i.license_pk WHERE l.license_id = ?", lic), "aucune double émission : " + ops);
            if (expected == null) expected = state;
            assertEquals(expected, state, "ordre " + ops);
        }
        assertEquals(true, orders.size() > 20, "assez d'ordres distincts : " + orders.size());
        // et l'état attendu est bien celui d'une licence de 120 jours depuis le début du droit, sans fin illimitée à tort
        assertEquals(true, expected.contains("|" + (NOW - 2 * HOUR) + "|" + (NOW - 2 * HOUR + 120 * DAY) + "\n"), expected);
    }

    @Test
    void anUnlimitedKeyKnownForTheLicenceMakesItUnlimitedInEveryOrder() {
        String expected = null;
        Random rnd = new Random(7L);
        for (int it = 0; it < 24; it++) {
            jdbc.update("DELETE FROM lic_registration");
            Acts.Tv tv = tv();
            String lic = licenseId();
            long issued = NOW - 2 * HOUR;
            String nonceA = nonce16(), nonceB = nonce16();
            String tokA = production(ISSUER, tv, lic, null, issued, List.of("usage|duree|" + issued + "|" + (issued + 90 * DAY)), nonceA);
            String tokB = production(ISSUER, tv, lic, null, issued, List.of(), nonceB);   // illimitée, même début
            List<String> ops = new ArrayList<>(List.of("A", "B", "A", "B"));
            Collections.shuffle(ops, rnd);
            for (String op : ops) registrar.register(new Presented(op.equals("A") ? tokA : tokB, tv.code(), rawPublic(installOf(tv)), true), Via.WALLET, T0);
            String state = canonical(lic, tv, nonceA, nonceB);
            if (expected == null) expected = state;
            assertEquals(expected, state, "ordre " + ops);
        }
        assertEquals(true, expected.startsWith("licence 1|PAID|ACTIVE|0|") && expected.contains("|null\n"), expected);
    }
}
