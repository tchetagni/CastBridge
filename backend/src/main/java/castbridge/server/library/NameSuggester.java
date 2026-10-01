package castbridge.server.library;

import java.util.List;

/**
 * Proposes titles for ambiguous file names. The only input is a list of cleaned names with minimal metadata: never the
 * content of a file, never a path, never an identifier of the device. The answer is a proposal, never a fact.
 */
public interface NameSuggester {
    /** One name to understand: [index] is its position in the request, [text] the cleaned name, [ext] its extension. */
    record Item(int index, String text, String ext, String kindHint, Integer durationMin) {}

    record Suggestion(int index, String kind, String title, Integer year, Integer season, Integer episode, String episodeTitle, String artist, double confidence) {}

    /** What the model call cost (tokens as reported by the service, or estimated), and how many of its answers were refused by the validation. */
    record Usage(long inputTokens, long outputTokens, int rejected, boolean estimated) {
        public static final Usage NONE = new Usage(0, 0, 0, false);
    }

    record Outcome(List<Suggestion> suggestions, Usage usage, String promptVersion) {}

    /** The model that answered ("none" when no model is configured). */
    String model();

    /** False when no model is configured: callers answer with {@code available:false} and no suggestions. */
    boolean available();

    /** @throws SuggesterException when the model could not answer */
    List<Suggestion> suggest(String lang, List<Item> items) throws SuggesterException;

    /** Same as {@link #suggest} with the usage; the default has no usage (stubs). */
    default Outcome suggestWithUsage(String lang, List<Item> items) throws SuggesterException {
        return new Outcome(suggest(lang, items), Usage.NONE, "");
    }

    /** The prompt version in use ("" when none), to estimate the cost before the call. */
    default String promptText() { return ""; }

    class SuggesterException extends Exception {
        public SuggesterException(String message, Throwable cause) { super(message, cause); }
        public SuggesterException(String message) { super(message); }
    }
}
