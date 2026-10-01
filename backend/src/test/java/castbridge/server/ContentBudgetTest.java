package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.config.CastbridgeProperties;
import castbridge.server.content.ContentBudget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Content budget: totals, breakdown, warn above 80 %, fail above the limit (here 1 000 bytes instead of 3 GB). */
@TestPropertySource(properties = "castbridge.content.budget-limit-bytes=1000")
class ContentBudgetTest extends ApiTestBase {
    @Autowired CastbridgeProperties props;
    @Autowired ContentBudget budget;

    @Test
    void pureSummaryStatuses() {
        var files = List.of(new ContentBudget.Entry("quiz", "quiz-3e-p1", "a.zip", 300), new ContentBudget.Entry("learn", "cep-maths", "b.zip", 600),
                new ContentBudget.Entry("quiz", "quiz-3e-p2", "c.zip", 50));
        var r = ContentBudget.summarize(files, 1000);
        assertEquals(950, r.totalBytes()); assertEquals("warn", r.status()); assertEquals(95, r.percent());
        assertEquals("learn", r.perFeature().keySet().iterator().next());          // biggest first
        assertEquals(650L, r.perFeature().get("quiz") + r.perFeature().get("learn") - 300);
        assertEquals("ok", ContentBudget.summarize(files, 2000).status());
        assertEquals("fail", ContentBudget.summarize(files, 949).status());
        assertEquals("ok", ContentBudget.summarize(List.of(), 3_000_000_000L).status());
        assertEquals(3_000_000_000L, ContentBudget.DEFAULT_LIMIT);
    }

    @Test
    void scansTheLotFoldersAndFailsOverTheLimit() throws Exception {
        Path dir = props.quiz().packsDir();
        Files.createDirectories(dir);
        Files.write(dir.resolve("quiz-3e-p1-v1.quiz.zip"), new byte[600]);
        Files.write(dir.resolve("catalog.json"), new byte[5000]);                // not a lot: not counted
        var r = budget.report();
        assertEquals(600, r.totalBytes()); assertEquals("ok", r.status());
        Path learn = props.storageDir().resolve("learn-packs");
        Files.createDirectories(learn);
        Files.write(learn.resolve("cep-maths-v2.learn.zip"), new byte[500]);
        r = budget.report();
        assertEquals(1100, r.totalBytes()); assertEquals("fail", r.status());
        assertEquals(500L, r.perFeature().get("learn")); assertEquals(600L, r.perFeature().get("quiz"));
        assertTrue(r.perLot().containsKey("learn/cep-maths") && r.perLot().containsKey("quiz/quiz-3e-p1"));
        mvc.perform(get("/api/v1/admin/content/budget").header("Authorization", ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("fail"));
        mvc.perform(get("/api/v1/admin/content/budget").param("strict", "true").header("Authorization", ADMIN)).andExpect(status().isInsufficientStorage());
    }
}
