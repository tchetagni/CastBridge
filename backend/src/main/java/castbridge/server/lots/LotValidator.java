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
}
