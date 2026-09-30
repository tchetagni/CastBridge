package castbridge.server.admin;

import castbridge.server.quiz.QuestionDto;
import castbridge.server.quiz.QuizCatalog;
import castbridge.server.quiz.QuizCsv;
import castbridge.server.quiz.QuizService;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin web interface: review the question bank, bulk import, export. */
@Controller
public class AdminQuizPage {
    private final QuizService quiz;
    private final ObjectMapper json;

    public AdminQuizPage(QuizService quiz, ObjectMapper json) {
        this.quiz = quiz;
        this.json = json;
    }

    @GetMapping("/admin/quiz")
    public String page(@RequestParam(defaultValue = "draft") String status, @RequestParam(required = false) String track,
                       @RequestParam(required = false) String level, @RequestParam(required = false) String field,
                       @RequestParam(required = false) String region, @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        String st = "all".equals(status) ? null : status;
        model.addAttribute("page", quiz.search(st, track, level, field, region, q, page, 25));
        model.addAttribute("status", status);
        model.addAttribute("track", track);
        model.addAttribute("level", level);
        model.addAttribute("field", field);
        model.addAttribute("region", region);
        model.addAttribute("q", q);
        model.addAttribute("stats", quiz.stats());
        model.addAttribute("tracks", QuizCatalog.TRACKS);
        model.addAttribute("levels", QuizCatalog.LEVELS.keySet());
        model.addAttribute("fields", QuizCatalog.FIELDS);
        model.addAttribute("active", "quiz");
        return "admin/quiz";
    }

    @PostMapping("/admin/quiz/{uuid}/status")
    public String status(@PathVariable String uuid, @RequestParam String value, @RequestParam(defaultValue = "") String back,
                         RedirectAttributes ra) {
        try {
            quiz.setStatus(uuid, value);
            ra.addFlashAttribute("ok", "Question " + uuid + " : " + label(value));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/quiz" + safeBack(back);
    }

    @PostMapping("/admin/quiz/{uuid}/delete")
    public String delete(@PathVariable String uuid, @RequestParam(defaultValue = "") String back, RedirectAttributes ra) {
        try {
            quiz.delete(uuid);
            ra.addFlashAttribute("ok", "Question " + uuid + " supprimée (les appareils l'effaceront à la prochaine synchro)");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/quiz" + safeBack(back);
    }

    @PostMapping("/admin/quiz/import")
    public String importFile(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun,
                             @RequestParam(defaultValue = "draft") String defaultStatus, RedirectAttributes ra) {
        try {
            if (file.isEmpty()) throw ApiException.badRequest("Choisissez un fichier JSON ou CSV");
            if (file.getSize() > 20 << 20) throw ApiException.badRequest("Import limité à 20 Mo");
            if (!QuizCatalog.STATUSES.contains(defaultStatus)) throw ApiException.badRequest("Statut par défaut invalide");
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            List<QuestionDto> batch = name.endsWith(".csv") ? QuizCsv.read(text) : parseJson(text);
            QuizService.ImportReport r = quiz.importAll(batch, dryRun, defaultStatus);
            ra.addFlashAttribute("ok", (dryRun ? "Vérification seule (rien d'enregistré) : " : "Import terminé : ") + r.total() + " questions, "
                    + r.created() + " nouvelles, " + r.updated() + " modifiées, " + r.unchanged() + " inchangées");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("details", e.details());
        } catch (IOException e) {
            ra.addFlashAttribute("error", "Lecture du fichier impossible");
        }
        return "redirect:/admin/quiz";
    }

    @GetMapping("/admin/quiz/export")
    public ResponseEntity<?> export(@RequestParam(defaultValue = "json") String format, @RequestParam(required = false) String status) {
        List<QuestionDto> all = quiz.export("all".equals(status) ? null : status);
        String stamp = LocalDate.now(castbridge.server.CastbridgeApplication.ZONE).toString();
        if ("csv".equals(format)) {
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"questions-" + stamp + ".csv\"")
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(QuizCsv.write(all, ';'));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", QuizService.FORMAT_VERSION);
        out.put("questions", all);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"questions-" + stamp + ".json\"")
                .contentType(MediaType.APPLICATION_JSON).body(out);
    }

    private List<QuestionDto> parseJson(String text) {
        try {
            JsonNode root = json.readTree(text);
            JsonNode list = root != null && root.isObject() ? root.get("questions") : root;
            if (list == null || !list.isArray()) throw ApiException.badRequest("JSON attendu : {\"questions\":[…]} ou un tableau");
            return json.convertValue(list, new TypeReference<List<QuestionDto>>() {});
        } catch (IOException | IllegalArgumentException e) {
            throw ApiException.badRequest("JSON illisible");
        }
    }

    private static String label(String status) {
        return switch (status) {
            case "reviewed" -> "validée (publiée sur les appareils)";
            case "rejected" -> "rejetée";
            default -> "remise en brouillon";
        };
    }

    /** Only a query string of this page may be given back (no open redirect). */
    private static String safeBack(String back) {
        return back != null && back.startsWith("?") && !back.contains("//") && back.length() < 500 ? back : "";
    }
}
