package castbridge.server.quiz;

import castbridge.server.config.CastbridgeProperties;
import castbridge.server.updates.ManifestSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Question packs (docs/QUIZ.md, « Packs de questions »): the big banks built by tools/quiz-bank (content/quiz/dist) are
 * copied to {@code castbridge.quiz.packs-dir} and served to the TVs by course, in parts of about 1 500 questions
 * (~75 KB each). The catalog is signed entry by entry with the update key (Ed25519), so a TV only installs what this
 * server announced; each file is checked against its SHA-256 before it is announced.
 */
@Service
public class QuizPackService {
    private static final Logger log = LoggerFactory.getLogger(QuizPackService.class);
    public static final String FORMAT = "castbridge-quiz-pack-v1";
    private static final Pattern FILE = Pattern.compile("quiz-[a-z0-9-]+-p\\d+-v\\d+\\.quiz\\.zip");
    /** What a TV may keep from all the packs together (the apps enforce it too). */
    public static final long TV_CAP_BYTES = 11_000_000L;
    public static final int TARGET_GAMES = 300;
    public static final int PER_GAME = 15;

    public record Pack(String id, String track, String level, String field, int part, int parts, int version, String file, long size,
                       String sha256, int questions, Map<String, Integer> byRegion) {
        public String course() {
            StringBuilder b = new StringBuilder(track);
            if (level != null) b.append('/').append(level);
            if (field != null) b.append('/').append(field);
            return b.toString();
        }

        /** The text the server signs; must match castbridge.core.quiz.QuizPackInfo.canonicalPayload() in the apps. */
        public String canonicalPayload() {
            return String.join("\n", FORMAT, "id=" + id, "course=" + course(), "part=" + part, "parts=" + parts, "version=" + version,
                    "file=" + file, "size=" + size, "sha256=" + sha256, "questions=" + questions);
        }
    }

    private final Path dir;
    private final ObjectMapper json;
    private final ManifestSigner signer;
    private List<Pack> cache = List.of();
    private long cacheStamp = -1;
    private final Map<String, String> shaCache = new LinkedHashMap<>();

    public QuizPackService(CastbridgeProperties props, ObjectMapper json, ManifestSigner signer) {
        this.dir = props.quiz().packsDir();
        this.json = json;
        this.signer = signer;
    }

    public boolean signingEnabled() { return signer.enabled(); }

    /** Packs whose file exists with the announced size and hash (re-read when catalog.json or a file changes). */
    public synchronized List<Pack> packs() {
        Path catalog = dir.resolve("catalog.json");
        if (!Files.isRegularFile(catalog)) return List.of();
        try {
            long stamp = Files.getLastModifiedTime(catalog).toMillis() * 31 + Files.size(catalog);
            for (Path f : listPackFiles()) stamp = stamp * 31 + Files.getLastModifiedTime(f).toMillis() + Files.size(f);
            if (stamp == cacheStamp) return cache;
            JsonNode root = json.readTree(catalog.toFile());
            List<Pack> out = new ArrayList<>();
            for (JsonNode p : root.path("packs")) {
                Pack pack = parse(p);
                if (pack == null) continue;
                Path f = dir.resolve(pack.file());
                if (!Files.isRegularFile(f) || Files.size(f) != pack.size()) { log.warn("quiz pack {} missing or wrong size: not announced", pack.file()); continue; }
                String sha = shaCache.computeIfAbsent(pack.file() + ":" + pack.size() + ":" + Files.getLastModifiedTime(f).toMillis(), k -> sha256(f));
                if (!sha.equalsIgnoreCase(pack.sha256())) { log.warn("quiz pack {} does not match its SHA-256: not announced", pack.file()); continue; }
                out.add(pack);
            }
            cache = List.copyOf(out);
            cacheStamp = stamp;
            return cache;
        } catch (IOException e) {
            log.warn("quiz packs unreadable: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Path> listPackFiles() throws IOException {
        try (var s = Files.list(dir)) { return s.filter(p -> FILE.matcher(p.getFileName().toString()).matches()).sorted().toList(); }
    }

    private static Pack parse(JsonNode p) {
        String file = p.path("file").asText("");
        if (!FILE.matcher(file).matches() || p.path("size").asLong(0) <= 0) return null;
        Map<String, Integer> regions = new LinkedHashMap<>();
        p.path("byRegion").fields().forEachRemaining(e -> regions.put(e.getKey(), e.getValue().asInt()));
        return new Pack(p.path("id").asText(), p.path("track").asText(), p.path("level").isNull() ? null : p.path("level").asText(null),
                p.path("field").isNull() ? null : p.path("field").asText(null), p.path("part").asInt(), p.path("parts").asInt(), p.path("version").asInt(),
                file, p.path("size").asLong(), p.path("sha256").asText("").toLowerCase(), p.path("questions").asInt(), regions);
    }

    public Optional<Path> file(String name) {
        if (!FILE.matcher(name).matches()) return Optional.empty();
        return packs().stream().filter(p -> p.file().equals(name)).findFirst().map(p -> dir.resolve(p.file()));
    }

    public Optional<Pack> pack(String name) { return packs().stream().filter(p -> p.file().equals(name)).findFirst(); }

    /** The catalog the TVs and phones fetch: every entry signed (field names as in QuizPackInfo.parse in the apps). */
    public Map<String, Object> signedCatalog(String course) {
        List<Map<String, Object>> out = new ArrayList<>();
        int version = 0;
        long total = 0;
        for (Pack p : packs()) {
            if (course != null && !course.equals(p.course())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id()); m.put("track", p.track()); m.put("level", p.level()); m.put("field", p.field());
            m.put("part", p.part()); m.put("parts", p.parts()); m.put("version", p.version()); m.put("file", p.file());
            m.put("size", p.size()); m.put("sha256", p.sha256()); m.put("questions", p.questions());
            m.put("keyId", signer.keyId()); m.put("signature", signer.signBase64(p.canonicalPayload()));
            out.add(m);
            version = Math.max(version, p.version());
            total += p.size();
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", 1);
        root.put("version", version);
        root.put("tvCapBytes", TV_CAP_BYTES);
        root.put("totalBytes", total);
        root.put("packs", out);
        return root;
    }

    // ------------------------------------------------------------------------------------ coverage

    /** Games of {@code PER_GAME} questions a bank gives with no repeat (same rule as QuizBank.remainingFresh in the apps). */
    public static int games(String track, Map<String, Integer> byRegion, int total) {
        if ("general".equals(track)) {
            double cm = byRegion.getOrDefault("CM", 0) / (0.7 * PER_GAME);
            double af = byRegion.getOrDefault("AF", 0) / (0.2 * PER_GAME);
            double w = byRegion.getOrDefault("WORLD", 0) / (0.1 * PER_GAME);
            return (int) Math.min(cm, Math.min(af, w));
        }
        return total / PER_GAME;
    }

    private static String sha256(Path f) {
        try (InputStream in = Files.newInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
