# w3-11 — Rendre les apps testables : logique pure extraite vers le cœur, écrans Quiz en fichiers séparés

**Vague 3 · Effort M (≈ 2 j) · Statut PRÊT** (après w2-04 : `PlayerActivity` stabilisé). Branche `claude/sonnet-w3-11`. Rapport : `docs/agent-reports/sonnet-w3-11.md`.

## Objectif
1. `homeTools()`/`tile()`/`updateStatus()` et `menuItems()` de `PlayerActivity` deviennent des fonctions pures en cœur (`HomeTools`) prenant une structure d'entrées (états, essai, verrou) et rendant une liste typée ; `ParentalHub.TRIAL_CLOSED_LABELS` est remplacé par un filtre par **id** via la même table.
2. Les aides de `QuizActivity` (`board()`, `bankNote()`, `count()`, extensions `Map<String, Any?>`, `stats()`) deviennent `QuizText`/`QuizStats` en cœur avec tests ; les 9 écrans internes passent dans des fichiers séparés avec une petite interface `QuizHost`.
3. Tests cœur pour tout ce qui est extrait.

## Pourquoi (preuves)
- `android/receiver/src/test`, `android/sender/src/test` : inexistants ; aucune `testImplementation` d'app ; le projet a déjà le bon réflexe (`LaunchPolicy`, `TrialPolicy`, `TvGate`, `StatusIconModel` en cœur).
- `R/PlayerActivity.kt:444-577` : catalogue d'outils d'accueil de 80 lignes et `menuItems()` `:669-718` : logique de décision (statuts, essai) dans l'activité.
- `R/ParentalHub.kt:271` : `TRIAL_CLOSED_LABELS = setOf("Bibliothèque", …)` dupliqué **par libellé** de `C/owner/TrialPolicy.kt:11` `CLOSED_TILES` (ids) ; libellés aussi dupliqués `R/PlayerActivity.kt:511` / `R/HomeScreen.kt:199`.
- `R/QuizActivity.kt:39-981` : 9 classes `Screen` internes (`:183,269,361,455,693,756,840,909,940`), aides `:93-102,232-240,266`, statistiques `:145-189`.
- Audit : AR-2, AR-4, AR-8, TE-1.

## Fichiers possédés
`R/PlayerActivity.kt`, `R/QuizActivity.kt`, nouveaux `R/quiz/*.kt` (un fichier par écran), nouveau `C/tv/HomeTools.kt`, nouveau `C/quiz/QuizText.kt`, nouveau `C/quiz/QuizStats.kt` (si `stats()` est pur), nouveaux `android/core/src/test/kotlin/castbridge/core/tv/HomeToolsTest.kt`, `android/core/src/test/kotlin/castbridge/core/QuizTextTest.kt`, `R/ParentalHub.kt` (**`TRIAL_CLOSED_LABELS` seulement**), `R/HomeScreen.kt` (libellés → `HomeTools`). **Hors zone** : `TrialPolicy.kt` (lire seulement), `KeyBadge*`, `TvService`, `ReceiverServer`, `build.gradle.kts` (w3-12).

## Étapes
1. `HomeTools` (cœur) : `data class HomeInputs(trial: Boolean, locked: Boolean, statuses: Map<String, String>, hasUsb: Boolean, …)` → `List<HomeTool(id, label, subtitle, icon: String, enabled, closedReason: String?)>` ; les libellés français sont définis **une fois** ici (table id → libellé) ; `TrialPolicy.CLOSED_TILES` consulté par id ; `PlayerActivity.homeTools()` appelle `HomeTools.build(inputs)` et ne garde que la création de vues ; `HomeScreen` et `ParentalHub` lisent la table (ids).
2. `menuItems()` : même approche (`HomeTools.menu(inputs)`).
3. `QuizText` : `board(room)`, `bankNote(...)`, `count(...)`, extensions de lecture de `Map` ; `QuizStats` : accumulateur pur ; tests avec des cartes/états synthétiques tirés des tests existants de `QuizRoom`.
4. Écrans Quiz : déplacer chaque classe interne dans `R/quiz/<Nom>Screen.kt` avec `interface QuizHost { val main: Handler; val room: …; fun push(screen); fun goHome(); fun confirm(...); fun choose(...); fun sound(...); fun stage(...) }` implémentée par `QuizActivity` ; **aucun** changement de comportement.
5. Vérifier qu'aucune chaîne utilisateur n'a changé (`git diff | grep '^[-+].*"'`).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.tv.HomeToolsTest' --tests 'castbridge.core.QuizTextTest' --tests 'castbridge.core.*Quiz*'   # vert
grep -n 'TRIAL_CLOSED_LABELS' android/receiver/src/main/kotlin/castbridge/receiver/ParentalHub.kt   # 0 hit (remplacé par un filtre par id)
grep -c '"Bibliothèque"' android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt android/receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt   # 0 et 0 (libellé défini en cœur)
wc -l android/receiver/src/main/kotlin/castbridge/receiver/QuizActivity.kt   # ≤ 350
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (si SDK)
```
Observable (campagne, étapes 10-11) : accueil, menu, tuiles fermées en essai, quiz : identiques.

## Cas limites
- Les icônes sont des ressources Android : le cœur ne manipule que des **noms** (`"ic_cb_cle"`), l'activité résout l'id.
- `ParentalHub` doit continuer à masquer les tuiles fermées pour un profil enfant **et** en essai : combiner les deux filtres par id.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; aucun changement de comportement ni de chaîne ; pas de Robolectric (décision : extraction vers le cœur).

## Rapport
`STATUT`, fonctions extraites (signatures), tests ajoutés, lignes avant/après.
