package castbridge.server.wallet.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Port exact de {@code Pot} (android/core/src/main/kotlin/castbridge/core/quiz/Wallet.kt:54-85). Mêmes vecteurs côté Kotlin
 * ({@code PotVectorsTest}) et côté Java ({@code PotSplitVectorsTest}) : tools/wallet/ledger-vectors.json.
 */
public final class PotSplit {
    private PotSplit() {}

    /** Parts de la cagnotte par place : 1 joueur 100 % ; 2 : 70/30 ; 3 et plus : 60/30/10. */
    public static List<Integer> shares(int players) {
        if (players <= 1) return List.of(100);
        if (players == 2) return List.of(70, 30);
        return List.of(60, 30, 10);
    }

    /**
     * Partage {@code pot} entre {@code scores} (joueur → points). Les ex æquo se partagent à parts égales les places qu'ils couvrent ; 0 point = rien
     * (sa part va aux autres) ; le reste de l'arrondi va au meilleur (le premier dans l'ordre d'insertion à score maximal). Somme = pot si quelqu'un a marqué.
     */
    public static LinkedHashMap<String, Long> split(long pot, Map<String, Integer> scores) {
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        for (String k : scores.keySet()) out.put(k, 0L);
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>();
        for (Map.Entry<String, Integer> e : scores.entrySet()) if (e.getValue() > 0) ranked.add(e);
        ranked.sort(Comparator.comparing((Map.Entry<String, Integer> e) -> e.getValue()).reversed()); // tri stable, comme sortedByDescending
        if (pot <= 0 || ranked.isEmpty()) return out;
        List<Integer> shares = shares(ranked.size());
        int totalPct = 0;
        for (int i = 0; i < Math.min(ranked.size(), shares.size()); i++) totalPct += shares.get(i);
        int place = 0;
        long given = 0;
        TreeMap<Integer, List<Map.Entry<String, Integer>>> groups = new TreeMap<>(Comparator.reverseOrder());
        for (Map.Entry<String, Integer> e : ranked) groups.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e);
        for (List<Map.Entry<String, Integer>> group : groups.values()) {
            int pct = 0;
            for (int i = place; i < place + group.size(); i++) pct += i < shares.size() ? shares.get(i) : 0;
            long each = pot * pct / totalPct / group.size();
            for (Map.Entry<String, Integer> e : group) {
                out.put(e.getKey(), each);
                given += each;
            }
            place += group.size();
        }
        String best = ranked.get(0).getKey();
        out.put(best, out.get(best) + (pot - given));
        return out;
    }
}
