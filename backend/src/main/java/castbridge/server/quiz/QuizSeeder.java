package castbridge.server.quiz;

import castbridge.server.config.CastbridgeProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * First start with an empty bank: imports the bank bundled in the TV app (copied from the feat/tv-quiz branch into
 * src/main/resources/seed). Questions marked {@code review: true} there arrive as drafts, the others as reviewed.
 * Lenient: an invalid or duplicate seed question is skipped with a warning, the rest is imported.
 */
@Component
public class QuizSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(QuizSeeder.class);
    static final List<String> FILES = List.of("seed/questions.json", "seed/questions-school.json");

    private final QuizService service;
    private final QuestionRepository questions;
    private final CastbridgeProperties props;
    private final ObjectMapper json;

    public QuizSeeder(QuizService service, QuestionRepository questions, CastbridgeProperties props, ObjectMapper json) {
        this.service = service;
        this.questions = questions;
        this.props = props;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.quiz().seed() || questions.count() > 0) return;
        List<QuestionDto> batch = new ArrayList<>();
        Set<String> ids = new HashSet<>(), keys = new HashSet<>();
        int skipped = 0;
        for (String file : FILES) {
            for (QuestionDto d : read(file)) {
                QuestionValidator.Result r = QuestionValidator.validate(d, "draft");
                if (!r.ok() || !ids.add(r.clean().uuid()) || !keys.add(r.clean().dedupKey())) {
                    skipped++;
                    log.warn("seed question {} skipped: {}", d.inputId(), r.ok() ? "duplicate" : String.join("; ", r.errors()));
                    continue;
                }
                batch.add(d);
            }
        }
        if (batch.isEmpty()) return;
        QuizService.ImportReport rep = service.importAll(batch, false, "draft");
        log.info("question bank seeded: {} questions ({} skipped)", rep.created(), skipped);
    }

    private List<QuestionDto> read(String file) {
        ClassPathResource res = new ClassPathResource(file);
        if (!res.exists()) return List.of();
        try (InputStream in = res.getInputStream()) {
            JsonNode root = json.readTree(in);
            return json.convertValue(root.get("questions"), new TypeReference<List<QuestionDto>>() {});
        } catch (IOException | IllegalArgumentException e) {
            log.warn("seed file {} unreadable: {}", file, e.getMessage());
            return List.of();
        }
    }
}
