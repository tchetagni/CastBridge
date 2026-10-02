package castbridge.server.lots;

import java.nio.file.Path;
import java.util.List;

/**
 * Checks the CONTENT of a lot file of one feature before the admin can publish it. The default ({@link ZipLotValidator}) only
 * checks that it is a sane ZIP; a feature (Apprendre, Quiz) adds its own {@code @Component} for its {@link #feature()} to
 * validate its format (it replaces the default for that feature). Returns the problems in French, empty = valid.
 */
public interface LotValidator {
    String feature();

    List<String> validate(Path file, long size);

    /**
     * Same check with the identity the admin declared (scope, version), for the features whose lot file repeats it (Langues).
     * The default ignores it, so the validators of Apprendre and Quiz are untouched.
     */
    default List<String> validate(Path file, long size, String scope, int version) { return validate(file, size); }
}
