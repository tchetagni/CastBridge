package castbridge.server.lots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** LangLotValidator on its own, and on EVERY pack committed in content/langues (zipped the way LangLotBuilder does: langue.json + media.json at the root). */
class LotLangValidatorTest {
    private final LangLotValidator v = new LangLotValidator();

    private static Path zip(String... nameAndContent) throws IOException {
        Path f = Files.createTempFile("lot", ".lot");
        try (OutputStream o = Files.newOutputStream(f); ZipOutputStream z = new ZipOutputStream(o)) {
            for (int i = 0; i + 1 < nameAndContent.length; i += 2) { z.putNextEntry(new ZipEntry(nameAndContent[i])); z.write(nameAndContent[i + 1].getBytes()); z.closeEntry(); }
        }
        f.toFile().deleteOnExit();
        return f;
    }

    /** What LangLotBuilder.withLicense does for a free lot. */
    private static String tagged(String json) { int i = json.indexOf('{'); return json.substring(0, i + 1) + "\"license\": \"CC-BY-SA-4.0\"," + json.substring(i + 1); }

    @Test
    void refusesALotWithoutTheCcBySaLicenceField() throws Exception {
        String base = "{\"format\":1,\"type\":\"langue\",\"id\":\"de-a0-x-en\",\"version\":1,\"units\":[{}]}";
        assertTrue(!v.validate(zip("langue.json", base), 100, "de-a0-x-en", 1).isEmpty());
        assertTrue(!v.validate(zip("langue.json", base.replace("{\"format", "{\"license\":\"CC-BY-NC-4.0\",\"format")), 100, "de-a0-x-en", 1).isEmpty());
        assertEquals(List.of(), v.validate(zip("langue.json", tagged(base)), 100, "de-a0-x-en", 1));
    }

    @Test
    void everyCommittedPackPassesAsALot() throws Exception {
        Path root = Path.of("..", "content", "langues");
        Assumptions.assumeTrue(Files.isDirectory(root), "content/langues absent");
        int n = 0;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path d : (Iterable<Path>) dirs.filter(p -> Files.isRegularFile(p.resolve("langue.json"))).sorted()::iterator) {
                String main = tagged(Files.readString(d.resolve("langue.json")));
                int version = new ObjectMapper().readTree(main).path("version").asInt(1);
                Path media = d.resolve("media.json");
                Path lot = Files.isRegularFile(media) ? zip("langue.json", main, "media.json", Files.readString(media)) : zip("langue.json", main);
                List<String> problems = v.validate(lot, Files.size(lot), d.getFileName().toString(), version);
                assertTrue(problems.isEmpty(), d.getFileName() + " : " + problems);
                n++;
            }
        }
        assertTrue(n >= 40, "packs checked: " + n);
    }

    @Test
    void refusesWhatTheTvWouldRefuse() throws Exception {
        String ok = "{\"license\":\"CC-BY-SA-4.0\",\"format\":1,\"type\":\"langue\",\"id\":\"de-a0-x-en\",\"version\":1,\"units\":[{}]}";
        assertEquals(List.of(), v.validate(zip("langue.json", ok), 100, "de-a0-x-en", 1));
        assertTrue(!v.validate(zip("langue.json", ok), 100, "de-a0-y-en", 1).isEmpty());                                   // id != scope
        assertTrue(!v.validate(zip("langue.json", ok), 100, "de-a0-x-en", 2).isEmpty());                                   // version
        assertTrue(!v.validate(zip("media.json", "{}"), 100, "de-a0-x-en", 1).isEmpty());                                  // no langue.json
        assertTrue(!v.validate(zip("langue.json", "pas du json"), 100, "de-a0-x-en", 1).isEmpty());
        assertTrue(!v.validate(zip("langue.json", ok.replace("\"units\":[{}]", "\"units\":[]")), 100, "de-a0-x-en", 1).isEmpty());
        assertTrue(!v.validate(zip("langue.json", ok.replace("langue\"", "autre\"")), 100, "de-a0-x-en", 1).isEmpty());
        assertTrue(!v.validate(zip("langue.json", ok, "x/../../y", "1"), 100, "de-a0-x-en", 1).isEmpty());                // path escape
        assertTrue(!v.validate(zip("langue.json", ok), LangLotValidator.MAX_BYTES + 1, "de-a0-x-en", 1).isEmpty());        // over 3 Mo
        assertTrue(!v.validate(zip("langue.json", ok), 100, "de-a0-x-de", 1).isEmpty());                                   // same target and start
    }
}
