package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import castbridge.server.wallet.core.Currency;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Débits : le règlement est limité par adresse (60 / min par défaut, ici 4), les opérations d'écriture par identité (30 / min par défaut, ici 3). */
class OpsLimitsTest extends OpsTestBase {
    @DynamicPropertySource
    static void limits(DynamicPropertyRegistry r) {
        r.add("castbridge.wallet.settle-per-minute", () -> "4");
        r.add("castbridge.wallet.writes-per-minute-per-identity", () -> "3");
    }

    @Test
    void settleIsRateLimitedPerAddressAndWritesPerIdentity() throws Exception {
        for (int i = 0; i < 4; i++) assertEquals(400, settle("pas un résultat").status(), "les 4 premières sont traitées (et refusées : illisibles)");
        assertEquals(429, settle("pas un résultat").status(), "5e requête de la minute");
        clock.freezeAt(T0.plus(Duration.ofSeconds(61)));
        assertEquals(400, settle("pas un résultat").status(), "la minute suivante, ça repart");
        // écritures par identité : la synchronisation de préparation en compte une
        Tv tv = trialTv();   // 1 écriture (sync)
        assertEquals(200, escrow(tv, "NDEM", 1, 1, "lim-0001").status());
        assertEquals(200, escrow(tv, "NDEM", 1, 1, "lim-0002").status());
        Reply third = escrow(tv, "NDEM", 1, 1, "lim-0003");
        assertEquals(429, third.status());
        assertEquals(98, bal(tv, Currency.NDEM));
        clock.freezeAt(T0.plus(Duration.ofSeconds(130)));
        assertEquals(200, escrow(tv, "NDEM", 1, 1, "lim-0003").status());
    }
}
