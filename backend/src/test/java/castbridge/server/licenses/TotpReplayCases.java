package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;

import castbridge.server.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Second audit w23-05, MEDIUM-B (test R7 de l'auditeur, réécrit) : un code TOTP est à USAGE UNIQUE même sous concurrence, pour la connexion web, les dons du portefeuille et la décision
 * sur une activation. L'auditeur a mesuré 7 acceptations sur 8 appels parallèles (auto-invocation de {@code checkLoginCode} : le verrou de ligne tombait).
 * Les sous-classes l'exécutent sur H2 et sur MySQL 8.4 réel.
 */
public abstract class TotpReplayCases extends RegistrarTestBase {
    @Autowired protected LicenseAccounts accounts;

    @Test
    void theSameTotpCodeIsAcceptedExactlyOnceBySixteenThreadsOfVerifyNamedAdmin() throws Exception {
        for (int round = 0; round < 15; round++) {
            Admin admin = newAdmin();
            String code = admin.code();
            assertEquals(1, race(16, () -> {
                accounts.verifyNamedAdmin(admin.name, code);
                return true;
            }), "tour " + round + " : le même code TOTP accepté plusieurs fois en parallèle (verifyNamedAdmin)");
        }
    }

    @Test
    void theSameTotpCodeIsAcceptedExactlyOnceByTheLoginCheck() throws Exception {
        for (int round = 0; round < 15; round++) {
            Admin admin = newAdmin();
            String code = admin.code();
            assertEquals(1, race(16, () -> accounts.checkLoginCode(admin.name, code)), "tour " + round + " : le même code TOTP accepté plusieurs fois en parallèle (connexion)");
        }
    }

    @Test
    void aNewerCodeStillWorksAndAnOlderOneIsRefused() {
        Admin admin = newAdmin();
        String older = admin.code();
        String newer = admin.code();
        accounts.verifyNamedAdmin(admin.name, newer);
        try {
            accounts.verifyNamedAdmin(admin.name, older);
            org.junit.jupiter.api.Assertions.fail("un code plus ancien que le dernier accepté doit être refusé");
        } catch (ApiException expected) {
            assertEquals(403, expected.status().value());
        }
    }

    /** Nombre d'appels qui ont réussi parmi {@code n} lancés exactement en même temps. */
    private int race(int n, Callable<Boolean> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(pool.submit(() -> {
                go.await();
                try {
                    return call.call();
                } catch (ApiException e) {
                    return false;
                }
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<Boolean> f : fs) if (Boolean.TRUE.equals(f.get())) ok++;
        pool.shutdown();
        return ok;
    }
}
