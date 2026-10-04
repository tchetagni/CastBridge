package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.wallet.Acts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Correctifs de l'audit Opus de w22-05, blocage : E1 (droits de mise lus de la licence vivante, jamais de l'édition mémorisée), M1 (cbe1 signé depuis la base), M4 (blocage lié à la
 * salle). Les tests p1 à p3 sont les preuves de l'audit.
 */
class AuditW2205EscrowTest extends OpsTestBase {

    private Reply escrowWithoutActivations(Tv tv, String cur, long per, int k, String idem) throws Exception {
        return postJson(tv.auth(), "/api/v1/wallet/escrow", req(tv).put("cur", cur).put("per", per).put("k", k).put("idem", idem));
    }

    private JsonNode flags(String cbw1) throws Exception {
        JsonNode payload = json.readTree(Base64.getUrlDecoder().decode(cbw1.split("\\.")[1]));
        return payload.get("flags");
    }

    private void revokeAndRelease(Tv tv) {
        jdbc.update("UPDATE lic_license SET state='REVOKED' WHERE license_id=?", "lic-" + tv.code().toLowerCase());
        jdbc.update("UPDATE lic_seat SET state='RELEASED', slot_no=NULL WHERE device_code=?", tv.code());
    }

    /** p1 : une licence illimitée révoquée (poste libéré) ne mise plus de MBOKO, avec ou sans activations jointes. */
    @Test
    void p1_revokedUnlimitedLicenseCannotStakeMboko() throws Exception {
        Tv tv = productionTv(null, 14, "ACTIVE");
        adminGrant(tv.code(), "MBOKO", 100);
        assertEquals(200, escrow(tv, "MBOKO", 1, 1, "rev-0000").status(), "avant la révocation : permis");
        revokeAndRelease(tv);
        Reply bare = escrowWithoutActivations(tv, "MBOKO", 10, 2, "rev-0001");
        assertEquals(409, bare.status(), bare.json().toString());
        assertEquals("ACTIVATE", bare.reason(), "refusé pour le droit, pas pour le solde");
        assertEquals(409, escrow(tv, "MBOKO", 10, 2, "rev-0002").status(), "avec activations aussi");
        assertEquals(409, escrow(tv, "NDEM", 10, 2, "rev-0003").status());
    }

    /** p2 : un essai échu ne mise plus de NDEM, même sans resynchronisation, avec ou sans activations. */
    @Test
    void p2_expiredTrialWithoutSyncMustNotStake() throws Exception {
        Tv tv = trialTv();
        clock.freezeAt(T0.plus(Duration.ofDays(40)));
        Reply bare = escrowWithoutActivations(tv, "NDEM", 5, 1, "exp-0001");
        assertEquals(409, bare.status(), bare.json().toString());
        assertEquals("ACTIVATE", bare.reason());
        assertEquals(409, escrow(tv, "NDEM", 5, 1, "exp-0002").status());
    }

    /** E1 : le cbw1 signé ne dit plus « mise MBOKO permise » pour une licence illimitée révoquée, ni « mise NDEM » pour un essai échu. */
    @Test
    void e1_theSignedSnapshotFollowsTheLiveLicenseAndTheTrialEnd() throws Exception {
        Tv unlimited = productionTv(null, 14, "ACTIVE");
        adminGrant(unlimited.code(), "NDEM", 10);
        JsonNode before = flags(convert(unlimited, "N2M", 1, "cv-e1-0001").json().get("snapshot").asText());
        assertTrue(before.get("stakesM").asBoolean(), "licence active : mise MBOKO permise");
        revokeAndRelease(unlimited);
        JsonNode after = flags(convert(unlimited, "N2M", 1, "cv-e1-0002").json().get("snapshot").asText());
        assertFalse(after.get("stakesM").asBoolean(), "licence révoquée : plus de mise MBOKO dans le cbw1");
        Tv trial = trialTv();
        clock.freezeAt(T0.plus(Duration.ofDays(40)));
        adminGrant(trial.code(), "MBOKO", 5);
        JsonNode ended = flags(convert(trial, "M2N", 1, "cv-e1-0003").json().get("snapshot").asText());
        assertFalse(ended.get("stakesN").asBoolean(), "essai échu : plus de mise NDEM dans le cbw1");
    }

    /** E1 : le droit « super » ne se reconnaît qu'à la clé mémorisée, jamais à une édition mémorisée sans licence (règle du cœur : super mise du NDEM, pas du MBOKO ; dons manuels, D-W22-4). */
    @Test
    void e1_superRightComesFromTheStoredSuperKeyOnly() throws Exception {
        String auth = registerTv();
        Acts.Tv hw = Acts.Tv.random();
        Tv sup = new Tv(auth, hw, List.of(Acts.production(ISSUER, hw, NOW, List.of("super|tout|" + NOW))), false);
        sync(sup);
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_identity WHERE holder = ? AND super_key = TRUE", sup.code()));
        adminGrant(sup.code(), "MBOKO", 10);
        JsonNode with = flags(convert(sup, "M2N", 1, "cv-sup-0001").json().get("snapshot").asText());
        assertTrue(with.get("stakesN").asBoolean(), "droit super : mise NDEM");
        assertFalse(with.get("stakesM").asBoolean(), "règle du cœur : pas de mise MBOKO pour « super »");
        jdbc.update("UPDATE wallet_identity SET super_key = FALSE, edition = 'UNLIMITED' WHERE holder = ?", sup.code());
        JsonNode without = flags(convert(sup, "M2N", 1, "cv-sup-0002").json().get("snapshot").asText());
        assertFalse(without.get("stakesN").asBoolean(), "UNLIMITED mémorisé sans super_key : aucun droit");
        assertFalse(without.get("stakesM").asBoolean());
    }

    /** Mutation 4 : le chemin « super » (clé super lue dans cbx1) permet la mise NDEM avec les activations jointes, jamais sans, jamais MBOKO. */
    @Test
    void aSuperKeyTvMayStakeNdemWithItsActivationsOnly() throws Exception {
        String auth = registerTv();
        Acts.Tv hw = Acts.Tv.random();
        Tv sup = new Tv(auth, hw, List.of(Acts.production(ISSUER, hw, NOW, List.of("super|tout|" + NOW))), false);
        sync(sup);
        adminGrant(sup.code(), "NDEM", 100);
        adminGrant(sup.code(), "MBOKO", 10);
        Reply r = escrow(sup, "NDEM", 2, 1, "sup-0001");
        assertEquals(200, r.status(), r.json().toString());
        assertEquals(409, escrowWithoutActivations(sup, "NDEM", 2, 1, "sup-0002").status(), "sans activations : refusé");
        assertEquals(409, escrow(sup, "MBOKO", 2, 1, "sup-0003").status(), "règle du cœur : super ne mise pas de MBOKO");
    }

    /** p3 / M1 : même clé, même montant, autre (per, k) : IDEM_CONFLICT, jamais un second cbe1 qui contredit le blocage. */
    @Test
    void p3_sameIdemSameAmountOtherPerAndKIsAConflict() throws Exception {
        Tv tv = trialTv();
        Reply a = escrow(tv, "NDEM", 10, 2, "m1-0001");
        assertEquals(200, a.status());
        Reply b = escrow(tv, "NDEM", 20, 1, "m1-0001");
        assertEquals(409, b.status(), "reçu " + b.status() + " " + b.json());
        assertEquals("IDEM_CONFLICT", b.reason());
        Reply again = escrow(tv, "NDEM", 10, 2, "m1-0001");
        assertEquals(a.json().get("cbe1").asText(), again.json().get("cbe1").asText(), "le rejeu fidèle rend le même cbe1");
        assertEquals(1, newTxns("ESCROW_LOCK"));
    }

    /** M1 : le cbe1 rejoué est signé depuis per et k LUS en base, même quand la requête rejouée est la même. */
    @Test
    void m1_replayedCbe1IsSignedFromTheDatabase() throws Exception {
        Tv tv = trialTv();
        Reply a = escrow(tv, "NDEM", 10, 2, "m1-0002");
        jdbc.update("UPDATE wallet_escrow SET per = NULL, k = NULL WHERE eid = ?", a.json().get("eid").asText());
        Reply again = escrow(tv, "NDEM", 10, 2, "m1-0002");
        assertEquals(200, again.status(), again.json().toString());
        JsonNode c = verifyCbe1(json, again.json().get("cbe1").asText());
        assertEquals(10, c.get("per").asLong());
        assertEquals(2, c.get("k").asInt());
        assertEquals(10L, count("SELECT per FROM wallet_escrow WHERE eid = ?", a.json().get("eid").asText()), "per et k sont réinscrits depuis le blocage");
    }

    private String resultKid() {
        return LicenseKeyring.kidOf(rawPublicBytes(RESULT));
    }

    private String settleRoom(String rid, String room, String eid, Tv tv, long used, long pay) {
        return sign(RESULT, "{\"kid\":\"" + resultKid() + "\",\"rid\":\"" + rid + "\",\"room\":\"" + room + "\",\"game\":\"quiz\",\"cur\":\"NDEM\",\"per\":10,\"kind\":\"END\",\"at\":" + NOW + ",\"lines\":[[\""
                + eid + "\",\"" + tv.code() + "\"," + used + "," + pay + "]]}");
    }

    private Reply escrowRoom(Tv tv, String idem, String room) throws Exception {
        ObjectNode b = req(tv).put("cur", "NDEM").put("per", 10).put("k", 1).put("idem", idem).put("room", room);
        b.putArray("activations").add(tv.activations().get(0));
        return postJson(tv.auth(), "/api/v1/wallet/escrow", b);
    }

    /** M4 : la salle déclarée est gardée par l'API (le cbe1 ne change pas) ; la même clé pour une autre salle est refusée ; le règlement doit citer la salle du blocage. */
    @Test
    void m4_cbe1CarriesTheRoomAndTheSettlementMustNameIt() throws Exception {
        Tv tv = trialTv();
        Reply a = escrowRoom(tv, "m4-0001", "ROOM1");
        assertEquals(200, a.status(), a.json().toString());
        assertFalse(verifyCbe1(json, a.json().get("cbe1").asText()).has("room"), "le format signé de cbe1 ne change pas (parité Kotlin, lecture stricte des clés)");
        assertEquals("ROOM1", jdbc.queryForObject("SELECT room FROM wallet_escrow WHERE eid = ?", String.class, a.json().get("eid").asText()), "la salle est gardée par l'API");
        Reply other = escrowRoom(tv, "m4-0001", "ROOM2");
        assertEquals(409, other.status());
        assertEquals("IDEM_CONFLICT", other.reason());
        String eid = a.json().get("eid").asText();
        Reply wrong = settle(settleRoom(rid(911), "ROOM2", eid, tv, 10, 10));
        assertEquals(400, wrong.status(), wrong.json().toString());
        assertEquals(0, newTxns("SETTLE"));
        Reply good = settle(settleRoom(rid(912), "ROOM1", eid, tv, 10, 10));
        assertEquals(200, good.status(), good.json().toString());
    }

    /** M4 : sans salle déclarée, le comportement d'avant est conservé (règlement accepté). */
    @Test
    void m4_aStakeWithoutRoomStillSettles() throws Exception {
        Tv tv = trialTv();
        Reply a = escrow(tv, "NDEM", 10, 1, "m4-0002");
        assertFalse(verifyCbe1(json, a.json().get("cbe1").asText()).has("room"));
        assertEquals(200, settle(settleRoom(rid(913), "ROOM9", a.json().get("eid").asText(), tv, 10, 10)).status());
    }
}
