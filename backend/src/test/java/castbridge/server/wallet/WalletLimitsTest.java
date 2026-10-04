package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Débits réglables : par identité et global, par minute (défauts 30 et 600). */
class WalletLimitsTest extends WalletTestBase {
    @DynamicPropertySource
    static void limits(DynamicPropertyRegistry r) {
        r.add("castbridge.wallet.writes-per-minute-per-identity", () -> "3");
        r.add("castbridge.wallet.writes-per-minute-global", () -> "7");
    }

    private int sync(String auth, Acts.Tv tv, long now) throws Exception {
        String b = "{\"deviceCode\":\"" + tv.code() + "\",\"activations\":[\"" + Acts.trialDays(ISSUER, tv, now, 30) + "\"]}";
        return mvc.perform(post("/api/v1/wallet/sync").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(b)).andReturn().getResponse().getStatus();
    }

    @Test
    void perIdentityThenGlobalLimitsAnswer429() throws Exception {
        Instant t0 = Instant.parse("2026-10-04T09:00:00Z");
        clock.freezeAt(t0);
        String auth = registerTv();
        Acts.Tv a = Acts.Tv.random(), b = Acts.Tv.random();
        for (int i = 0; i < 3; i++) assertEquals(200, sync(auth, a, t0.toEpochMilli()));
        assertEquals(429, sync(auth, a, t0.toEpochMilli()), "4e écriture de la minute pour cette identité");
        assertEquals(200, sync(registerTv(), b, t0.toEpochMilli()), "une autre identité n'est pas gênée");
        clock.freezeAt(t0.plusSeconds(61));
        assertEquals(200, sync(auth, a, t0.toEpochMilli()), "la minute suivante, ça repart");
        // global : 7 par minute, toutes identités confondues ; 1 déjà utilisé dans cette minute-ci (a)
        int refused = 0;
        for (int i = 0; i < 10; i++) if (sync(registerTv(), Acts.Tv.random(), t0.toEpochMilli()) == 429) refused++;
        assertEquals(10 - 6, refused, "limite globale de 7 par minute, dont 1 déjà utilisé");
    }
}
