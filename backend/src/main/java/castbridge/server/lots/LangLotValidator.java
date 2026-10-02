package castbridge.server.lots;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Langues lots (feature {@code langues}, docs/LANGUES.md § 14): the TEXT lot built by {@code :core:buildLangLots}
 * (castbridge.core.langues.LangLotBuilder), a zip with {@code langue.json} (+ optional {@code media.json}) at its root and nothing else.
 * Checks what the TV's consumer (LangLotConsumer) would refuse anyway, so a bad lot never reaches the catalog: at most 3 Mo (the
 * consumer's cap), a sane ZIP ({@link ZipLotValidator}), only the two allowed entries, a readable langue.json whose id and version
 * are those declared at upload. The audio / video twin ({@code langues-media}) is another feature, not accepted here.
 */
public class LangLotValidator implements LotValidator {
    public static final String FEATURE = "langues";
    /** castbridge.core.langues.LangLotConsumer.MAX_TEXT_LOT_BYTES (inside the 10 Mo TV cap of {@link LotService#MAX_LOT_BYTES}). */
    public static final long MAX_BYTES = 3L << 20;
    /** castbridge.core.langues.LangLotBuilder.LICENSE_TAG. */
    public static final String LICENSE_TAG = "CC-BY-SA-4.0";
    static final long MAX_ENTRY = 8L << 20;
    /** {target}-{level}-{theme}-{source}: castbridge.core.langues.LangLots. */
    static final Pattern SCOPE = Pattern.compile("([a-z]{2})-([a-z0-9]{2,5})-([a-z0-9]{1,16})-([a-z]{2})");
    private static final Set<String> ENTRIES = Set.of("langue.json", "media.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ZipLotValidator zip = new ZipLotValidator(FEATURE);

    @Override public String feature() { return FEATURE; }

    @Override public List<String> validate(Path file, long size) { return validate(file, size, null, 0); }

    @Override
    public List<String> validate(Path file, long size, String scope, int version) {
        List<String> problems = new ArrayList<>();
        if (size > MAX_BYTES) problems.add("lot Langues trop gros : " + size + " octets (maximum " + MAX_BYTES + " : 3 Mo pour un lot texte)");
        problems.addAll(zip.validate(file, size));
        if (!problems.isEmpty()) return problems;
        Matcher m = scope == null ? null : SCOPE.matcher(scope);
        boolean scopeOk = m != null && m.matches();
        if (scope != null && !scopeOk) problems.add("scope Langues : « <cible>-<niveau>-<thème>-<départ> » attendu (ex. de-a0-famille-en)");
        else if (scopeOk && m.group(1).equals(m.group(4))) problems.add("scope Langues : la langue de départ doit différer de la langue cible");
        try (ZipFile z = new ZipFile(file.toFile())) {
            var en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!ENTRIES.contains(e.getName())) problems.add("entrée inattendue dans un lot Langues : « " + e.getName() + " » (langue.json et media.json seulement)");
                else if (e.getSize() > MAX_ENTRY) problems.add(e.getName() + " trop gros");
            }
            ZipEntry main = z.getEntry("langue.json");
            if (main == null) problems.add("langue.json absent à la racine du lot");
            if (!problems.isEmpty()) return problems;
            JsonNode root = read(z, main);
            if (root == null || !root.isObject()) { problems.add("langue.json illisible"); return problems; }
            if (!"langue".equals(root.path("type").asText(null))) problems.add("langue.json : \"type\" doit valoir \"langue\"");
            if (root.has("format") && root.path("format").asInt(-1) != 1) problems.add("langue.json : format " + root.path("format").asText() + " non géré (1 attendu)");
            // « free only »: the builder writes this field into the langue.json of a free (CC BY-SA) lot only; a lot without it is a reserved or hand-made one and never gets published here
            if (!LICENSE_TAG.equals(root.path("license").asText(null))) problems.add("langue.json : \"license\" doit valoir \"" + LICENSE_TAG + "\" (seuls les lots libres CC BY-SA sont publiés ; champ écrit par :core:buildLangLots)");
            if (!root.path("units").isArray() || root.path("units").isEmpty()) problems.add("langue.json : aucune unité");
            if (scope != null && !scope.equals(root.path("id").asText(null))) problems.add("langue.json : id « " + root.path("id").asText("") + " » différent du scope déclaré « " + scope + " »");
            if (version > 0 && root.path("version").asInt(1) != version) problems.add("langue.json : version " + root.path("version").asInt(1) + " différente de la version déclarée " + version);
            if (scopeOk) {
                if (root.hasNonNull("target") && !m.group(1).equals(root.get("target").asText())) problems.add("langue.json : \"target\" incohérent avec le scope");
                if (root.hasNonNull("source") && !m.group(4).equals(root.get("source").asText())) problems.add("langue.json : \"source\" incohérent avec le scope");
                if (root.hasNonNull("level") && !m.group(2).equalsIgnoreCase(root.get("level").asText())) problems.add("langue.json : \"level\" incohérent avec le scope");
            }
            ZipEntry media = z.getEntry("media.json");
            if (media != null) {
                JsonNode md = read(z, media);
                if (md == null || !md.isObject()) problems.add("media.json illisible");
            }
        } catch (IOException | RuntimeException e) {
            problems.add("lot Langues illisible");
        }
        return problems;
    }

    /** Parsed JSON of an entry, or null if it is too big or not JSON. */
    private static JsonNode read(ZipFile z, ZipEntry e) throws IOException {
        try (InputStream in = z.getInputStream(e)) {
            byte[] b = in.readNBytes((int) MAX_ENTRY + 1);
            if (b.length > MAX_ENTRY) return null;
            return JSON.readTree(b);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return null;
        }
    }
}
