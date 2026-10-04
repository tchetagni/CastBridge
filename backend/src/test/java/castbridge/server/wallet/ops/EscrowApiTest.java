package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.Currency;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** {@code POST /api/v1/wallet/escrow} : règles d'édition (LICENCE pour MBOKO), bornes, interrupteurs, idempotence, liaison d'appareil, cbe1 signé. */
class EscrowApiTest extends OpsTestBase {

    @Test
    void trialTvStakesNdemAndGetsAVerifiableCbe1() throws Exception {
        Tv tv = trialTv();
        long before = bal(tv, Currency.NDEM);
        Reply r = escrow(tv, "NDEM", 10, 2, "room-0001");
        assertEquals(200, r.status(), r.json().toString());
        JsonNode c = verifyCbe1(json, r.json().get("cbe1").asText());
        assertEquals("castbridge-play", c.get("aud").asText());
        assertEquals(tv.code(), c.get("id").asText());
        assertEquals("NDEM", c.get("cur").asText());
        assertEquals(10, c.get("per").asLong());
        assertEquals(2, c.get("k").asInt());
        assertEquals(20, c.get("amt").asLong());
        assertEquals(NOW, c.get("iat").asLong());
        assertEquals(NOW + Duration.ofMinutes(30).toMillis(), c.get("exp").asLong());
        assertEquals(22, c.get("eid").asText().length());
        assertEquals(c.get("eid").asText(), r.json().get("eid").asText());
        assertEquals(before - 20, bal(tv, Currency.NDEM));
        assertEquals(20, locked(tv, Currency.NDEM));
        assertEquals("OPEN", jdbc.queryForObject("SELECT state FROM wallet_escrow WHERE eid = ?", String.class, c.get("eid").asText()));
        assertEquals(10L, count("SELECT per FROM wallet_escrow WHERE eid = ?", c.get("eid").asText()));
        assertEquals(2L, count("SELECT k FROM wallet_escrow WHERE eid = ?", c.get("eid").asText()));
        assertTrue(r.json().get("snapshot").asText().startsWith("cbw1."), "un cbw1 neuf accompagne toute écriture");
        assertReconciled();
    }

    @Test
    void trialTvCannotStakeMboko() throws Exception {
        Tv tv = trialTv();
        adminGrant(tv.code(), "MBOKO", 50);
        Reply r = escrow(tv, "MBOKO", 5, 1, "room-0002");
        assertEquals(409, r.status());
        assertEquals("TRIAL_NO_MBOKO", r.reason());
        assertEquals("Mises MBOKO : version complète", r.message());
        assertEquals(50, bal(tv, Currency.MBOKO));
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_escrow WHERE holder = ?", tv.code()));
    }

    @Test
    void productionTvWithAnActiveLicenseStakesMboko() throws Exception {
        Tv tv = productionTv();
        assertEquals(10, bal(tv, Currency.MBOKO));
        Reply r = escrow(tv, "MBOKO", 2, 3, "room-0003");
        assertEquals(200, r.status(), r.json().toString());
        assertEquals(6, verifyCbe1(json, r.json().get("cbe1").asText()).get("amt").asLong());
        assertEquals(4, bal(tv, Currency.MBOKO));
        assertEquals(6, locked(tv, Currency.MBOKO));
    }

    @Test
    void mboko_stake_checks_the_LICENSE_state_not_the_activation_key() throws Exception {
        Tv suspended = productionTv(T0.plusSeconds(365L * 86_400), 14, "SUSPENDED");
        adminGrant(suspended.code(), "MBOKO", 10);
        Reply r = escrow(suspended, "MBOKO", 1, 1, "room-0004");
        assertEquals(409, r.status());
        assertEquals("ACTIVATE", r.reason(), "licence suspendue : pas de mise MBOKO même avec une clé de production valide");
        Tv revoked = productionTv(T0.plusSeconds(365L * 86_400), 14, "REVOKED");
        adminGrant(revoked.code(), "MBOKO", 10);
        assertEquals(409, escrow(revoked, "MBOKO", 1, 1, "room-0005").status());
        // en grâce (échue depuis 3 jours, 14 jours de grâce) : la mise est permise
        Tv grace = productionTv(T0.minusSeconds(3L * 86_400), 14, "ACTIVE");
        adminGrant(grace.code(), "MBOKO", 10);
        assertEquals(200, escrow(grace, "MBOKO", 1, 1, "room-0006").status());
        // grâce épuisée : refusé
        Tv gone = productionTv(T0.minusSeconds(20L * 86_400), 14, "ACTIVE");
        adminGrant(gone.code(), "MBOKO", 10);
        assertEquals(409, escrow(gone, "MBOKO", 1, 1, "room-0007").status());
    }

    @Test
    void aLicenseSuspendedAfterTheLastSyncStopsStakesAtOnce() throws Exception {
        Tv tv = productionTv();
        assertEquals(200, escrow(tv, "MBOKO", 1, 1, "room-0060").status());
        jdbc.update("UPDATE lic_license SET state = 'SUSPENDED' WHERE license_id = ?", "lic-" + tv.code().toLowerCase());
        Reply r = escrow(tv, "MBOKO", 1, 1, "room-0061");
        assertEquals(409, r.status(), "la licence est lue à chaque mise, pas seulement à la synchronisation");
        assertEquals("ACTIVATE", r.reason());
        assertEquals(409, escrow(tv, "NDEM", 1, 1, "room-0062").status());
        jdbc.update("UPDATE lic_license SET state = 'ACTIVE' WHERE license_id = ?", "lic-" + tv.code().toLowerCase());
        assertEquals(200, escrow(tv, "MBOKO", 1, 1, "room-0063").status());
    }

    @Test
    void productionKeyWithoutAnyLicenseIsPendingWhenTheActivationsAreSent() throws Exception {
        String auth = registerTv();
        castbridge.server.wallet.Acts.Tv hw = castbridge.server.wallet.Acts.Tv.random();
        Tv tv = new Tv(auth, hw, java.util.List.of(castbridge.server.wallet.Acts.production(ISSUER, hw, NOW, java.util.List.of())), true);
        sync(tv);
        var body = req(tv).put("cur", "NDEM").put("per", 1).put("k", 1).put("idem", "room-0008");
        body.putArray("activations").add(tv.activations().get(0));
        Reply r = postJson(auth, "/api/v1/wallet/escrow", body);
        assertEquals(409, r.status());
        assertEquals("LICENSE_PENDING", r.reason());
        assertEquals("Licence en attente d'enregistrement", r.message());
    }

    @Test
    void boundsOfPerAndKAndTheSwitches() throws Exception {
        Tv tv = productionTv();
        assertEquals(400, escrow(tv, "NDEM", 1001, 1, "room-0010").status(), "au-dessus du maximum par siège");
        assertEquals(400, escrow(tv, "NDEM", 0, 1, "room-0011").status());
        assertEquals(400, escrow(tv, "NDEM", 5, 0, "room-0012").status());
        assertEquals(400, escrow(tv, "NDEM", 5, 9, "room-0013").status());
        assertEquals(400, escrow(tv, "EURO", 5, 1, "room-0014").status());
        assertEquals(400, escrow(tv, "NDEM", 5, 1, "x").status(), "clé d'idempotence trop courte");
        assertEquals(409, escrow(tv, "NDEM", 1000, 8, "room-0015").status(), "8 000 NDEM : solde insuffisant (1 000 attribués)");
        assertEquals("INSUFFICIENT", escrow(tv, "NDEM", 1000, 8, "room-0016").reason());
        setPolicy("switch.stakes.NDEM", 0);
        Reply off = escrow(tv, "NDEM", 5, 1, "room-0017");
        assertEquals(409, off.status());
        assertEquals("STAKES_SUSPENDED", off.reason());
        assertEquals(200, escrow(tv, "MBOKO", 1, 1, "room-0018").status(), "l'interrupteur NDEM ne coupe pas MBOKO");
        setPolicy("switch.stakes.MBOKO", 0);
        assertEquals("STAKES_SUSPENDED", escrow(tv, "MBOKO", 1, 1, "room-0019").reason());
        setPolicy("switch.stakes.NDEM", 1);
        setPolicy("switch.stakes.MBOKO", 1);
        assertEquals(200, escrow(tv, "NDEM", 5, 1, "room-0020").status());
        setPolicy("stake.maxPerSeat.NDEM", 50);
        assertEquals(400, escrow(tv, "NDEM", 51, 1, "room-0021").status(), "les bornes sont lues à chaque appel");
    }

    @Test
    void sameIdemReplaysTheSameCbe1AndPostsOnce() throws Exception {
        Tv tv = trialTv();
        Reply a = escrow(tv, "NDEM", 10, 2, "room-0030");
        long after = bal(tv, Currency.NDEM);
        for (int i = 0; i < 3; i++) {
            Reply again = escrow(tv, "NDEM", 10, 2, "room-0030");
            assertEquals(200, again.status());
            assertEquals(a.json().get("cbe1").asText(), again.json().get("cbe1").asText(), "même blocage, même cbe1");
        }
        assertEquals(after, bal(tv, Currency.NDEM));
        assertEquals(1, newTxns("ESCROW_LOCK"));
        Reply other = escrow(tv, "NDEM", 10, 3, "room-0030");
        assertEquals(409, other.status());
        assertEquals("IDEM_CONFLICT", other.reason());
        assertEquals(after, bal(tv, Currency.NDEM));
        // la même clé d'une AUTRE TV est un autre blocage
        Tv tv2 = trialTv();
        Reply b = escrow(tv2, "NDEM", 10, 2, "room-0030");
        assertEquals(200, b.status());
        assertFalse(a.json().get("eid").asText().equals(b.json().get("eid").asText()));
    }

    @Test
    void anotherDevicesTokenIsRefusedBoundOtherTv() throws Exception {
        Tv tv = trialTv();
        String thief = registerTv();
        Reply r = escrowAs(tv, thief, "NDEM", 5, 1, "room-0040");
        assertEquals(403, r.status());
        assertEquals("BOUND_OTHER_TV", r.reason());
        assertEquals("Ce compte est lié à une autre TV", r.message());
        assertEquals(0, locked(tv, Currency.NDEM));
        assertEquals(401, escrowAs(tv, null, "NDEM", 5, 1, "room-0041").status());
        assertEquals(401, escrowAs(tv, "Bearer inconnu", "NDEM", 5, 1, "room-0042").status());
    }

    @Test
    void unknownIdentityAndFrozenIdentityAreRefused() throws Exception {
        Tv tv = trialTv();
        String auth = registerTv();
        castbridge.server.wallet.Acts.Tv hw = castbridge.server.wallet.Acts.Tv.random();
        Tv never = new Tv(auth, hw, java.util.List.of(), false);
        assertEquals(409, escrow(never, "NDEM", 5, 1, "room-0050").status(), "jamais synchronisée : Activez la TV");
        jdbc.update("UPDATE wallet_identity SET frozen = TRUE WHERE holder = ?", tv.code());
        Reply f = escrow(tv, "NDEM", 5, 1, "room-0051");
        assertEquals(409, f.status());
        assertEquals("FROZEN", f.reason());
    }
}
