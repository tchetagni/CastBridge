package castbridge.server.content;

import castbridge.server.config.CastbridgeProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Content budget (owner limit: 3 GB for all the published lots): totals the size of every lot file of the quiz packs folder and of the
 * folders listed in {@code castbridge.content.lots-dirs} (default: {storage-dir}/learn-packs and {storage-dir}/lots), per feature and
 * per lot, and says OK, WARN (over 80 %) or FAIL (over the limit).
 */
@Service
public class ContentBudget {
    public static final long DEFAULT_LIMIT = 3_000_000_000L;
    public static final double WARN_AT = 0.8;

    public record Entry(String feature, String lot, String file, long bytes) {}

    public record Report(long limitBytes, long totalBytes, String status, Map<String, Long> perFeature, Map<String, Long> perLot, List<Entry> files) {
        public int percent() { return limitBytes == 0 ? 0 : (int) (100 * totalBytes / limitBytes); }
    }

    private final List<Path> dirs = new ArrayList<>();
    private final long limit;

    public ContentBudget(CastbridgeProperties props, @Value("${castbridge.content.lots-dirs:}") String extra,
                         @Value("${castbridge.content.budget-limit-bytes:" + DEFAULT_LIMIT + "}") long limit) {
        Set<Path> set = new LinkedHashSet<>();
        set.add(props.quiz().packsDir());
        set.add(props.storageDir().resolve("learn-packs"));
        set.add(props.storageDir().resolve("lots"));
        for (String d : extra.split(",")) if (!d.isBlank()) set.add(Path.of(d.trim()));
        dirs.addAll(set);
        this.limit = limit;
    }

    public Report report() {
        List<Entry> files = new ArrayList<>();
        for (Path d : dirs) {
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> s = Files.walk(d, 3)) {
                s.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".zip")).forEach(p -> {
                    try { files.add(entry(d, p, Files.size(p))); } catch (IOException ignored) { /* vanished while scanning */ }
                });
            } catch (IOException ignored) { /* unreadable folder: counted as empty */ }
        }
        return summarize(files, limit);
    }

    static Entry entry(Path root, Path file, long size) {
        String name = file.getFileName().toString();
        String feature = name.endsWith(".quiz.zip") ? "quiz" : name.endsWith(".learn.zip") ? "learn"
                : root.getFileName() == null ? "autre" : root.getFileName().toString();
        String lot = name.replaceAll("\\.(quiz|learn)\\.zip$|\\.zip$", "").replaceAll("-v\\d+$", "");
        return new Entry(feature, lot, name, size);
    }

    /** Pure: totals, breakdowns (biggest first) and status. */
    public static Report summarize(List<Entry> files, long limit) {
        long total = files.stream().mapToLong(Entry::bytes).sum();
        Map<String, Long> feature = new LinkedHashMap<>(), lot = new LinkedHashMap<>();
        files.forEach(e -> { feature.merge(e.feature(), e.bytes(), Long::sum); lot.merge(e.feature() + "/" + e.lot(), e.bytes(), Long::sum); });
        String status = total > limit ? "fail" : total > WARN_AT * limit ? "warn" : "ok";
        return new Report(limit, total, status, sorted(feature), sorted(lot), files.stream().sorted(Comparator.comparingLong(Entry::bytes).reversed()).toList());
    }

    private static Map<String, Long> sorted(Map<String, Long> m) {
        Map<String, Long> out = new LinkedHashMap<>();
        m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }
}
