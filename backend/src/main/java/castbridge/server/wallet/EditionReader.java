package castbridge.server.wallet;

import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.licenses.Envelope;
import castbridge.server.licenses.EnvelopeVerifier;
import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.WireActivation;
import castbridge.server.wallet.core.Edition;
import castbridge.server.wallet.core.EditionSpan;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ce que l'API lit dans les activations {@code cbx1} jointes à {@code sync} (conception W22 § 1.2, § 3.2) : l'IDENTITÉ (code d'appareil) et l'édition ESSAI (durée lue dans la clé).
 * Une activation de production sert seulement à prouver l'identité : sa durée, son caractère illimité, la grâce et la révocation sont l'état de la LICENCE (voir {@link LicenseFacts}).
 * Le droit {@code super} donne une édition SUPER (aucune attribution automatique). N'utilise que les parties pures du module des licences ({@link Envelope}, {@link WireActivation},
 * {@link DeviceIdentity}, {@link LicenseKeyring#verify}) : aucun bean des licences n'est requis, le module peut rester éteint. Horloge : celle du serveur, passée par l'appelant.
 * Fermé au moindre doute : une activation douteuse n'apporte rien. La fenêtre d'installation de 48 h n'est pas jugée (elle ne sert qu'à INSTALLER, comme dans le service de jeu).
 */
public class EditionReader {
    private static final Logger log = LoggerFactory.getLogger(EditionReader.class);
    public static final int MAX_TOKENS = 4;
    public static final int MAX_TOKEN_LENGTH = 8_192;
    /** Une activation émise plus de 24 h dans le futur du serveur : l'horloge du serveur ou l'activation est fausse ; dans le doute, refus. */
    public static final long CLOCK_SKEW_MS = 24L * 3_600_000L;
    private static final long DAY_MS = 86_400_000L;
    private static final long MAX_TRIAL_MS = 365L * DAY_MS;

    /** Pourquoi une activation n'a rien apporté (journal et tests ; jamais montré tel quel). */
    public enum Rejection { OTHER_TV, BAD_SIGNATURE, UNKNOWN_KEY, REVOKED, KEY_NOT_ALLOWED, MALFORMED, WRONG_SUBJECT, BAD_RIGHTS, CLOCK, TOO_MANY }

    /**
     * @param identity        code d'appareil prouvé par au moins une activation acceptée, sinon null
     * @param spans           intervalles d'édition ESSAI et SUPER lus dans les clés (jamais PRODUCTION ni ILLIMITÉE)
     * @param productionKey   une activation de production acceptée (preuve d'identité ; la licence dit le reste)
     * @param clockDoubt      une activation datée de plus de 24 h dans le futur
     * @param firstTrialStart début de la plus ancienne clé d'essai acceptée, sinon null
     * @param rejected        un motif par activation écartée
     */
    public record Reading(String identity, List<EditionSpan> spans, boolean productionKey, boolean superKey, boolean clockDoubt, Instant firstTrialStart, List<Rejection> rejected) {
        public boolean accepted() { return identity != null; }
    }

    private final Map<String, EnvelopeVerifier.TrustedKey> keys;
    private final Supplier<EnvelopeVerifier.Revocations> revocations;

    public EditionReader(Map<String, EnvelopeVerifier.TrustedKey> keys, Supplier<EnvelopeVerifier.Revocations> revocations) {
        this.keys = Map.copyOf(keys);
        this.revocations = revocations;
    }

    /** Clés de {@code castbridge.wallet.trusted-keys} (format des licences) et fichier de révocations facultatif. */
    public static EditionReader of(WalletProperties props) { return of(props, EnvelopeVerifier.Revocations::none); }

    /** Idem, avec une source de révocations supplémentaire (celles du module des licences, {@code lic_revocation}) : l'union des deux listes s'applique. */
    public static EditionReader of(WalletProperties props, Supplier<EnvelopeVerifier.Revocations> extra) {
        Map<String, EnvelopeVerifier.TrustedKey> keys = new HashMap<>();
        for (String entry : props.trustedKeys()) {
            try {
                String[] p = entry.trim().split(":");
                if (p.length != 3) throw new IllegalArgumentException("format");
                byte[] pub = Base64.getDecoder().decode(p[1].trim());
                if (pub.length != 32) throw new IllegalArgumentException("taille de clé");
                Set<SignerScope> scopes = EnumSet.noneOf(SignerScope.class);
                for (String s : p[2].split("\\+")) scopes.add(SignerScope.valueOf(s.trim()));
                String kid = LicenseKeyring.kidOf(pub);
                keys.put(kid, new EnvelopeVerifier.TrustedKey(kid, pub, scopes));
            } catch (RuntimeException e) {
                log.warn("wallet : une entrée de castbridge.wallet.trusted-keys est ignorée (attendu nom:clé publique base64:PORTEE+PORTEE)");
            }
        }
        RevocationFile file = new RevocationFile(props.revocationsFile(), keys);
        return new EditionReader(keys, () -> file.get().merge(extra.get()));
    }

    /** Liste de révocations lue d'un fichier (un jeton {@code cbx1} de révocation par ligne, chacun vérifié par une clé de confiance portant REVOKE) ; relue quand le fichier change. */
    static final class RevocationFile implements Supplier<EnvelopeVerifier.Revocations> {
        private final Path file;
        private final Map<String, EnvelopeVerifier.TrustedKey> keys;
        private long loadedAt = Long.MIN_VALUE;
        private long loadedSize = -1;
        private EnvelopeVerifier.Revocations current = EnvelopeVerifier.Revocations.none();

        RevocationFile(String path, Map<String, EnvelopeVerifier.TrustedKey> keys) {
            this.file = path == null || path.isBlank() ? null : Path.of(path);
            this.keys = keys;
        }

        @Override
        public synchronized EnvelopeVerifier.Revocations get() {
            if (file == null) return EnvelopeVerifier.Revocations.none();
            try {
                if (!Files.isRegularFile(file)) return current = EnvelopeVerifier.Revocations.none();
                long m = Files.getLastModifiedTime(file).toMillis(), size = Files.size(file);
                if (m == loadedAt && size == loadedSize) return current;
                EnvelopeVerifier.Ring ring = new EnvelopeVerifier.Ring();
                keys.values().forEach(ring::add);
                EnvelopeVerifier v = new EnvelopeVerifier(ring, EnvelopeVerifier.Revocations.none(), new EnvelopeVerifier.SeqState(), "tv");
                EnvelopeVerifier.Revocations all = EnvelopeVerifier.Revocations.none();
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) continue;
                    EnvelopeVerifier.Revocations r = v.verifyRevocation(line.trim());
                    if (r != null) all = all.merge(r);
                    else log.warn("wallet : une ligne du fichier de révocations est ignorée (illisible ou non signée par une clé de confiance)");
                }
                loadedAt = m;
                loadedSize = size;
                return current = all;
            } catch (IOException | RuntimeException e) {
                log.warn("wallet : fichier de révocations illisible ({}) : dernière liste connue conservée", e.getClass().getSimpleName());
                return current;
            }
        }
    }

    /** Lit les activations d'une TV : {@code deviceCode} est celui que la TV annonce (déjà validé par l'appelant). */
    public Reading read(String deviceCode, List<String> tokens, Instant now) {
        String code = DeviceIdentity.parseCode(deviceCode);
        if (code == null) throw new IllegalArgumentException("Code d'appareil invalide");
        List<Rejection> rejected = new ArrayList<>();
        if (tokens == null || tokens.isEmpty()) return new Reading(null, List.of(), false, false, false, null, rejected);
        if (tokens.size() > MAX_TOKENS) {
            rejected.add(Rejection.TOO_MANY);
            return new Reading(null, List.of(), false, false, false, null, rejected);
        }
        EnvelopeVerifier.Revocations rev = revocations.get();
        List<EditionSpan> spans = new ArrayList<>();
        boolean production = false, superKey = false, clock = false;
        Instant firstTrial = null;
        for (String token : tokens) {
            Rejection why = null;
            WireActivation.Fields f = null;
            Envelope env = null;
            if (token == null || token.length() > MAX_TOKEN_LENGTH) why = Rejection.MALFORMED;
            else if ((env = Envelope.decode(token)) == null || !env.type().equals(WireActivation.TYPE) || (f = WireActivation.fieldsOf(env)) == null) why = Rejection.MALFORMED;
            if (why == null) why = check(env, f, code, rev);
            if (why == null && f.issuedAt() > now.toEpochMilli() + CLOCK_SKEW_MS) {
                why = Rejection.CLOCK;
                clock = true;
            }
            if (why != null) {
                rejected.add(why);
                continue;
            }
            boolean trial = f.kind().equals("trial");
            Instant issued = Instant.ofEpochMilli(f.issuedAt() > 0 ? f.issuedAt() : f.notBefore());
            if (f.rights().stream().anyMatch(WireActivation::isSuper)) {
                spans.add(new EditionSpan(Edition.SUPER, issued, null));
                superKey = true;
            }
            if (trial) {
                EditionSpan s = trialSpan(f);
                if (s == null) {
                    rejected.add(Rejection.BAD_RIGHTS);
                    continue;
                }
                spans.add(s);
                if (firstTrial == null || s.start().isBefore(firstTrial)) firstTrial = s.start();
            } else {
                production = true;
            }
        }
        // l'identité est prouvée dès qu'une activation (essai ou production) a été acceptée ; horloge douteuse : rien n'est accordé, tout est refusé
        if (clock) return new Reading(null, List.of(), false, false, true, null, List.copyOf(rejected));
        boolean accepted = production || firstTrial != null;
        return new Reading(accepted ? code : null, List.copyOf(spans), production, superKey, false, firstTrial, List.copyOf(rejected));
    }

    private Rejection check(Envelope env, WireActivation.Fields a, String code, EnvelopeVerifier.Revocations rev) {
        EnvelopeVerifier.TrustedKey key = keys.get(a.kid());
        if (key == null) return Rejection.UNKNOWN_KEY;
        if (rev.keys().contains(a.kid())) return Rejection.REVOKED;
        boolean sigOk;
        try {
            sigOk = LicenseKeyring.verify(key.publicKey(), env.payload().getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(env.signature()));
        } catch (RuntimeException e) {
            sigOk = false;
        }
        if (!sigOk) return Rejection.BAD_SIGNATURE;
        boolean trial = a.kind().equals("trial");
        boolean allowed = trial ? key.allows(SignerScope.ISSUE_TRIAL) : key.allows(SignerScope.ISSUE_PRODUCTION) || key.allows(SignerScope.REACTIVATE);
        if (!allowed) return Rejection.KEY_NOT_ALLOWED;
        if (a.rights().stream().anyMatch(WireActivation::isSuper) && !key.allows(SignerScope.SUPER_UNLIMITED)) return Rejection.KEY_NOT_ALLOWED;
        if (!a.subject().equals("tv")) return Rejection.WRONG_SUBJECT;
        if (trial && !a.rights().stream().allMatch(WireActivation::isTrialRight)) return Rejection.BAD_RIGHTS;
        if (!DeviceIdentity.code(a.factors()).equals(code)) return Rejection.OTHER_TV;
        if (rev.seatRevoked(a)) return Rejection.REVOKED;
        return null;
    }

    /** {@code [from, to)} du droit {@code usage}, sinon 30 jours depuis l'émission (même règle implicite que le cœur) ; null si la durée est hors bornes (1 ms à 365 jours). */
    private static EditionSpan trialSpan(WireActivation.Fields a) {
        long from, to;
        String usage = a.rights().stream().filter(WireActivation::isUsage).findFirst().orElse(null);
        if (usage != null) {
            String[] p = usage.split("\\|", -1);
            from = Long.parseLong(p[2]);
            to = Long.parseLong(p[3]);
        } else {
            from = a.issuedAt() > 0 ? a.issuedAt() : a.notBefore();
            to = WireActivation.implicitUsageEnd(a);
        }
        if (from <= 0 || to <= from || to - from > MAX_TRIAL_MS) return null;
        return new EditionSpan(Edition.TRIAL, Instant.ofEpochMilli(from), Instant.ofEpochMilli(to));
    }
}
