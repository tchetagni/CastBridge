package castbridge.server.quiz;

import castbridge.server.CastbridgeApplication;
import castbridge.server.config.CastbridgeProperties;
import castbridge.server.web.ApiException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Question bank: admin CRUD and bulk import, device sync, game draws. */
@Service
public class QuizService {
    private static final Logger log = LoggerFactory.getLogger(QuizService.class);
    public static final int FORMAT_VERSION = 2;
    private static final int MAX_ERRORS = 100;

    private final QuestionRepository questions;
    private final TombstoneRepository tombstones;
    private final CastbridgeProperties props;

    public QuizService(QuestionRepository questions, TombstoneRepository tombstones, CastbridgeProperties props) {
        this.questions = questions;
        this.tombstones = tombstones;
        this.props = props;
    }

    // ================================================================ admin

    public Question get(String uuid) {
        return questions.findByUuid(uuid).orElseThrow(() -> ApiException.notFound("Question « " + uuid + " » introuvable"));
    }

    public Page<Question> search(String status, String track, String level, String field, String region, String text, int page, int size) {
        String like = text == null || text.isBlank() ? null : "%" + text.trim().toLowerCase() + "%";
        return questions.search(blank(status), blank(track), level == null ? null : QuizCatalog.levelKey(level), QuizCatalog.fieldKey(field),
                blank(region), like, PageRequest.of(Math.max(0, page), clamp(size, 1, 500)));
    }

    @Transactional
    public Question create(QuestionDto dto) {
        QuestionValidator.Clean c = valid(dto, "draft");
        if (dto.inputId() != null && questions.findByUuid(c.uuid()).isPresent())
            throw ApiException.conflict("Une question porte déjà l'identifiant « " + c.uuid() + " »");
        questions.findByDedupKey(c.dedupKey()).ifPresent(q -> {
            throw ApiException.conflict("Doublon de la question « " + q.getUuid() + " »");
        });
        Question q = new Question();
        c.applyTo(q);
        Instant now = Instant.now();
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        Question saved = questions.save(q);
        statusChanged(saved.getUuid(), false, saved.isReviewed(), now);
        return saved;
    }

    /** @param dto new content; its {@code version}, when given, must be the stored one (else 409: edited meanwhile) */
    @Transactional
    public Question update(String uuid, QuestionDto dto) {
        Question q = get(uuid);
        if (dto.version() != null && dto.version() != q.getVersion())
            throw ApiException.conflict("La question a été modifiée entre-temps (version " + q.getVersion() + ", reçue " + dto.version() + ")");
        QuestionValidator.Clean c = valid(withId(dto, uuid), q.getReviewStatus());
        questions.findByDedupKey(c.dedupKey()).filter(o -> !o.getUuid().equals(uuid)).ifPresent(o -> {
            throw ApiException.conflict("Doublon de la question « " + o.getUuid() + " »");
        });
        if (c.sameContent(q)) return q;
        boolean was = q.isReviewed();
        c.applyTo(q);
        Instant now = Instant.now();
        q.setUpdatedAt(now);
        statusChanged(uuid, was, q.isReviewed(), now);
        return questions.saveAndFlush(q);
    }

    @Transactional
    public Question setStatus(String uuid, String status) {
        if (!QuizCatalog.STATUSES.contains(status)) throw ApiException.badRequest("Statut : draft, reviewed ou rejected attendu");
        Question q = get(uuid);
        if (q.getReviewStatus().equals(status)) return q;
        boolean was = q.isReviewed();
        q.setReviewStatus(status);
        Instant now = Instant.now();
        q.setUpdatedAt(now);
        statusChanged(uuid, was, q.isReviewed(), now);
        return questions.saveAndFlush(q);
    }

    @Transactional
    public void delete(String uuid) {
        Question q = get(uuid);
        questions.delete(q);
        tombstone(uuid, Instant.now());
    }

    public record ImportReport(int total, int created, int updated, int unchanged, boolean dryRun) {}

    /**
     * All or nothing: every question is validated first (content, duplicates inside the batch and against the bank);
     * one error = nothing is written and the 400 lists the faulty questions. Existing ids are updated, unchanged
     * questions keep their date (a re-import does not make the devices download anything).
     */
    @Transactional
    public ImportReport importAll(List<QuestionDto> batch, boolean dryRun, String defaultStatus) {
        if (batch.isEmpty()) throw ApiException.badRequest("Aucune question à importer");
        if (batch.size() > 20_000) throw ApiException.badRequest("20 000 questions au maximum par import");
        List<String> errors = new ArrayList<>();
        List<QuestionValidator.Clean> clean = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Map<String, String> dedup = new HashMap<>();
        for (int i = 0; i < batch.size(); i++) {
            QuestionDto d = batch.get(i);
            QuestionValidator.Result r = QuestionValidator.validate(d, defaultStatus);
            String label = "#" + (i + 1) + (d.inputId() != null ? " (" + d.inputId() + ")" : "");
            if (!r.ok()) {
                r.errors().forEach(err -> errors.add(label + " " + err));
                continue;
            }
            QuestionValidator.Clean c = r.clean();
            if (!ids.add(c.uuid())) errors.add(label + " identifiant en double dans le lot");
            String other = dedup.putIfAbsent(c.dedupKey(), c.uuid());
            if (other != null) errors.add(label + " même question que " + other + " dans le lot");
            clean.add(c);
        }
        Map<String, Question> existing = new HashMap<>();
        Map<String, Question> byDedup = new HashMap<>();
        for (List<QuestionValidator.Clean> chunk : chunks(clean, 500)) {
            questions.findByUuidIn(chunk.stream().map(QuestionValidator.Clean::uuid).toList()).forEach(q -> existing.put(q.getUuid(), q));
            questions.findByDedupKeyIn(chunk.stream().map(QuestionValidator.Clean::dedupKey).toList()).forEach(q -> byDedup.put(q.getDedupKey(), q));
        }
        for (QuestionValidator.Clean c : clean) {
            Question twin = byDedup.get(c.dedupKey());
            // the twin may itself be renamed/changed by this batch: only a conflict if it keeps that text
            if (twin != null && !twin.getUuid().equals(c.uuid()) && !ids.contains(twin.getUuid()))
                errors.add("(" + c.uuid() + ") doublon de la question existante « " + twin.getUuid() + " »");
        }
        if (!errors.isEmpty()) {
            List<String> shown = errors.size() > MAX_ERRORS ? new ArrayList<>(errors.subList(0, MAX_ERRORS)) : errors;
            if (errors.size() > MAX_ERRORS) shown.add("… et " + (errors.size() - MAX_ERRORS) + " autres erreurs");
            throw new ApiException(HttpStatus.BAD_REQUEST, "Import refusé : " + errors.size() + " erreur(s), rien n'a été enregistré", shown);
        }
        int created = 0, updated = 0, unchanged = 0;
        Instant now = Instant.now();
        for (QuestionValidator.Clean c : clean) {
            Question q = existing.get(c.uuid());
            if (q != null && c.sameContent(q)) {
                unchanged++;
                continue;
            }
            if (q == null) created++;
            else updated++;
            if (dryRun) continue;
            boolean was = q != null && q.isReviewed();
            if (q == null) {
                q = new Question();
                q.setCreatedAt(now);
            }
            c.applyTo(q);
            q.setUpdatedAt(now);
            questions.save(q);
            statusChanged(c.uuid(), was, q.isReviewed(), now);
        }
        if (!dryRun) log.info("quiz import: {} created, {} updated, {} unchanged", created, updated, unchanged);
        return new ImportReport(clean.size(), created, updated, unchanged, dryRun);
    }

    public List<QuestionDto> export(String status) {
        return questions.findAll(org.springframework.data.domain.Sort.by("id")).stream()
                .filter(q -> status == null || status.isBlank() || q.getReviewStatus().equals(status))
                .map(QuestionDto::of).toList();
    }

    public Map<String, Object> stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>(), byTrack = new LinkedHashMap<>(), byLevel = new LinkedHashMap<>(),
                byRegion = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : questions.stats()) {
            long n = (Long) row[4];
            total += n;
            byStatus.merge((String) row[0], n, Long::sum);
            if ("reviewed".equals(row[0])) {
                byTrack.merge((String) row[1], n, Long::sum);
                byLevel.merge(row[2] == null ? "(culture générale)" : (String) row[2], n, Long::sum);
                byRegion.merge((String) row[3], n, Long::sum);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("parStatut", byStatus);
        out.put("publieesParParcours", byTrack);
        out.put("publieesParNiveau", byLevel);
        out.put("publieesParRegion", byRegion);
        return out;
    }

    // ================================================================ devices

    public record SyncPage(int version, int page, int size, int totalPages, long total, OffsetDateTime syncToken,
                           OffsetDateTime since, boolean resetRequired, List<QuestionDto> questions, List<String> deleted) {}

    /**
     * Published questions, page by page, oldest change first. With {@code since}: only what changed after it, plus (on
     * page 0) the ids to delete. A device keeps the {@code syncToken} of page 0 (date of the last change in the bank,
     * stable while nothing changes, so the ETag / 304 works) as its next {@code since}.
     * {@code resetRequired} = the device was away longer than the deletions are remembered: drop the cache, fetch all.
     */
    public SyncPage published(String track, String level, String field, String lang, String sinceText, int page, int size) {
        Filter f = filter(track, level, field, true);
        Instant since = parseSince(sinceText);
        Instant now = Instant.now();
        int p = Math.max(0, page), s = clamp(size, 1, 500);
        Page<Question> res = questions.published(f.track, f.level, f.field, blank(lang), since, PageRequest.of(p, s));
        boolean reset = since != null && since.isBefore(now.minus(props.quiz().tombstoneDays(), ChronoUnit.DAYS));
        List<String> deleted = since == null || p > 0 ? List.of()
                : tombstones.findByDeletedAtAfterOrderByDeletedAtAsc(since).stream().map(QuestionTombstone::getUuid).toList();
        Instant last = questions.lastChange();
        Instant lastDeletion = tombstones.lastDeletion();
        if (last == null || (lastDeletion != null && lastDeletion.isAfter(last))) last = lastDeletion;
        if (last == null) last = since;
        return new SyncPage(FORMAT_VERSION, p, s, res.getTotalPages(), res.getTotalElements(), last == null ? null : local(last),
                since == null ? null : local(since), reset, res.map(QuestionDto::of).getContent(), deleted);
    }

    public record Draw(long seed, String track, String level, String field, int count, List<QuestionDto> questions) {}

    /**
     * {@code count} questions of increasing difficulty (the target difficulty rises from 1 to 5 with the position),
     * reproducible for a seed; for general knowledge 70 % Cameroon / 20 % Africa / 10 % World (±1). Same algorithm as
     * QuizBank.draw on the TV (feat/tv-quiz), so a server draw and an offline draw feel the same.
     */
    public Draw draw(String track, String level, String field, String lang, int count, Long seed, Set<String> exclude, boolean shuffleChoices) {
        Filter f = filter(track == null || track.isBlank() ? "general" : track, level, field, false);
        if (count < 1 || count > 50) throw ApiException.badRequest("count : entre 1 et 50");
        long s = seed != null ? seed : new Random().nextLong() & 0xFFFFFFFFFFFFL;
        List<Question> pool = questions.pool(f.track, f.level, f.field, blank(lang));
        pool.sort(java.util.Comparator.comparing(Question::getId)); // same seed + same bank = same draw
        List<Question> picked = drawFrom(pool, count, new Random(s), exclude, "general".equals(f.track));
        Random rng = new Random(s ^ 0x5DEECE66DL);
        List<QuestionDto> out = new ArrayList<>();
        for (Question q : picked) {
            QuestionDto d = QuestionDto.of(q);
            if (shuffleChoices) {
                List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
                Collections.shuffle(order, rng);
                List<String> c = order.stream().map(i -> q.getChoices().get(i)).toList();
                d = d.withChoices(c, order.indexOf(q.getAnswer()));
            }
            out.add(d);
        }
        return new Draw(s, f.track, f.level, f.field, out.size(), out);
    }

    static List<Question> drawFrom(List<Question> pool, int count, Random rng, Set<String> exclude, boolean general) {
        int n = Math.min(count, pool.size());
        if (n == 0) return List.of();
        List<String> slots = new ArrayList<>();
        if (general) {
            Map<String, Integer> quota = quotas(n, rng);
            for (String r : List.of("CM", "AF", "WORLD")) for (int i = 0; i < quota.get(r); i++) slots.add(r);
            Collections.shuffle(slots, rng);
        } else {
            for (int i = 0; i < n; i++) slots.add(null);
        }
        Set<String> used = new HashSet<>();
        List<Question> picked = new ArrayList<>();
        for (int pos = 0; pos < n; pos++) {
            int target = targetDifficulty(pos, n);
            String region = slots.get(pos);
            List<Question> inRegion = region == null ? pool : pool.stream().filter(q -> q.getRegion().equals(region)).toList();
            List<Question> cands = inRegion.stream().filter(q -> !used.contains(q.getUuid()) && !exclude.contains(q.getUuid())).toList();
            if (cands.isEmpty()) cands = inRegion.stream().filter(q -> !used.contains(q.getUuid())).toList();
            if (cands.isEmpty()) cands = pool.stream().filter(q -> !used.contains(q.getUuid()) && !exclude.contains(q.getUuid())).toList();
            if (cands.isEmpty()) cands = pool.stream().filter(q -> !used.contains(q.getUuid())).toList();
            if (cands.isEmpty()) break;
            int best = cands.stream().mapToInt(q -> Math.abs(q.getDifficulty() - target)).min().orElseThrow();
            List<Question> top = cands.stream().filter(q -> Math.abs(q.getDifficulty() - target) == best).toList();
            Question q = top.get(rng.nextInt(top.size()));
            used.add(q.getUuid());
            picked.add(q);
        }
        List<Question> sorted = new ArrayList<>(picked);
        sorted.sort(java.util.Comparator.comparingInt(Question::getDifficulty)); // stable: keeps the pick order on ties
        return sorted;
    }

    /** 1..5 rising evenly with the position (15 questions: 3 per level). */
    static int targetDifficulty(int pos, int count) { return 1 + (pos * 5) / count; }

    /** 70/20/10: Africa and World rounded, Cameroon takes the rest; when World is exactly x.5 it goes up or down (seeded). */
    static Map<String, Integer> quotas(int count, Random rng) {
        int af = (int) Math.round(count * 0.2);
        double exactWorld = count * 0.1;
        int world = exactWorld % 1.0 == 0.5 ? (int) exactWorld + rng.nextInt(2) : (int) Math.round(exactWorld);
        return Map.of("CM", count - af - world, "AF", af, "WORLD", world);
    }

    // ================================================================ housekeeping

    @Scheduled(cron = "0 30 3 * * *", zone = "Africa/Douala")
    @Transactional
    public void purgeTombstones() {
        int n = tombstones.purgeBefore(Instant.now().minus(props.quiz().tombstoneDays(), ChronoUnit.DAYS));
        if (n > 0) log.info("purged {} old question tombstones", n);
    }

    // ================================================================ helpers

    private record Filter(String track, String level, String field) {}

    private static Filter filter(String track, String level, String field, boolean trackOptional) {
        String t = blank(track);
        if (t != null) t = t.toLowerCase();
        if (t == null && !trackOptional) t = "general";
        if (t != null && !QuizCatalog.TRACKS.contains(t)) throw ApiException.badRequest("track : general, primary, secondary ou higher attendu");
        String l = null;
        if (blank(level) != null) {
            l = QuizCatalog.levelKey(level);
            if (l == null) throw ApiException.badRequest("level : niveau inconnu « " + level + " »");
            if (t != null && !QuizCatalog.LEVELS.get(l).equals(t)) throw ApiException.badRequest("level : " + l + " n'appartient pas au parcours " + t);
        }
        String f = QuizCatalog.fieldKey(field);
        if (f != null && !QuizCatalog.FIELDS.contains(f)) throw ApiException.badRequest("field : filière inconnue « " + field + " »");
        return new Filter(t, l, f);
    }

    private QuestionValidator.Clean valid(QuestionDto dto, String defaultStatus) {
        QuestionValidator.Result r = QuestionValidator.validate(dto, defaultStatus);
        if (!r.ok()) throw new ApiException(HttpStatus.BAD_REQUEST, "Question invalide", r.errors());
        return r.clean();
    }

    private static QuestionDto withId(QuestionDto d, String uuid) {
        return new QuestionDto(uuid, uuid, d.lang(), d.track(), d.level(), d.field(), d.region(), d.category(), d.difficulty(), d.question(),
                d.choices(), d.answer(), d.explanation(), d.source(), d.reviewStatus(), d.status(), d.review(), null, d.version());
    }

    /** Keeps the tombstones in step: leaving "reviewed" = devices must drop it; (re)entering = not deleted any more. */
    private void statusChanged(String uuid, boolean wasReviewed, boolean isReviewed, Instant now) {
        if (wasReviewed && !isReviewed) tombstone(uuid, now);
        else if (isReviewed) tombstones.findById(uuid).ifPresent(tombstones::delete);
    }

    private void tombstone(String uuid, Instant now) {
        QuestionTombstone t = tombstones.findById(uuid).orElseGet(() -> new QuestionTombstone(uuid, now));
        t.setDeletedAt(now);
        tombstones.save(t);
    }

    static Instant parseSince(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s.trim().replace(' ', '+')).toInstant(); // '+' of the offset often arrives as a space
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(s.trim());
            } catch (DateTimeParseException e2) {
                throw ApiException.badRequest("since : date ISO 8601 attendue (ex. 2026-09-30T10:00:00+01:00)");
            }
        }
    }

    private static OffsetDateTime local(Instant i) { return i.atZone(CastbridgeApplication.ZONE).toOffsetDateTime(); }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static <T> List<List<T>> chunks(List<T> list, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) out.add(list.subList(i, Math.min(list.size(), i + size)));
        return out;
    }
}
