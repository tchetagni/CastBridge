package castbridge.server.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All the settings, from environment variables (12-factor). See backend/README.md for the variable names.
 *
 * @param adminToken    bearer token of the admin API (CASTBRIDGE_ADMIN_TOKEN, at least 32 characters, else the admin API is off)
 * @param web           admin web interface (/admin): initial account
 * @param signing       Ed25519 key signing the update manifests
 * @param storageDir    where the APK files live (a Docker volume)
 * @param publicBaseUrl external base URL used in the download links ("https://example.org/castbridge"); empty = from the request
 * @param rateLimit     per-IP limit on the public routes
 * @param packages      expected Android package of each app (checked against the uploaded APK)
 * @param quiz          question bank settings
 * @param devices       device tracking and retention
 * @param geo           approximate location of devices from their public IP
 * @param catalog       the signed bundle catalogue served to the owner's tools
 * @param freeContent   the free content archive (CC BY-SA, public download)
 */
@ConfigurationProperties(prefix = "castbridge")
public record CastbridgeProperties(
        String adminToken,
        Web web,
        Signing signing,
        Path storageDir,
        String publicBaseUrl,
        RateLimit rateLimit,
        Packages packages,
        Quiz quiz,
        Devices devices,
        Geo geo,
        Catalog catalog,
        FreeContent freeContent) {

    public CastbridgeProperties {
        if (web == null) web = new Web(null, null, false);
        if (signing == null) signing = new Signing(null, null);
        if (storageDir == null) storageDir = Path.of("/data/apk");
        if (publicBaseUrl == null) publicBaseUrl = "";
        if (rateLimit == null) rateLimit = new RateLimit(120, 60);
        if (packages == null) packages = new Packages("castbridge.receiver", "castbridge.sender");
        if (quiz == null) quiz = new Quiz(true, 365, null);
        if (quiz.packsDir() == null) quiz = new Quiz(quiz.seed(), quiz.tombstoneDays(), storageDir.resolve("quiz-packs"));
        if (devices == null) devices = new Devices(365, 30, 30, 180, 15, 900);
        if (geo == null) geo = new Geo(null, null);
        if (catalog == null) catalog = new Catalog(null);
        if (catalog.bundlesFile() == null || catalog.bundlesFile().toString().isBlank()) catalog = new Catalog(storageDir.resolve("lots").resolve("bundles-catalog.json"));
        if (freeContent == null) freeContent = new FreeContent(null);
        if (freeContent.file() == null || freeContent.file().toString().isBlank()) freeContent = new FreeContent(storageDir.resolve("lots").resolve("castbridge-contenus-libres.zip"));
    }

    /**
     * @param adminUser      login of the initial admin account (created at start-up if missing)
     * @param adminPassword  its password (at least 12 characters), only used to create it (or reset it, see below)
     * @param resetPassword  true = overwrite the stored password with adminPassword at start-up (forgotten password)
     */
    public record Web(String adminUser, String adminPassword, boolean resetPassword) {}

    /** @param key base64 PKCS#8 DER or PEM text of the private key; @param keyFile path of a PEM/DER file (Docker secret) */
    public record Signing(String key, Path keyFile) {}

    /** @param perMinute sustained requests per minute and IP; @param burst extra requests allowed at once */
    public record RateLimit(int perMinute, int burst) {}

    public record Packages(String tv, String phone) {
        public String of(String app) {
            return "tv".equals(app) ? tv : "phone".equals(app) ? phone : null;
        }
    }

    /**
     * @param seed          import the bundled bank when the table is empty
     * @param tombstoneDays how long deletions are remembered for sync
     * @param packsDir      folder with the question packs built by tools/quiz-bank (catalog.json + *.quiz.zip), served to the TVs;
     *                      CASTBRIDGE_QUIZ_PACKS_DIR, default {storage-dir}/quiz-packs
     */
    public record Quiz(boolean seed, int tombstoneDays, Path packsDir) {}

    /**
     * @param retentionDays     devices not seen for that long are forgotten
     * @param heartbeatDays     detailed heartbeats kept that long (then only the daily aggregate)
     * @param ipDays            raw IP addresses kept that long
     * @param crashDays         crash reports kept that long
     * @param onlineMinutes     "online" = last contact within that many minutes
     * @param heartbeatSeconds  heartbeat period asked of the apps
     */
    public record Devices(int retentionDays, int heartbeatDays, int ipDays, int crashDays, int onlineMinutes, int heartbeatSeconds) {}

    /**
     * @param countryHeader request header carrying the ISO country set by the reverse proxy (e.g. "CF-IPCountry"), optional
     * @param databaseFile  optional MaxMind GeoLite2 City or Country database (.mmdb) for country and city
     */
    public record Geo(String countryHeader, Path databaseFile) {}

    /** @param bundlesFile the bundle catalogue signed OFFLINE by the owner (tools/trial-edition sign-catalog); CASTBRIDGE_BUNDLES_CATALOG_FILE, default {storage-dir}/lots/bundles-catalog.json */
    public record Catalog(Path bundlesFile) {}

    /** @param file the ZIP archive of free content (CC BY-SA); CASTBRIDGE_FREE_CONTENT_FILE, default {storage-dir}/lots/castbridge-contenus-libres.zip */
    public record FreeContent(Path file) {}
}
