package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.Currency;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Course : plusieurs fils postent le même résultat en même temps (la TV gagnante, la TV perdante, le collecteur) : une seule transaction SETTLE, même réponse pour tous. */
class SettleRaceTest extends OpsTestBase {

    @Test
    void eightThreadsPostingTheSameResultMakeOneSettleTransaction() throws Exception {
        Tv a = trialTv(), b = trialTv();
        String ea = escrow(a, "NDEM", 10, 2, "race-0001").json().get("eid").asText();
        String eb = escrow(b, "NDEM", 10, 2, "race-0001").json().get("eid").asText();
        String t = cbr1(RESULT, rid(100), "NDEM", 10, "END", List.of(new Line(ea, a.code(), 20, 30), new Line(eb, b.code(), 20, 10)));
        int n = 8;
        CyclicBarrier go = new CyclicBarrier(n);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<Reply>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<Reply> c = () -> {
                go.await();
                return settle(t);
            };
            fs.add(pool.submit(c));
        }
        Reply first = null;
        for (Future<Reply> f : fs) {
            Reply r = f.get();
            assertEquals(200, r.status(), r.json().toString());
            if (first == null) first = r;
            assertEquals(first.json(), r.json(), "même réponse pour tous");
        }
        pool.shutdown();
        assertEquals(1, newTxns("SETTLE"), "une seule transaction de règlement");
        assertEquals(110, bal(a, Currency.NDEM), "A : 80 + 30");
        assertEquals(90, bal(b, Currency.NDEM), "B : 80 + 10");
        assertEquals(0, sys("SYS:POT", "NDEM"));
        assertTrue(count("SELECT COUNT(*) FROM wallet_result WHERE rid = ?", rid(100)) == 1);
        assertReconciled();
    }
}
