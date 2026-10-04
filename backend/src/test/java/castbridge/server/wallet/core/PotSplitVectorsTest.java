package castbridge.server.wallet.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Les mêmes vecteurs que le test Kotlin {@code PotVectorsTest} : tools/wallet/ledger-vectors.json. */
class PotSplitVectorsTest {
    static JsonNode vectors() throws Exception {
        Path p = Path.of("../tools/wallet/ledger-vectors.json");
        if (!Files.exists(p)) p = Path.of("tools/wallet/ledger-vectors.json");
        JsonNode root = new ObjectMapper().readTree(Files.readString(p));
        assertEquals("castbridge-ledger-vectors-v1", root.get("format").asText());
        return root;
    }

    @Test
    void javaPortMatchesTheSharedVectors() throws Exception {
        JsonNode cases = vectors().get("potSplit");
        assertTrue(cases.size() >= 12, "au moins 12 cas");
        for (JsonNode c : cases) {
            Map<String, Integer> scores = new LinkedHashMap<>();
            for (JsonNode s : c.get("scores")) scores.put(s.get(0).asText(), s.get(1).asInt());
            Map<String, Long> expect = new LinkedHashMap<>();
            for (JsonNode e : c.get("expect")) expect.put(e.get(0).asText(), e.get(1).asLong());
            assertEquals(expect, PotSplit.split(c.get("pot").asLong(), scores), c.get("name").asText());
        }
    }

    @Test
    void sharesAreTheKotlinOnes() {
        assertEquals(java.util.List.of(100), PotSplit.shares(1));
        assertEquals(java.util.List.of(70, 30), PotSplit.shares(2));
        assertEquals(java.util.List.of(60, 30, 10), PotSplit.shares(3));
        assertEquals(java.util.List.of(60, 30, 10), PotSplit.shares(8));
    }

    @Test
    void splitSumsToThePotWhenAnyoneScored() {
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 5000; i++) {
            Map<String, Integer> scores = new LinkedHashMap<>();
            int n = 1 + r.nextInt(8);
            boolean any = false;
            for (int j = 0; j < n; j++) { int s = r.nextInt(4); any |= s > 0; scores.put("s" + j, s); }
            long pot = r.nextInt(5000);
            long sum = PotSplit.split(pot, scores).values().stream().mapToLong(Long::longValue).sum();
            assertEquals(any ? pot : 0, sum, "pot " + pot + " " + scores);
        }
    }
}
