package castbridge.server.licenses;

import castbridge.server.licenses.DeviceIdentity.Factor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** The activation, type {@code activation} of the {@link Envelope} {@code cbx1} (docs/ACTIVATION-FORMAT.md § 3.3), plus a strict decoder (tests, inspection). */
public final class WireActivation {
    public static final String TYPE = "activation";
    public static final String PREFIX = Envelope.PREFIX;
    public static final String TRIAL_LICENSE = "trial";
    public static final long DAY_MS = 86_400_000L;
    /** An activation can be installed during 48 h from its creation (docs/ACTIVATION-FORMAT.md § Durées), for everybody. */
    public static final long HOUR_MS = 3_600_000L;
    public static final int MAX_WINDOW_HOURS = 48;
    /** The `super` right (SUPER_UNLIMITED): reads and unlocks everything, rentals included, for good; only a key holding SUPER_UNLIMITED may sign it (never the server's). */
    /** The usage ceiling line (`usage|duree|from|to`): the only « right » a trial key may carry; it grants nothing by itself. */
    public static boolean isUsage(String rightLine) {
        return rightLine.startsWith("usage|");
    }

    /** The reserved product of the trial window (RentalLines.TRIAL_PRODUCT): the only rental a trial key may carry. */
    public static final String TRIAL_PRODUCT = "essai";
    public static final int TRIAL_DAYS = 3;
    public static final int TRIAL_USAGE_MINUTES = 12 * 60;
    private static final Pattern BOX = Pattern.compile("^[A-Za-z0-9_;:+-]{0,4096}$");

    public static boolean isRental(String rightLine) {
        return rightLine.startsWith("rental|");
    }

    /** What a TRIAL key may carry: usage ceilings and the reserved trial rental (never a purchase, a subscription or another rental). */
    public static boolean isTrialRight(String rightLine) {
        return isUsage(rightLine) || (isRental(rightLine) && rightLine.split("\\|", -1)[1].equals(TRIAL_PRODUCT));
    }

    /** Why a (well-formed) rental line is out of bounds, or null (mirror of RentalLines.bounds). */
    public static String rentalBounds(String line) {
        String[] f = line.split("\\|", -1);
        long startsAt = Long.parseLong(f[3]), period = Long.parseLong(f[4]), grace = Long.parseLong(f[6]);
        int days = Integer.parseInt(f[5]), maxUsage = Integer.parseInt(f[7]), maxConcurrent = Integer.parseInt(f[8]);
        if (f[1].equals(TRIAL_PRODUCT) && (days < 1 || days > TRIAL_DAYS || maxUsage < 1 || maxUsage > TRIAL_USAGE_MINUTES || grace != 0)) return "trial window";
        if (days < 1 || days > 366) return "days";
        if (grace < 0 || grace > 30 * DAY_MS) return "grace";
        if (maxUsage < 0 || maxUsage > 366 * 24 * 60) return "max usage";
        if (maxConcurrent < 0 || maxConcurrent > 20) return "max concurrent";
        if (startsAt <= 0 || period <= 0 || period > startsAt) return "dates";
        if (f[2].isEmpty()) return "no bundle";
        return null;
    }

    /** Default length of the implicit usage ceiling of a trial key without a `usage` right (ActivationPolicy.TRIAL_DEFAULT_DAYS). */
    public static final int TRIAL_DEFAULT_DAYS = 30;
    /** A right of another kind is kept verbatim and grants nothing (RentalLines.KIND_NAME, docs/RENTAL-LOTS.md 1.2 and 10.1). */
    private static final Pattern KIND_NAME = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");

    /**
     * End of the IMPLICIT usage ceiling (mirror of Activation.implicitUsageEnd): a TRIAL activation without a `usage` right ends {@link #TRIAL_DEFAULT_DAYS} days after its issue (a key
     * without an issue time: its notBefore). A production activation without usage stays unlimited (null); an explicit usage right always wins (null here: its own end applies).
     */
    public static Long implicitUsageEnd(Fields a) {
        if (!a.kind().equals("trial") || a.rights().stream().anyMatch(WireActivation::isUsage)) return null;
        return (a.issuedAt() > 0 ? a.issuedAt() : a.notBefore()) + TRIAL_DEFAULT_DAYS * DAY_MS;
    }

    public static boolean isSuper(String rightLine) {
        return rightLine.startsWith("super|");
    }

    /** The installation-key claim (`ik|<64 lowercase hex>`): the Ed25519 public key the TV signs its `bind` proofs with, SIGNED inside the activation (W23-05 audit HIGH-1). It grants nothing; a reader that does not know it ignores it. */
    public static boolean isInstallKey(String rightLine) {
        return rightLine.startsWith("ik|");
    }

    private static final Pattern IK_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public static String installKeyLine(byte[] ed25519PublicKey) {
        if (ed25519PublicKey == null || ed25519PublicKey.length != 32) throw new IllegalArgumentException("clé d'installation de 32 octets attendue");
        return "ik|" + java.util.HexFormat.of().formatHex(ed25519PublicKey);
    }

    /**
     * The installation key claimed by the activation, as canonical base64 of the 32 raw bytes (the form of the `bind` proof and of {@code wallet_identity.install_pub}), or null when the
     * activation carries none (every activation issued before W23-05 correction). Throws {@link IllegalArgumentException} when the claim is malformed or repeated.
     */
    public static String installKeyOf(List<String> rights) {
        String found = null;
        for (String r : rights) {
            if (!isInstallKey(r)) continue;
            String[] f = r.split("\\|", -1);
            if (f.length != 2 || !IK_HEX.matcher(f[1]).matches() || found != null) throw new IllegalArgumentException("ik");
            found = Base64.getEncoder().encodeToString(java.util.HexFormat.of().parseHex(f[1]));
        }
        return found;
    }
    public static final long MAX_OPEN_ALL_MS = 30 * DAY_MS;
    public static final Pattern ID = Envelope.ID;
    public static final Pattern HEX = Envelope.HEX;

    private WireActivation() {}

    /** @param notAfter the {@code expiresAt} of the envelope (end of the installation window); @param seq sequence number of the key */
    public record Fields(String kind, String subject, String kid, long seq, String nonce, long issuedAt, long notBefore, long notAfter, String license, String seat, int k,
                         Map<Factor, String> factors, List<String> rights) {}

    public record Decoded(Fields fields, Envelope envelope, byte[] signature) {
        public String text() { return envelope.payload(); }
    }

    /** The unsigned envelope of an activation (type {@code activation}, target = the device, body = kind, subject, licence, seat, sorted rights). */
    public static Envelope envelope(Fields f, String signature) {
        Map<Factor, String> sorted = new EnumMap<>(Factor.class);
        sorted.putAll(f.factors());
        List<String> body = new ArrayList<>(List.of("kind=" + f.kind(), "subject=" + f.subject(), "license=" + f.license(), "seat=" + f.seat()));
        List<String> rights = new ArrayList<>(f.rights());
        Collections.sort(rights); // by the bytes of the UTF-8 text: the rights are ASCII, so the String order is the byte order
        rights.forEach(r -> body.add("right=" + r));
        return new Envelope(TYPE, f.kid(), f.seq(), f.nonce(), f.issuedAt(), f.notBefore(), f.notAfter(), Envelope.Target.device(f.k(), sorted), body, signature);
    }

    public static String payload(Fields f) { return envelope(f, "").payload(); }

    /** cbx1.&lt;payload base64url without padding&gt;.&lt;signature base64 with padding&gt; */
    public static String token(Fields f, byte[] signature) { return envelope(f, Base64.getEncoder().encodeToString(signature)).token(); }

    /** Default seat identifier of a NEW seat: hex(SHA-256("castbridge-seat|" + license + "|" + hex(set hash))[0:8]). */
    public static String defaultSeat(String license, Map<Factor, String> factors) {
        byte[] h = Hashing.sha256(("castbridge-seat|" + license + "|" + java.util.HexFormat.of().formatHex(DeviceIdentity.setHash(factors))).getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(h, 0, 8);
    }

    /** A right line is valid when it has the right number of fields and valid identifiers (§ 3.3). */
    public static boolean rightLineOk(String line) {
        String[] f = line.split("\\|", -1);
        try {
            switch (f[0]) {
                case "purchase" -> {
                    if (f.length != 4 || !ID.matcher(f[1]).matches() || !idsOk(f[2])) return false;
                    Long.parseLong(f[3]);
                    return true;
                }
                case "subscription" -> {
                    if (f.length != 7 || !ID.matcher(f[1]).matches() || !idsOk(f[2]) || !(f[6].equals("0") || f[6].equals("1"))) return false;
                    Long.parseLong(f[3]);
                    Long.parseLong(f[4]);
                    Long.parseLong(f[5]);
                    return true;
                }
                case "openall" -> {
                    if (f.length != 4 || !ID.matcher(f[1]).matches()) return false;
                    Long.parseLong(f[2]);
                    Long.parseLong(f[3]);
                    return true;
                }
                case "usage" -> {     // usage ceiling: usage|duree|<from ms>|<to ms>
                    if (f.length != 4 || !f[1].equals("duree")) return false;
                    Long.parseLong(f[2]);
                    Long.parseLong(f[3]);
                    return true;
                }
                case "rental" -> {    // rental|product|bundles|startsAt|period|days|graceMs|maxUsage|maxConcurrent|box
                    if (f.length != 10 || !ID.matcher(f[1]).matches() || !BOX.matcher(f[9]).matches()) return false;
                    if (!f[2].isEmpty()) for (String b : f[2].split(",", -1)) if (!ID.matcher(b).matches()) return false;
                    Long.parseLong(f[3]);
                    Long.parseLong(f[4]);
                    Integer.parseInt(f[5]);
                    Long.parseLong(f[6]);
                    Integer.parseInt(f[7]);
                    Integer.parseInt(f[8]);
                    return true;
                }
                case "ik" -> {        // installation key claim: ik|<64 hex>
                    return f.length == 2 && IK_HEX.matcher(f[1]).matches();
                }
                case "super" -> {     // SUPER_UNLIMITED: super|<produit>|<date ms> (only the super administrator's key signs it)
                    if (f.length != 3 || !ID.matcher(f[1]).matches()) return false;
                    Long.parseLong(f[2]);
                    return true;
                }
                default -> {          // another kind: kept verbatim (an old reader ignores a right it does not know), it only has to look like a right line
                    return KIND_NAME.matcher(f[0]).matches() && line.indexOf('\n') < 0;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean idsOk(String csv) {
        if (csv.isEmpty()) return true;
        for (String s : csv.split(",", -1)) if (!ID.matcher(s).matches()) return false;
        return true;
    }

    /** The activation view of an envelope of type {@code activation}, or null (wrong type, target not a device, body not canonical). */
    public static Fields fieldsOf(Envelope e) {
        try {
            if (!e.type().equals(TYPE) || e.target().kind() != Envelope.Target.Kind.DEVICE) return null;
            List<String> b = e.body();
            String seat = val(b.get(3), "seat"), license = val(b.get(2), "license");
            if (!HEX.matcher(seat).matches() || !ID.matcher(license).matches()) return null;
            List<String> rights = new ArrayList<>();
            for (String l : b.subList(4, b.size())) {
                if (!l.startsWith("right=") || !rightLineOk(l.substring(6))) return null;
                rights.add(l.substring(6));
            }
            String kind = val(b.get(0), "kind"), subject = val(b.get(1), "subject");
            if (!kind.equals("trial") && !kind.equals("production")) return null;
            if (!subject.equals("tv") && !subject.equals("phone")) return null;
            Fields f = new Fields(kind, subject, e.kid(), e.seq(), e.nonce(), e.issuedAt(), e.notBefore(), e.expiresAt(), license, seat, e.target().k(), e.target().factors(), rights);
            return envelope(f, e.signature()).payload().equals(e.payload()) ? f : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Strict decoder: the envelope is rebuilt from the parsed fields and must be identical byte for byte, or the result is null. */
    public static Decoded decode(String token) {
        try {
            Envelope e = Envelope.decode(token);
            Fields f = e == null ? null : fieldsOf(e);
            return f == null ? null : new Decoded(f, e, Base64.getDecoder().decode(e.signature()));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String val(String line, String key) {
        if (!line.startsWith(key + "=")) throw new IllegalArgumentException(key);
        return line.substring(key.length() + 1);
    }
}
