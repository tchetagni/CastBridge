package castbridge.server.telemetry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The closed catalog of usage events (docs/TELEMETRY.md; same catalog in android/core castbridge.core.telemetry).
 * Each event lists the properties it may carry (white list: other keys are dropped, forbidden keys reject the event),
 * and how it maps onto the indexed columns used by the KPIs (dim1, dim2, ms, bytes, value, ok).
 */
public final class EventCatalog {
    private EventCatalog() {}

    public static final int SCHEMA_VERSION = 1;

    enum Kind { TEXT, ENUM, INT, NUM, BOOL, MESSAGE }

    record Prop(Kind kind, Set<String> values, int maxLen, double max) {
        static Prop text(int maxLen) { return new Prop(Kind.TEXT, Set.of(), maxLen, 0); }
        static Prop oneOf(String... values) { return new Prop(Kind.ENUM, Set.of(values), 0, 0); }
        static Prop integer(double max) { return new Prop(Kind.INT, Set.of(), 0, max); }
        static Prop number(double max) { return new Prop(Kind.NUM, Set.of(), 0, max); }
        static Prop bool() { return new Prop(Kind.BOOL, Set.of(), 0, 0); }
        static Prop message() { return new Prop(Kind.MESSAGE, Set.of(), 200, 0); }
    }

    /**
     * @param essential kept even without the "usage statistics" consent (errors and updates, needed to maintain the apps)
     * @param dim1 property stored in the dim1 column (grouping), {@code dim2} likewise; ms / bytes / value / ok: numeric columns
     * @param dayCounter also counted in the anonymous kpi_event_day table
     */
    record Def(String name, boolean essential, Map<String, Prop> props, String dim1, String dim2, String ms, String bytes, String value,
               String ok, boolean dayCounter) {}

    static final double DAY_MS = 86_400_000d;
    static final double MAX_BYTES = 1e13; // 10 To
    static final Pattern CODE = Pattern.compile("[A-Za-z0-9_.:+/-]{1,64}");

    /** Stable feature ids, per app, with the label shown in /admin. Screens use the same ids (+ a few more). */
    public static final Map<String, String> TV_FEATURES = ordered(
            "library", "Bibliothèque", "quiz", "Quiz", "chess", "Échecs", "receive", "Recevoir du téléphone", "usb", "Clé USB",
            "bluetooth", "Bluetooth", "internet", "Internet / test", "wifi_direct", "Wi-Fi Direct", "admin", "Administration",
            "downloads", "Téléchargements", "updates", "Mises à jour", "settings", "Réglages", "learn", "Apprendre",
            "remote", "Télécommande", "help", "Aide", "dev_options", "Options développeur", "games", "Jeux", "sudoku", "Sudoku");
    public static final Map<String, String> PHONE_FEATURES = ordered(
            "send", "Envoyer", "move", "Déplacer", "watch_on_tv", "Regarder sur la TV", "tv_library", "Bibliothèque TV",
            "file_exchange", "Échange de fichiers", "remote", "Télécommande", "player", "Lecteur / Ouvrir avec", "cast", "Caster",
            "quiz", "Quiz", "chess", "Échecs", "bt_gateway", "Passerelle Bluetooth", "downloads", "Téléchargements",
            "updates", "Mises à jour", "settings", "Réglages", "learn", "Apprendre", "games", "Jeux");
    /** Screens that are not features. */
    public static final Map<String, String> OTHER_SCREENS = ordered("home", "Accueil", "onboarding", "Premier lancement",
            "player", "Lecteur", "privacy", "Confidentialité");

    /** Keys that must never be sent (content, identity, location, secrets): the event is refused. */
    public static final Set<String> FORBIDDEN = Set.of("filename", "file", "file_name", "title", "path", "url", "uri", "email", "phone",
            "contact", "contacts", "password", "pin", "key", "token", "secret", "lat", "lon", "lng", "latitude", "longitude", "gps",
            "location", "ip", "ssid", "bssid", "mac", "imei", "serial", "account", "user", "username", "name");

    public static final Map<String, Def> EVENTS = new LinkedHashMap<>();

    static {
        Prop ms = Prop.integer(30 * DAY_MS), bytes = Prop.integer(MAX_BYTES), ok = Prop.bool(), error = Prop.text(64);
        Prop channel = Prop.oneOf("wifi", "bluetooth", "wifidirect", "dlna");
        Prop mode = Prop.oneOf("copy", "move", "direct");
        Prop screen = new Prop(Kind.TEXT, Set.of(), 40, 0); // checked against the screens of the app in validate()
        def("session_start", false, Map.of(), null, null, null, null, null, null, false);
        def("session_end", false, Map.of("ms", ms), null, null, "ms", null, null, null, true);
        def("screen_view", false, Map.of("screen", screen), "screen", null, null, null, null, null, false);
        def("screen_time", false, Map.of("screen", screen, "ms", ms), "screen", null, "ms", null, null, null, false);
        def("feature_used", false, Map.of("feature", screen, "source", Prop.oneOf("tile", "menu", "phone", "remote", "shortcut", "notification")),
                "feature", "source", null, null, null, null, true);
        def("cast_start", false, Map.of("channel", channel, "mode", mode, "bytes", bytes), "channel", "mode", null, "bytes", null, null, true);
        def("cast_end", false, Map.of("channel", channel, "mode", mode, "bytes", bytes, "ms", ms, "kbps", Prop.number(1e7), "ok", ok,
                "error", error), "channel", "mode", "ms", "bytes", "kbps", "ok", true);
        Prop source = Prop.oneOf("internal", "usb", "stream", "phone", "dlna");
        def("playback_start", false, Map.of("codec", Prop.text(32), "resolution", Prop.text(16), "hw", Prop.bool(), "source", source),
                "codec", "resolution", null, null, null, null, true);
        def("playback_end", false, Map.of("ms", ms, "pct", Prop.number(100), "codec", Prop.text(32), "resolution", Prop.text(16),
                "hw", Prop.bool(), "source", source, "abandoned", Prop.bool(), "ok", ok, "error", error),
                "codec", "resolution", "ms", null, "pct", "ok", true);
        def("library_stats", false, Map.of("files", Prop.integer(1e7), "bytes", bytes), null, null, null, "bytes", "files", null, false);
        def("quiz_game", false, Map.of("mode", Prop.text(24), "duel", Prop.text(24), "track", Prop.oneOf("general", "primary", "secondary", "higher"),
                "level", Prop.text(16), "field", Prop.text(24), "players", Prop.integer(100), "score", Prop.integer(1e9), "ms", ms,
                "jokers", Prop.integer(100)), "mode", "track", "ms", null, "players", null, true);
        def("quiz_answer", false, Map.of("question", Prop.text(64), "correct", Prop.bool(), "ms", ms), "question", null, "ms", null, null,
                "correct", false);
        def("chess_game", false, Map.of("mode", Prop.text(24), "ai_level", Prop.integer(100), "time_control", Prop.text(16),
                "result", Prop.oneOf("win", "loss", "draw", "abandon"), "moves", Prop.integer(10_000), "ms", ms),
                "mode", "result", "ms", null, "moves", null, true);
        def("sudoku_game", false, Map.of("level", Prop.oneOf("EASY", "MEDIUM", "HARD", "EXPERT"), "ms", ms, "hints", Prop.integer(10),
                "result", Prop.oneOf("win", "abandon")), "level", "result", "ms", null, "hints", null, true);
        def("download", false, Map.of("type", Prop.oneOf("http", "magnet", "torrent"), "bytes", bytes, "ms", ms, "ok", ok, "error", error),
                "type", null, "ms", "bytes", null, "ok", true);
        def("gateway_session", false, Map.of("ms", ms, "bytes", bytes), null, null, "ms", "bytes", null, null, true);
        // "via" (network path used), not "path": "path" is a forbidden key (file paths)
        def("connectivity_check", false, Map.of("via", Prop.oneOf("wifi", "bluetooth", "ethernet", "wifidirect", "none"), "ok", ok,
                "latency_ms", Prop.integer(600_000)), "via", null, null, null, "latency_ms", "ok", true);
        def("update_install", true, Map.of("from", Prop.integer(1e9), "to", Prop.integer(1e9), "ok", ok, "error", error),
                "to", "from", null, null, null, "ok", true);
        def("error", true, Map.of("screen", screen, "type", error, "message", Prop.message()), "screen", "type", null, null, null, null, true);
        def("crash", true, Map.of("screen", screen, "type", error, "message", Prop.message()), "screen", "type", null, null, null, null, true);
        // « Apprendre » (docs/LEARN.md § 6): content ids only (pack, lesson, exercise), never the pupil's first name
        Prop id = Prop.text(64);
        def("learn", false, Map.ofEntries(
                Map.entry("action", Prop.oneOf("profile_created", "lesson_view", "lesson_complete", "exercise_result", "review_result",
                        "mock_exam_result", "badge_earned", "pack_installed")),
                Map.entry("pack", id), Map.entry("lesson", id), Map.entry("subject", Prop.text(32)), Map.entry("exercise", id),
                Map.entry("correct", Prop.bool()), Map.entry("points", Prop.number(1000)), Map.entry("max", Prop.number(1000)),
                Map.entry("attempt", Prop.integer(1000)), Map.entry("box", Prop.integer(10)), Map.entry("score", Prop.number(1000)),
                Map.entry("out_of", Prop.number(1000)), Map.entry("ms", ms), Map.entry("badge", Prop.text(32)), Map.entry("level", Prop.text(16)),
                Map.entry("version", Prop.integer(1e9))),
                "action", "subject", "ms", null, "score", "correct", true);
    }

    private static void def(String name, boolean essential, Map<String, Prop> props, String dim1, String dim2, String ms, String bytes,
                            String value, String ok, boolean dayCounter) {
        EVENTS.put(name, new Def(name, essential, props, dim1, dim2, ms, bytes, value, ok, dayCounter));
    }

    public static Map<String, String> features(String app) {
        return "phone".equals(app) ? PHONE_FEATURES : TV_FEATURES;
    }

    /** Label of a feature / screen for the admin pages. */
    public static String label(String app, String id) {
        String l = features(app).get(id);
        if (l == null) l = OTHER_SCREENS.get(id);
        if (l == null) l = TV_FEATURES.getOrDefault(id, PHONE_FEATURES.getOrDefault(id, id));
        return l;
    }

    static boolean knownScreen(String app, String id) {
        return features(app).containsKey(id) || OTHER_SCREENS.containsKey(id);
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return java.util.Collections.unmodifiableMap(m);
    }

    /** Removes what looks like a path, a URL or a media file name from a free message (never content in clear). */
    static String scrub(String message) {
        return message
                .replaceAll("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+", "[url]")
                .replaceAll("(?<![\\w])(/[^\\s/]+){2,}/?", "[chemin]")
                .replaceAll("\\S+\\.(?i:mp4|mkv|avi|mov|webm|ts|m4v|mp3|m4a|flac|wav|apk|jpg|jpeg|png|srt|torrent|pdf|zip)\\b", "[fichier]");
    }

    public static List<String> names() { return List.copyOf(EVENTS.keySet()); }
}
