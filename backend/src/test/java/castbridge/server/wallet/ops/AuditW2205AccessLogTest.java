package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** F4 : le code de réception ne doit apparaître dans aucun journal d'accès (il est dans le chemin de la consultation). */
class AuditW2205AccessLogTest extends OpsTestBase {

    @Test
    void theReceiveCodeNeverReachesTheAccessLog() throws Exception {
        Tv to = trialTv(), from = trialTv();
        String code = receiveCode(to).json().get("code").asText();
        Logger access = (Logger) LoggerFactory.getLogger("castbridge.access");
        ListAppender<ILoggingEvent> list = new ListAppender<>();
        list.start();
        access.addAppender(list);
        try {
            assertEquals(200, lookup(from, code).status());
            assertEquals(409, lookup(from, "R0000-0000-00").status());
        } finally {
            access.detachAppender(list);
        }
        String all = list.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(all.contains("/api/v1/wallet/receive-code/"), "la route est journalisée : " + all);
        assertFalse(all.contains(code), "le code complet ne doit pas être journalisé : " + all);
        assertFalse(all.contains(code.replace("-", "")), "ni sa forme canonique");
        assertFalse(all.contains("R0000-0000-00"), "ni une saisie");
    }
}
