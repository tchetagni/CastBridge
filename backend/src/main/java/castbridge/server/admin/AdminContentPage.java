package castbridge.server.admin;

import castbridge.server.CastbridgeApplication;
import castbridge.server.content.ContentBudget;
import castbridge.server.content.ContentRules;
import castbridge.server.content.ContentService;
import castbridge.server.web.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin web interface « Contenus » (docs/CONTENT-VALIDATION.md): review queue (by lot / class / subject, sorted by reports or suspicious
 * score), decisions with a note, bulk validation of a lot, CSV export and re-import for reviewers who work offline, validation
 * progress per lot and per class, and the content budget (3 GB).
 */
@Controller
public class AdminContentPage {
    private static final int PAGE = 20;
    private final ContentService content;
    private final ContentBudget budget;

    public AdminContentPage(ContentService content, ContentBudget budget) {
        this.content = content;
        this.budget = budget;
    }

    @GetMapping("/admin/content")
    public String queue(@RequestParam(required = false) String lot, @RequestParam(required = false, name = "cls") String cls,
                        @RequestParam(required = false) String subject, @RequestParam(required = false) String state,
                        @RequestParam(required = false) String kind, @RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "score") String sort, @RequestParam(defaultValue = "0") int page, Model model) {
        ContentService.Filter f = new ContentService.Filter(lot, cls, subject, state, kind, q, sort);
        model.addAttribute("page", content.queue(f, page, PAGE));
        model.addAttribute("lots", content.lots());
        model.addAttribute("classes", content.classes(lot));
        model.addAttribute("subjects", content.subjects(lot));
        model.addAttribute("states", ContentRules.STATES);
        model.addAttribute("stateLabels", ContentRules.STATE_LABELS);
        model.addAttribute("reasons", ContentRules.REASONS);
        model.addAttribute("lot", lot); model.addAttribute("cls", cls); model.addAttribute("subject", subject); model.addAttribute("state", state);
        model.addAttribute("kind", kind); model.addAttribute("q", q); model.addAttribute("sort", sort);
        model.addAttribute("active", "content");
        return "admin/content";
    }

    @GetMapping("/admin/content/item")
    public String item(@RequestParam String kind, @RequestParam String id, @RequestParam(defaultValue = "") String back, Model model) {
        ContentService.Row r = content.item(kind, id);
        model.addAttribute("r", r);
        model.addAttribute("reports", content.reports(kind, id, r.hash()));
        model.addAttribute("reasonCounts", content.reasonCounts(kind, id));
        model.addAttribute("reasons", ContentRules.REASONS);
        model.addAttribute("stateLabels", ContentRules.STATE_LABELS);
        model.addAttribute("next", ContentRules.STATES.stream().filter(s -> ContentRules.canMove(r.state(), s)).toList());
        model.addAttribute("back", safeBack(back));
        model.addAttribute("active", "content");
        return "admin/content-item";
    }

    @PostMapping("/admin/content/decide")
    public String decide(@RequestParam String kind, @RequestParam String id, @RequestParam String state, @RequestParam(defaultValue = "") String note,
                         @RequestParam(defaultValue = "") String back, Principal who, RedirectAttributes ra) {
        try {
            content.decide(kind, id, state, who.getName(), note, LocalDate.now(CastbridgeApplication.ZONE));
            ra.addFlashAttribute("ok", id + " : " + ContentRules.STATE_LABELS.get(ContentRules.state(state)));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/content" + safeBack(back);
    }

    @PostMapping("/admin/content/bulk")
    public String bulk(@RequestParam String lot, @RequestParam(required = false, name = "cls") String cls, @RequestParam(defaultValue = "") String note,
                       @RequestParam(defaultValue = "false") boolean includeReported, Principal who, RedirectAttributes ra) {
        try {
            ContentService.BulkResult r = content.bulkValidate(lot, cls, who.getName(), note, includeReported);
            ra.addFlashAttribute("ok", "Lot " + lot + " : " + r.validated() + " validés, " + r.skippedReported() + " laissés en relecture (signalés ou suspects), "
                    + r.skippedOther() + " sans empreinte");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/content?lot=" + java.net.URLEncoder.encode(lot, StandardCharsets.UTF_8);
    }

    @GetMapping("/admin/content/export.csv")
    public ResponseEntity<String> exportCsv(@RequestParam(required = false) String lot, @RequestParam(required = false, name = "cls") String cls,
                                            @RequestParam(required = false) String subject, @RequestParam(required = false) String state,
                                            @RequestParam(required = false) String kind) {
        String body = content.exportCsv(new ContentService.Filter(lot, cls, subject, state, kind, null, "lot"));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contenus-" + LocalDate.now(CastbridgeApplication.ZONE) + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(body);
    }

    @PostMapping("/admin/content/import-csv")
    public String importCsv(@RequestParam("file") MultipartFile file, Principal who, RedirectAttributes ra) {
        try {
            if (file.isEmpty()) throw ApiException.badRequest("Choisissez un fichier CSV");
            if (file.getSize() > 20 << 20) throw ApiException.badRequest("Import limité à 20 Mo");
            ContentService.CsvResult r = content.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8), who.getName());
            ra.addFlashAttribute("ok", r.applied() + " décision(s) enregistrée(s), " + r.skipped() + " ligne(s) ignorée(s) ou refusée(s)");
            if (!r.errors().isEmpty()) ra.addFlashAttribute("details", r.errors());
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            ra.addFlashAttribute("error", "Lecture du fichier impossible");
        }
        return "redirect:/admin/content";
    }

    @PostMapping("/admin/content/import-index")
    public String importIndex(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        try {
            if (file.isEmpty()) throw ApiException.badRequest("Choisissez le fichier produit par « cbvalidate.py index »");
            if (file.getSize() > 64 << 20) throw ApiException.badRequest("Import limité à 64 Mo");
            ContentService.ImportResult r = content.importIndex(Arrays.asList(new String(file.getBytes(), StandardCharsets.UTF_8).split("\\R")));
            ra.addFlashAttribute("ok", "Index : " + r.created() + " nouveaux, " + r.updated() + " modifiés (dont " + r.reopened() + " remis en relecture), " + r.unchanged() + " inchangés");
            if (!r.errors().isEmpty()) ra.addFlashAttribute("details", r.errors());
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            ra.addFlashAttribute("error", "Lecture du fichier impossible");
        }
        return "redirect:/admin/content";
    }

    @GetMapping("/admin/content/progress")
    public String progress(Model model) {
        List<ContentService.Progress> lots = content.progress(false);
        model.addAttribute("lots", lots);
        model.addAttribute("classes", content.progress(true));
        model.addAttribute("budget", budget.report());
        model.addAttribute("active", "content");
        return "admin/content-progress";
    }

    private static String safeBack(String back) {
        return back != null && back.startsWith("?") && !back.contains("//") && back.length() < 500 ? back : "";
    }
}
