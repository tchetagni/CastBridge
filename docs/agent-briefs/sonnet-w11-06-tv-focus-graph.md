# w11-06 — Cœur : `TvFocusGraph` — carte de focus D-pad de l'accueil TV, plus court chemin, test « ≤ 3 appuis en moyenne »
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-b** (vague W11) · prérequis : aucun · porte : `gradle --offline :core:test --tests 'castbridge.core.nav.TvFocusGraphTest'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 11b (cœur, JVM, testé) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT (indépendant de w11-05).** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 4.3, § 10.1. Branche `claude/sonnet-w11-06`. Rapport : `docs/agent-reports/sonnet-w11-06.md`.

## Objectif
Un graphe de focus **pur Kotlin** décrivant l'accueil TV (rangée « Reprendre », grille 3 × 2, puce d'en-tête, puces de statut) avec, pour chaque nœud, le nœud atteint par HAUT/BAS/GAUCHE/DROITE, et une mesure : coût (appuis + OK) depuis le focus initial jusqu'à chaque destination. Le test échoue si la moyenne dépasse 3,0 (sans rangée Reprendre) / 4,0 (avec), si un nœud est inatteignable, ou si une arête n'est pas réversible. w11-10 utilisera les ids de voisins pour poser des `nextFocus*` explicites.

## Pourquoi (preuves)
- `android/receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt:183-213` : 18 tuiles dans un `HorizontalScrollView` ⇒ 9,5 appuis en moyenne, 18 au maximum (conception § 1.2) ; aucun test possible aujourd'hui (pas de `src/test` dans `receiver`).
- `HomeScreen.kt:174-177` et `PlayerActivity.kt:1062` : focus initial sur la première carte, HAUT depuis la puce d'en-tête vers la barre de statut.
- Bugs de focus D-pad sur la TV de référence = risque n° 1 de la conception (§ 9.5) : la carte doit être calculée, pas devinée.

## Fichiers possédés
- Nouveaux : `android/core/src/main/kotlin/castbridge/core/nav/TvFocusGraph.kt`, `android/core/src/test/kotlin/castbridge/core/nav/TvFocusGraphTest.kt`.
- Hors zone : tout le reste (y compris `NavModel.kt` de w11-05 : ce cahier prend une `List<String>` d'ids en entrée).

## Signatures à respecter (contrat pour w11-10)
```kotlin
package castbridge.core.nav

enum class Dir { UP, DOWN, LEFT, RIGHT }
data class Node(val id: String, val x: Int, val y: Int, val w: Int, val h: Int)   // dp sur un canevas 1280 × 720
class TvFocusGraph(val nodes: List<Node>, private val edges: Map<String, Map<Dir, String>>) {
    fun next(id: String, d: Dir): String?
    fun cost(from: String, to: String): Int?          // nombre d'appuis directionnels (sans OK) ; null si inatteignable
    fun averageCost(from: String, targets: List<String>): Double   // appuis + 1 (OK)
    companion object {
        /** Accueil : [resumeCount] cartes « Reprendre » (0..6), [tiles] ids de la grille (≤ 6, 3 par rangée), [statusChips] (0..4). */
        fun home(resumeCount: Int, tiles: List<String>, statusChips: Int = 0): TvFocusGraph
        fun initialFocus(resumeCount: Int, tiles: List<String>): String
    }
}
```
Ids des nœuds : `resume:0..n-1`, `tile:<id>`, `chip`, `status:0..k-1`. Géométrie de référence (§ 7.3) : grille à partir de y = 300, tuiles 340 × 150 dp, marges 24 dp, origine x = 56 ; rangée Reprendre y = 160, cartes 220 × 124 dp ; `chip` en haut à gauche (56, 90) ; puces de statut colonne à droite (1180, 84 + i·46).

## Étapes
1. Construire `home(...)` : arêtes selon § 4.3 (DROITE/GAUCHE dans une rangée, BAS/HAUT entre rangées vers la colonne la plus proche par recouvrement horizontal, `resume:*` → BAS → tuile de la colonne la plus proche, `tile:*` première rangée → HAUT → `resume:j` la plus proche ou `chip` si aucune carte, `chip` → HAUT → `status:0`, `status:k` → BAS → `status:k+1` puis `chip`). Aucune arête vers l'extérieur de l'écran ; une arête absente = « reste sur place » (`next` renvoie `null`).
2. `cost` par parcours en largeur (BFS) ; `averageCost` = moyenne de (`cost` + 1).
3. Tests : (a) production, 0 Reprendre, 6 tuiles ⇒ moyenne ≤ 3,0, max ≤ 5 ; (b) avec 3 cartes Reprendre ⇒ moyenne ≤ 4,0 ; (c) enfant, 4 tuiles (sans `phone`, `plus`) ⇒ moyenne ≤ 2,5 ; (d) chaque nœud atteint chaque autre nœud ; (e) réversibilité : si `next(a, RIGHT) == b` alors `next(b, LEFT) == a`, idem UP/DOWN sauf vers `resume` (plusieurs tuiles peuvent remonter vers la même carte : accepter `next(b, DOWN)` ∈ voisins de a) ; (f) tous les nœuds dans 0..1280 × 0..720 ; (g) `initialFocus` = `resume:0` si cartes, sinon `tile:<premier>` ; (h) 1 tuile seulement ⇒ aucune exception ; (i) `statusChips = 4` ⇒ `status:3` → BAS → `chip`.
4. Imprimer dans le test (stdout) la table coût par destination pour les trois états (le rapport la recopie).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.nav.TvFocusGraphTest'   # vert, ≥ 9 cas
grep -c 'import android' core/src/main/kotlin/castbridge/core/nav/TvFocusGraph.kt          # 0
```
Observable : aucun (cœur).

## Cas limites
0 tuile ⇒ graphe réduit à `chip` + statuts (TV verrouillée : l'accueil n'est pas montré, mais le graphe ne doit pas lever). 7 tuiles ⇒ `IllegalArgumentException` (le modèle en interdit plus de 6). Rangée Reprendre de 6 cartes plus large que la grille ⇒ BAS depuis `resume:5` va à `tile` de la 3e colonne.

## À ne pas faire
Aucune dépendance Android ni à `NavModel` (w11-05). Ne pas modéliser les écrans hubs (Apprendre, Jeux). Pas d'animation, pas de rendu.

## Rapport
`STATUT`, la table des coûts pour les trois états, nombre de cas, et la liste `id → voisins` à poser en `nextFocus*` par w11-10.
