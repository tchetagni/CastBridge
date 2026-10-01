package castbridge.server.lots;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Default validation: a readable ZIP, no path escaping the archive, bounded expansion (zip-bomb guard). */
public class ZipLotValidator implements LotValidator {
    static final long MAX_EXPANDED = 64L << 20;
    private final String feature;

    public ZipLotValidator(String feature) { this.feature = feature; }

    @Override public String feature() { return feature; }

    @Override
    public List<String> validate(Path file, long size) {
        List<String> problems = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entries = zip.entries();
            long expanded = 0;
            int n = 0;
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                n++;
                String name = e.getName();
                if (name.startsWith("/") || name.startsWith("\\") || name.contains("..") || name.contains("\\") || name.indexOf('\0') >= 0)
                    problems.add("entrée d'archive interdite : « " + name.replace('\0', '?') + " »");
                expanded += Math.max(0, e.getSize());
            }
            if (n == 0) problems.add("archive vide");
            if (expanded > MAX_EXPANDED) problems.add("contenu décompressé trop gros (" + (expanded >> 20) + " Mo, maximum " + (MAX_EXPANDED >> 20) + " Mo)");
        } catch (IOException | RuntimeException e) {
            problems.add("le fichier n'est pas une archive ZIP lisible");
        }
        return problems;
    }
}
