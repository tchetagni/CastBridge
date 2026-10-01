package castbridge.server.orders;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * THE CLOSED LIST of order actions, server side (docs/ORDRES.md § Actions): the server refuses to SIGN anything outside it, and the TV refuses to APPLY anything outside it
 * (castbridge.core.policy.PolicyActions, same rules). The two lists are kept identical by a test on tools/orders/actions.json. No action runs code, reads or deletes the user's
 * files, opens a remote access or touches the signed updates: there is simply no identifier for it.
 */
public final class PolicyCatalog {
    private PolicyCatalog() {}

    public static final Set<String> ACTIONS = Set.of("license.activate", "license.suspend", "license.revoke", "license.extend", "revocation.add", "rights.refresh",
            "flag.set", "app.min_version", "update.channel", "catalog.available", "catalog.retire", "budget.set", "message.show", "message.clear");
    public static final Set<String> FLAGS = Set.of("learn.beta", "quiz.beta", "bt.tunnel", "lots.autodownload", "telemetry.verbose", "ui.new-home");
    public static final Set<String> CHANNELS = Set.of("stable", "beta");
    public static final Map<String, long[]> BUDGETS = Map.of("lots_mb", new long[]{0, 100_000}, "starter_mb", new long[]{0, 10_000}, "quiz_daily", new long[]{0, 10_000});
    public static final Set<String> LEVELS = Set.of("info", "notice");
    public static final long MAX_EXTENSION_MS = 366L * 24 * 3600 * 1000;
    public static final int MAX_MESSAGE = 280;

    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    private static final Pattern KID = Pattern.compile("^[0-9a-f]{16}$");
    private static final Pattern LOT = Pattern.compile("^[a-z][a-z0-9]{0,15}:[a-z0-9][a-z0-9-]{0,31}$");
    private static final Pattern MSG_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");
    private static final Pattern NUM = Pattern.compile("^(0|[1-9][0-9]{0,15})$");
    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_.-]{0,31}$");
    private static final Pattern ACTION = Pattern.compile("^[a-z][a-z0-9_.-]{0,47}$");

    /** Null if the order is allowed, else the French reason. [nowMs] = server time. */
    public static String check(String action, Map<String, String> p, long nowMs) {
        if (action == null || !ACTION.matcher(action).matches() || !ACTIONS.contains(action)) return "Action hors de la liste fermée : " + (action == null ? "" : action.substring(0, Math.min(48, action.length())));
        if (p.size() > 16) return "Trop de paramètres";
        for (var e : p.entrySet()) if (!NAME.matcher(e.getKey()).matches() || e.getValue() == null || e.getValue().length() > 512 || e.getValue().contains("\n") || e.getValue().contains("\r")) return "Paramètre mal formé : " + e.getKey();
        switch (action) {
            case "license.activate", "license.suspend", "license.revoke":
                return only(p, "license") != null ? only(p, "license") : (p.containsKey("license") && ID.matcher(p.get("license")).matches() ? null : "Licence manquante ou mal formée");
            case "license.extend": {
                String o = only(p, "license", "until"); if (o != null) return o;
                if (!p.containsKey("license") || !ID.matcher(p.get("license")).matches()) return "Licence manquante ou mal formée";
                Long until = num(p.get("until")); if (until == null) return "Fin manquante";
                return until > nowMs + MAX_EXTENSION_MS ? "Prolongation trop lointaine (366 jours au plus)" : null;
            }
            case "revocation.add": {
                String o = only(p, "kid", "seat", "license", "at"); if (o != null) return o;
                if (p.containsKey("kid")) return p.size() == 1 && KID.matcher(p.get("kid")).matches() ? null : "Clé à révoquer mal formée";
                return p.containsKey("license") && ID.matcher(p.get("license")).matches() && p.containsKey("seat") && KID.matcher(p.get("seat")).matches() && num(p.get("at")) != null ? null : "Poste à révoquer mal formé";
            }
            case "rights.refresh": return only(p, "reason");
            case "flag.set": {
                String o = only(p, "name", "value"); if (o != null) return o;
                if (!FLAGS.contains(p.get("name"))) return "Indicateur hors liste";
                return "0".equals(p.get("value")) || "1".equals(p.get("value")) ? null : "Valeur : 0 ou 1";
            }
            case "app.min_version": {
                String o = only(p, "version"); if (o != null) return o;
                Long v = num(p.get("version")); return v != null && v <= 2_000_000_000L ? null : "Version invalide";
            }
            case "update.channel": { String o = only(p, "channel"); return o != null ? o : CHANNELS.contains(p.get("channel")) ? null : "Canal inconnu"; }
            case "catalog.available", "catalog.retire": {
                String o = only(p, "lots"); if (o != null) return o;
                String[] ids = p.getOrDefault("lots", "").split(",", -1);
                if (ids.length == 0 || ids.length > 24) return "Liste de lots invalide";
                for (String l : ids) if (!LOT.matcher(l).matches()) return "Identifiant de lot invalide : " + l;
                return null;
            }
            case "budget.set": {
                String o = only(p, "name", "value"); if (o != null) return o;
                long[] r = BUDGETS.get(p.get("name")); if (r == null) return "Budget inconnu";
                Long v = num(p.get("value")); return v != null && v >= r[0] && v <= r[1] ? null : "Valeur hors bornes";
            }
            case "message.show": {
                String o = only(p, "id", "text", "level", "until"); if (o != null) return o;
                if (!MSG_ID.matcher(p.getOrDefault("id", "")).matches()) return "Identifiant de message invalide";
                String t = p.getOrDefault("text", "").strip();
                if (t.isEmpty() || t.length() > MAX_MESSAGE || t.chars().anyMatch(Character::isISOControl) || t.contains("://") || t.toLowerCase().contains("www.")) return "Texte invalide (vide, trop long, ou contenant un lien)";
                if (!LEVELS.contains(p.getOrDefault("level", "info"))) return "Niveau inconnu";
                return p.containsKey("until") && num(p.get("until")) == null ? "Date invalide" : null;
            }
            case "message.clear": { String o = only(p, "id"); return o != null ? o : MSG_ID.matcher(p.getOrDefault("id", "")).matches() ? null : "Identifiant invalide"; }
            default: return "Action hors de la liste fermée";
        }
    }

    private static String only(Map<String, String> p, String... names) {
        Set<String> ok = Set.of(names);
        for (String k : p.keySet()) if (!ok.contains(k)) return "Paramètre inattendu : " + k;
        return null;
    }

    private static Long num(String s) { return s != null && NUM.matcher(s).matches() ? Long.valueOf(s) : null; }
}
