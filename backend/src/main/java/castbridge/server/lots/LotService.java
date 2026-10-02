package castbridge.server.lots;

import castbridge.server.CastbridgeApplication;
import castbridge.server.config.CastbridgeProperties;
import castbridge.server.updates.ManifestSigner;
import castbridge.server.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lots (docs/LOTS.md): the admin uploads a lot (checked: size, sha256, minAppVersion, per-feature validator), publishes it
 * (per-lot rollout and channel, like the app releases); the phones fetch the signed catalog and the files. A lot that is not
 * published, or is revoked, is never listed nor served. Files live in {@code <storage-dir>/lots/} and never change once written.
 */
@Service
public class LotService {
    private static final Logger log = LoggerFactory.getLogger(LotService.class);

    public static final Set<String> FEATURES = Set.of("learn", "quiz", "langues");
    public static final Set<String> CHANNELS = Set.of("stable", "beta");
    /** A lot must fit in the TV's 10 Mo budget (castbridge.core.lots.LotBudget.TV_MAX_BYTES). */
    public static final long MAX_LOT_BYTES = 10L << 20;
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final LotRepository repo;
    private final CastbridgeProperties props;
    private final ManifestSigner signer;
    private final Map<String, LotValidator> validators = new HashMap<>();

    public LotService(LotRepository repo, CastbridgeProperties props, ManifestSigner signer, List<LotValidator> custom) {
        this.repo = repo;
        this.props = props;
        this.signer = signer;
        for (String f : FEATURES) validators.put(f, new ZipLotValidator(f));
        validators.put(LangLotValidator.FEATURE, new LangLotValidator());           // Langues: its own format (docs/LANGUES.md), only free text lots
        for (LotValidator v : custom) validators.put(v.feature(), v);     // a feature's own validator replaces the default
    }

    public record NewLot(String feature, String scope, int version, String title, String channel, Integer minAppVersion, Integer rolloutPercent,
                         boolean publish, String expectedSha256) {}

    public boolean signingEnabled() { return signer.enabled(); }

    public Path dir() { return props.storageDir().resolve("lots"); }

    public Path file(Lot l) { return dir().resolve(l.getFileName()); }

    // ---------------------------------------------------------------- admin

    @Transactional
    public Lot upload(NewLot f, InputStream data) {
        List<String> errors = new ArrayList<>();
        if (!FEATURES.contains(f.feature())) errors.add("feature : « learn », « quiz » ou « langues » attendu");
        if (f.scope() == null || !SEGMENT.matcher(f.scope()).matches()) errors.add("scope : 1 à 32 caractères [a-z0-9-] (ex. cm2, 3e, tle-c, droit-l1)");
        if (f.version() <= 0) errors.add("version : entier positif attendu");
        String channel = f.channel() == null || f.channel().isBlank() ? "stable" : f.channel();
        if (!CHANNELS.contains(channel)) errors.add("canal : « stable » ou « beta » attendu");
        if (f.title() == null || f.title().isBlank() || f.title().length() > 200) errors.add("title : 1 à 200 caractères");
        int minApp = f.minAppVersion() == null ? 0 : f.minAppVersion();
        if (minApp < 0) errors.add("minAppVersion : entier positif ou nul attendu");
        int rollout = f.rolloutPercent() == null ? 100 : f.rolloutPercent();
        if (rollout < 0 || rollout > 100) errors.add("rollout : entre 0 et 100");
        if (f.expectedSha256() != null && !f.expectedSha256().matches("[0-9a-fA-F]{64}")) errors.add("sha256 : 64 chiffres hexadécimaux attendus");
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Lot refusé", errors);
        if (repo.existsByFeatureAndScopeAndVersion(f.feature(), f.scope(), f.version()))
            throw ApiException.conflict("Le lot " + f.feature() + "/" + f.scope() + " existe déjà en version " + f.version() + " (une version ne change jamais : publier la suivante)");

        Path incoming = dir().resolve(".incoming");
        Path tmp = incoming.resolve(UUID.randomUUID() + ".part");
        try {
            Files.createDirectories(incoming);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long size;
            try (InputStream in = new DigestInputStream(data, sha); OutputStream out = Files.newOutputStream(tmp)) {
                size = in.transferTo(out);
            }
            if (size == 0) throw ApiException.badRequest("Fichier vide ou absent (champ « file »)");
            if (size > MAX_LOT_BYTES)
                throw ApiException.badRequest("Lot trop gros : " + size + " octets (maximum " + MAX_LOT_BYTES + " : il doit tenir dans les 10 Mo de la TV)");
            String sha256 = HexFormat.of().formatHex(sha.digest());
            if (f.expectedSha256() != null && !f.expectedSha256().equalsIgnoreCase(sha256))
                throw ApiException.badRequest("Empreinte SHA-256 reçue " + sha256 + " ≠ attendue " + f.expectedSha256().toLowerCase() + " : envoi corrompu");
            List<String> problems = validators.get(f.feature()).validate(tmp, size, f.scope(), f.version());
            if (!problems.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Contenu du lot refusé", problems);

            String fileName = "castbridge-lot-%s-%s-v%d.lot".formatted(f.feature(), f.scope(), f.version());
            Files.createDirectories(dir());
            Path target = dir().resolve(fileName);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Lot l = new Lot();
            l.setFeature(f.feature());
            l.setScope(f.scope());
            l.setVersion(f.version());
            l.setChannel(channel);
            l.setTitle(f.title().strip());
            l.setSizeBytes(size);
            l.setSha256(sha256);
            l.setMinAppVersion(minApp);
            l.setFileName(fileName);
            l.setRolloutPercent(rollout);
            l.setUploadedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
            if (f.publish()) { l.setPublished(true); l.setPublishedAt(l.getUploadedAt()); }
            Lot saved = repo.save(l);
            log.info("lot uploaded: {} {} v{} {} ({} bytes, published={})", l.getFeature(), l.getScope(), l.getVersion(), channel, size, f.publish());
            return saved;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible d'enregistrer le lot sur le serveur");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { /* a stale .part in .incoming is harmless */ }
        }
    }

    public List<Lot> list() { return repo.findAllByOrderByFeatureAscScopeAscVersionDesc(); }

    public Lot get(long id) { return repo.findById(id).orElseThrow(() -> ApiException.notFound("Lot " + id + " introuvable")); }

    @Transactional
    public Lot publish(long id) {
        Lot l = get(id);
        if (l.isRevoked()) throw ApiException.conflict("Ce lot a été retiré : publier une nouvelle version");
        if (!l.isPublished()) { l.setPublished(true); l.setPublishedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS)); }
        return l;
    }

    @Transactional
    public Lot revoke(long id) {
        Lot l = get(id);
        if (!l.isRevoked()) { l.setRevoked(true); l.setRevokedAt(Instant.now()); }
        return l;
    }

    @Transactional
    public Lot rollout(long id, int percent) {
        if (percent < 0 || percent > 100) throw ApiException.badRequest("Le pourcentage doit être entre 0 et 100");
        Lot l = get(id);
        l.setRolloutPercent(percent);
        return l;
    }

    @Transactional
    public void delete(long id) {
        Lot l = get(id);
        repo.delete(l);
        repo.flush();
        try { Files.deleteIfExists(file(l)); } catch (IOException e) { log.warn("could not delete {}", l.getFileName()); }
    }

    // ---------------------------------------------------------------- devices

    /**
     * The signed catalog: for each (feature, scope) the highest published, non-revoked version this device may see (beta devices
     * also see the stable lots; a partial rollout only reaches the devices whose stable hash falls under the percentage, no
     * deviceId = full rollouts only).
     */
    public LotCatalog catalog(String feature, String channel, String deviceId) {
        if (feature != null && !feature.isBlank() && !FEATURES.contains(feature)) throw ApiException.badRequest("feature : « learn », « quiz » ou « langues » attendu");
        String f = feature == null || feature.isBlank() ? null : feature;
        if (channel == null || channel.isBlank()) channel = "stable";
        if (!CHANNELS.contains(channel)) throw ApiException.badRequest("canal : « stable » ou « beta » attendu");
        if (deviceId != null && !deviceId.isBlank() && !DEVICE_ID.matcher(deviceId).matches())
            throw ApiException.badRequest("deviceId : 8 à 64 caractères [A-Za-z0-9_-]");
        if (!signer.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Lots indisponibles : clé de signature non configurée");
        String id = deviceId == null || deviceId.isBlank() ? null : deviceId;
        List<String> channels = "beta".equals(channel) ? List.of("stable", "beta") : List.of("stable");
        List<Lot> candidates = f == null ? repo.findByPublishedTrueAndRevokedFalseAndChannelIn(channels)
                : repo.findByPublishedTrueAndRevokedFalseAndFeatureAndChannelIn(f, channels);
        Map<String, Lot> best = new HashMap<>();
        candidates.stream().filter(l -> inRollout(l, id))
                .sorted(Comparator.comparingInt(Lot::getVersion))
                .forEach(l -> best.put(l.getFeature() + "/" + l.getScope(), l));
        List<LotCatalog.Entry> entries = best.values().stream()
                .sorted(Comparator.comparing(Lot::getFeature).thenComparing(Lot::getScope))
                .map(l -> new LotCatalog.Entry(l.getFeature(), l.getScope(), l.getVersion(), l.getSizeBytes(), l.getSha256(), l.getTitle(), l.getMinAppVersion()))
                .toList();
        LotCatalog c = new LotCatalog(channel, f, AT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS).atZone(CastbridgeApplication.ZONE)), entries, null, null);
        return c.withSignature(signer.keyId(), signer.signBase64(c.canonicalPayload()));
    }

    /** The lot a device downloads: only an existing, published, non-revoked version. */
    public Optional<Lot> servable(String feature, String scope, int version) {
        return repo.findByFeatureAndScopeAndVersion(feature, scope, version).filter(Lot::isPublished);
    }

    static boolean inRollout(Lot l, String deviceId) {
        if (l.getRolloutPercent() >= 100) return true;
        if (l.getRolloutPercent() <= 0 || deviceId == null) return false;
        return rolloutBucket(deviceId, l.getFeature() + "/" + l.getScope(), l.getVersion()) < l.getRolloutPercent();
    }

    /** 0..99, stable for a device and a lot version (another version reshuffles who goes first). */
    static int rolloutBucket(String deviceId, String lot, int version) {
        byte[] h = LotCatalog.sha256((deviceId + ":" + lot + ":" + version).getBytes(StandardCharsets.UTF_8));
        long v = ((h[0] & 0xffL) << 24) | ((h[1] & 0xffL) << 16) | ((h[2] & 0xffL) << 8) | (h[3] & 0xffL);
        return (int) (v % 100);
    }
}
