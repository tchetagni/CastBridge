package castbridge.server.library;

import java.util.List;

/** Used while no LLM key is configured (the default): nothing is called, nothing is invented. */
public class DisabledNameSuggester implements NameSuggester {
    @Override public String model() { return "none"; }

    @Override public boolean available() { return false; }

    @Override public List<Suggestion> suggest(String lang, List<Item> items) { return List.of(); }
}
