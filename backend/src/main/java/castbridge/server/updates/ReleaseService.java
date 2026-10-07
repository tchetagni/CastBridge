package castbridge.server.updates;

import castbridge.server.CastbridgeApplication;
import castbridge.server.config.CastbridgeProperties;
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

/** Publishing, managing and choosing app releases. */
@Service
public class ReleaseService {
    private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);

    public static final Set<String> APPS = Set.of("tv", "phone");
    public static final Set<String> ABIS = Set.of("armeabi-v7a", "arm64-v8a", "x86", "x86_64", "universal");
    public static final Set<String> CHANNELS = Set.of("stable", "beta");
    private static final Pattern VERSION_NAME = Pattern.compile("[\\p{L}\\p{N} ._+\\-()]{1,64}");
    private static final Pattern DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final DateTimeFormatter PUBLISHED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ReleaseRepository repo;
    private final CastbridgeProperties props;
    private final ManifestSigner signer;
    private final UpdatePolicyRepository policies;

    public ReleaseService(ReleaseRepository repo, UpdatePolicyRepository policies, CastbridgeProperties props, ManifestSigner signer) {
        this.repo = repo;
        this.policies = policies;
        this.props = props;
        this.signer = signer;
    }

    /** Form of POST /api/v1/admin/releases (the APK itself comes as a stream). */
    public record NewRelease(String app, String abi, int versionCode, String versionName, String notes, String channel,
                             Boolean mandatory, Integer minSdk, Integer rolloutPercent) {}

    public record Published(Release release, String inspection) {}

    /** The storage folders must exist before the first upload (multipart spool, incoming files). */
    @jakarta.annotation.PostConstruct
    void prepareStorage() {
        try {
            Files.createDirectories(props.storageDir().resolve(".multipart"));
            Files.createDirectories(props.storageDir().resolve(".incoming"));
        } catch (IOException e) {
            log.error("APK storage {} is not writable: uploads will fail", props.storageDir());
        }
    }

    // ---------------------------------------------------------------- admin

    @Transactional
    public Published publish(NewRelease form, String originalName, InputStream apk) {
        List<String> errors = new ArrayList<>();
        if (!APPS.contains(form.app())) errors.add("app : « tv » ou « phone » attendu");
        if (!ABIS.contains(form.abi())) errors.add("abi : armeabi-v7a, arm64-v8a, x86, x86_64 ou universal attendu");
        String channel = form.channel() == null || form.channel().isBlank() ? "stable" : form.channel();
        if (!CHANNELS.contains(channel)) errors.add("canal : « stable » ou « beta » attendu");
        if (form.versionCode() <= 0) errors.add("versionCode : entier positif attendu");
        if (form.versionName() == null || !VERSION_NAME.matcher(form.versionName()).matches())
            errors.add("versionName : 1 à 64 caractères (lettres, chiffres, espace, . _ + - ( ))");
        if (form.notes() != null && form.notes().length() > 4000) errors.add("notes : 4000 caractères au maximum");
        if (form.minSdk() != null && (form.minSdk() < 1 || form.minSdk() > 100)) errors.add("minSdk : entre 1 et 100");
        int rollout = form.rolloutPercent() == null ? 100 : form.rolloutPercent();
        if (rollout < 0 || rollout > 100) errors.add("rollout : entre 0 et 100");
        if (originalName != null && !originalName.toLowerCase().endsWith(".apk")) errors.add("fichier : un .apk est attendu");
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Publication refusée", errors);
        if (repo.existsByAppAndAbiAndChannelAndVersionCode(form.app(), form.abi(), channel, form.versionCode()))
            throw ApiException.conflict("Une version " + form.versionCode() + " existe déjà pour " + form.app() + "/" + form.abi() + "/" + channel);

        Path incoming = props.storageDir().resolve(".incoming");
        Path tmp = incoming.resolve(UUID.randomUUID() + ".part");
        try {
            Files.createDirectories(incoming);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long size;
            byte[] head = new byte[4];
            try (InputStream in = new DigestInputStream(apk, sha); OutputStream out = Files.newOutputStream(tmp)) {
                size = in.transferTo(out);
            }
            try (InputStream in = Files.newInputStream(tmp)) {
                if (in.readNBytes(head, 0, 4) < 4 || head[0] != 'P' || head[1] != 'K')
                    throw ApiException.badRequest("Le fichier n'est pas un APK (archive ZIP attendue)");
            }
            String sha256 = HexFormat.of().formatHex(sha.digest());

            Optional<ApkInspector.ApkInfo> info = ApkInspector.inspect(tmp);
            String inspection = "illisible : informations du formulaire utilisées";
            Integer minSdk = form.minSdk();
            String packageName = null;
            if (info.isPresent()) {
                ApkInspector.ApkInfo i = info.get();
                List<String> mismatch = new ArrayList<>();
                String expected = props.packages().of(form.app());
                if (i.packageName() != null && expected != null && !expected.isBlank() && !expected.equals(i.packageName()))
                    mismatch.add("paquet de l'APK « " + i.packageName() + " », attendu « " + expected + " » pour l'app " + form.app());
                if (i.versionCode() != null && i.versionCode() != form.versionCode())
                    mismatch.add("versionCode de l'APK " + i.versionCode() + " ≠ formulaire " + form.versionCode());
                if (!mismatch.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "L'APK ne correspond pas au formulaire", mismatch);
                packageName = i.packageName();
                if (minSdk == null) minSdk = i.minSdk();
                inspection = "vérifié : " + i.packageName() + " " + i.versionCode() + " (" + i.versionName() + ")";
            }

            // always "castbridge-<app>-…": the reserved name "latest.apk" (stable address /dl/{app}/latest.apk) can never be a published file
            String fileName = "castbridge-%s-%s-%d-%s-%s.apk".formatted(form.app(), safe(form.versionName()), form.versionCode(),
                    form.abi(), sha256.substring(0, 8));
            Path dir = props.storageDir().resolve(form.app());
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }

            Release r = new Release();
            r.setApp(form.app());
            r.setAbi(form.abi());
            r.setChannel(channel);
            r.setVersionCode(form.versionCode());
            r.setVersionName(form.versionName());
            r.setNotes(form.notes() == null ? "" : form.notes().strip());
            r.setPackageName(packageName);
            r.setMinSdk(minSdk);
            r.setMandatory(Boolean.TRUE.equals(form.mandatory()));
            r.setFileName(fileName);
            r.setSha256(sha256);
            r.setSizeBytes(size);
            r.setRolloutPercent(rollout);
            r.setPublishedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
            Release saved = repo.save(r);
            log.info("release published: {} {} {} {} ({} bytes)", r.getApp(), r.getAbi(), r.getChannel(), r.getVersionCode(), size);
            return new Published(saved, inspection);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible d'enregistrer le fichier sur le serveur");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // a stale .part in .incoming is harmless
            }
        }
    }

    public List<Release> list(String app) {
        return app == null || app.isBlank() ? repo.findAllByOrderByAppAscVersionCodeDescIdDesc() : repo.findByAppOrderByVersionCodeDescIdDesc(app);
    }

    public Release get(long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Version " + id + " introuvable"));
    }

    @Transactional
    public void delete(long id) {
        Release r = get(id);
        repo.delete(r);
        repo.flush();
        if (repo.countByFileName(r.getFileName()) == 0) {
            try {
                Files.deleteIfExists(file(r));
            } catch (IOException e) {
                log.warn("could not delete {}", r.getFileName());
            }
        }
    }

    @Transactional
    public Release rollout(long id, int percent) {
        if (percent < 0 || percent > 100) throw ApiException.badRequest("Le pourcentage doit être entre 0 et 100");
        Release r = get(id);
        r.setRolloutPercent(percent);
        return r;
    }

    @Transactional
    public Release revoke(long id) {
        Release r = get(id);
        if (!r.isRevoked()) {
            r.setRevoked(true);
            r.setRevokedAt(Instant.now());
        }
        return r;
    }

    public Path file(Release r) {
        return props.storageDir().resolve(r.getApp()).resolve(r.getFileName());
    }

    public Optional<Release> byFile(String app, String fileName) {
        return repo.findByAppAndFileName(app, fileName);
    }

    // ---------------------------------------------------------------- public download addresses

    /**
     * Order in which a public download that names no ABI looks for an APK (the device is unknown). A 64-bit TV runs the 32-bit APK and the
     * TVs of the fleet are armeabi-v7a, so that comes first; the universal APK next; the 64-bit one last (a 32-bit TV cannot run it).
     * An ABI that is not listed (x86, x86_64) is never chosen unless it is asked for.
     */
    private static final Map<String, List<String>> DOWNLOAD_ABIS = Map.of(
            "tv", List.of("armeabi-v7a", "universal", "arm64-v8a"),
            "phone", List.of("universal", "arm64-v8a", "armeabi-v7a"));

    /** The name people know the app by. */
    public static String appLabel(String app) {
        return "tv".equals(app) ? "CastBridge-TV" : "CastBridge";
    }

    /**
     * The release behind the stable public address {@code /dl/{app}/latest.apk} and behind the page {@code /telecharger}: among the
     * <b>stable</b>, <b>not revoked</b> releases at <b>100 %</b> rollout, the one with the highest versionCode; for the same versionCode,
     * the most preferred ABI. With {@code abi} null or blank the order is {@link #DOWNLOAD_ABIS}; with an ABI, that ABI then the universal
     * APK (never another architecture). Beta releases, revoked ones and partial rollouts are never offered: this is a public address,
     * not a device that can be told apart by its identifier.
     */
    public Optional<Release> latestForDownload(String app, String abi) {
        if (!APPS.contains(app)) throw ApiException.notFound("Application inconnue : « tv » ou « phone » attendu");
        String wanted = abi == null || abi.isBlank() ? null : abi.trim();
        if (wanted != null && !ABIS.contains(wanted))
            throw ApiException.badRequest("abi : " + String.join(", ", ABIS.stream().sorted().toList()) + " attendu");
        List<String> order = wanted == null ? DOWNLOAD_ABIS.get(app)
                : "universal".equals(wanted) ? List.of("universal") : List.of(wanted, "universal");
        return repo.findByAppAndRevokedFalseAndChannelInAndAbiInOrderByVersionCodeDesc(app, List.of("stable"), order).stream()
                .filter(r -> r.getRolloutPercent() >= 100)
                .min(Comparator.comparingInt((Release r) -> -r.getVersionCode())
                        .thenComparingInt(r -> order.indexOf(r.getAbi()))
                        .thenComparingLong(r -> r.getId() == null ? 0L : -r.getId()));
    }

    // ---------------------------------------------------------------- devices

    /**
     * The newest release this device may install, or empty if it is up to date. Beta devices also get stable releases.
     * {@code deviceAbis} = the ABIs the device supports, preferred first (Build.SUPPORTED_ABIS): among the APKs of the
     * newest version, the one for the most preferred ABI wins, the universal APK is the fallback. A partial rollout
     * only reaches the devices whose stable hash falls under the percentage (no deviceId = full rollouts only).
     */
    public Optional<UpdateManifest> latest(String app, List<String> deviceAbis, String channel, int currentVersionCode, String deviceId,
                                           Integer sdk, String baseUrl) {
        if (!APPS.contains(app)) throw ApiException.notFound("Application inconnue : " + app);
        List<String> pref = deviceAbis == null ? List.of() : deviceAbis.stream().map(String::trim)
                .filter(a -> ABIS.contains(a) && !"universal".equals(a)).distinct().toList();
        if (deviceAbis != null && !deviceAbis.isEmpty() && pref.isEmpty() && deviceAbis.stream().noneMatch(a -> "universal".equals(a.trim())))
            throw ApiException.badRequest("abi : " + String.join(", ", ABIS.stream().sorted().toList()) + " attendu");
        if (channel == null || channel.isBlank()) channel = "stable";
        if (!CHANNELS.contains(channel)) throw ApiException.badRequest("canal : « stable » ou « beta » attendu");
        if (deviceId != null && !deviceId.isBlank() && !DEVICE_ID.matcher(deviceId).matches())
            throw ApiException.badRequest("deviceId : 8 à 64 caractères [A-Za-z0-9_-]");
        if (!signer.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Mises à jour indisponibles : clé de signature non configurée");

        List<String> channels = "beta".equals(channel) ? List.of("stable", "beta") : List.of("stable");
        List<String> abis = new ArrayList<>(pref);
        abis.add("universal");
        String id = deviceId == null || deviceId.isBlank() ? null : deviceId;
        int minSupported = channels.stream().mapToInt(c -> minSupported(app, c)).max().orElse(0);
        // a device below the oldest supported version gets the update whatever the rollout percentage
        boolean forced = currentVersionCode < minSupported;
        List<Release> eligible = repo.findByAppAndRevokedFalseAndChannelInAndAbiInOrderByVersionCodeDesc(app, channels, abis).stream()
                .filter(r -> sdk == null || r.getMinSdk() == null || r.getMinSdk() <= sdk)
                .filter(r -> forced || inRollout(r, id))
                .toList();
        Optional<Release> best = eligible.stream()
                .filter(r -> r.getVersionCode() > currentVersionCode)
                .min(java.util.Comparator.comparingInt((Release r) -> -r.getVersionCode())
                        .thenComparingInt(r -> abis.indexOf(r.getAbi()))
                        .thenComparing(r -> "beta".equals(r.getChannel()) ? 1 : 0));
        if (best.isEmpty()) return Optional.empty();
        Release r = best.get();
        boolean mandatory = forced || eligible.stream().anyMatch(x -> x.isMandatory()
                && x.getVersionCode() > currentVersionCode && x.getVersionCode() <= r.getVersionCode());
        String url = baseUrl + "/dl/" + r.getApp() + "/" + r.getFileName();
        String publishedAt = PUBLISHED_AT.format(r.getPublishedAt().atZone(CastbridgeApplication.ZONE));
        UpdateManifest m = new UpdateManifest(r.getApp(), r.getChannel(), r.getAbi(), r.getVersionCode(), r.getVersionName(), url,
                r.getSha256(), r.getSizeBytes(), r.getMinSdk(), r.getNotes() == null ? "" : r.getNotes(), mandatory, minSupported,
                publishedAt, null, null);
        return Optional.of(m.withSignature(signer.keyId(), signer.signBase64(m.canonicalPayload())));
    }

    // ---------------------------------------------------------------- min supported version (forced updates)

    public int minSupported(String app, String channel) {
        return policies.findById(new UpdatePolicy.Key(app, channel)).map(UpdatePolicy::getMinSupportedVersionCode).orElse(0);
    }

    public List<UpdatePolicy> policies() { return policies.findAllByOrderByAppAscChannelAsc(); }

    @Transactional
    public UpdatePolicy setMinSupported(String app, String channel, int minSupportedVersionCode) {
        if (!APPS.contains(app)) throw ApiException.notFound("Application inconnue : " + app);
        if (!CHANNELS.contains(channel)) throw ApiException.badRequest("canal : « stable » ou « beta » attendu");
        if (minSupportedVersionCode < 0) throw ApiException.badRequest("minSupportedVersionCode : entier positif ou nul attendu");
        UpdatePolicy p = policies.findById(new UpdatePolicy.Key(app, channel)).orElseGet(() -> new UpdatePolicy(app, channel));
        p.setMinSupportedVersionCode(minSupportedVersionCode);
        p.setUpdatedAt(Instant.now());
        return policies.save(p);
    }

    static boolean inRollout(Release r, String deviceId) {
        if (r.getRolloutPercent() >= 100) return true;
        if (r.getRolloutPercent() <= 0 || deviceId == null) return false;
        return rolloutBucket(deviceId, r.getApp(), r.getVersionCode()) < r.getRolloutPercent();
    }

    /** 0..99, stable for a device and a version (another version reshuffles who goes first). */
    static int rolloutBucket(String deviceId, String app, int versionCode) {
        byte[] h = ManifestSigner.sha256((deviceId + ":" + app + ":" + versionCode).getBytes(StandardCharsets.UTF_8));
        long v = ((h[0] & 0xffL) << 24) | ((h[1] & 0xffL) << 16) | ((h[2] & 0xffL) << 8) | (h[3] & 0xffL);
        return (int) (v % 100);
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
