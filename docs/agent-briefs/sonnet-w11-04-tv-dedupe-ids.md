# w11-04 — CastBridge-TV : suppression des doublons tuile/menu, tuiles identifiées par id (essai, mode enfant), ordre par usage, ids de télémétrie
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-a** (vague W11) · prérequis : w11-03 · porte : `gradle --offline :core:test --tests 'castbridge.core.parental.KidHomeIdsTest'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Vague 11a (gain rapide, après w11-03) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT (après w11-03).** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 1.2, § 9.1 (Q4, Q5, Q7). Branche `claude/sonnet-w11-04`. Rapport : `docs/agent-reports/sonnet-w11-04.md`.

## Objectif
Chaque fonction de l'accueil TV a **un** chemin : les entrées du menu « Connexion & réglages » déjà portées par une tuile disparaissent du menu (≤ 12 lignes) ; « Recevoir du téléphone » et « Aide » fusionnent ; la rangée d'outils est triée par usage ; l'essai et le mode enfant filtrent les tuiles par **id** (corrige « Jeux » invisible en mode enfant) ; `langues` entre dans le catalogue de télémétrie.

## Pourquoi (preuves)
- `android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt:704-748` : 22 à 24 entrées de menu, dont Bibliothèque, Téléchargements, Ajouter un téléphone, Bluetooth visible, Wi-Fi Direct, USB (×2), Internet, Mises à jour, Options développeur, SSH **déjà** tuiles (`:544-607`).
- `PlayerActivity.kt:565` et `:606` : « Recevoir du téléphone » et « Aide » appellent toutes deux `homeApi().openHelp()`.
- `PlayerActivity.kt:545` et `:576` : deux tuiles avec l'id télémétrie `bluetooth`.
- `PlayerActivity.kt:550` : id `langues` absent de `EventCatalog.TV_FEATURES` (`core/.../telemetry/Telemetry.kt:16-17`).
- `ParentalHub.kt:271-280` filtre par étiquette ; `core/.../parental/ParentalModel.kt:227` `KID_HOME = listOf("Apprendre", "Quiz", "Échecs", "Bibliothèque", "Aide", "Contrôle parental")` alors que la tuile s'appelle « Jeux » (`PlayerActivity.kt:555`) ⇒ en mode enfant la tuile Jeux est retirée.
- `HomeScreen.kt:174-177` : focus initial sur la première carte vidéo ; la rangée d'outils vient avant les rangées (`:151-152`).

## Fichiers possédés
- Modifiés : `android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt` (`homeTools()`, `menuItems()`, `tile()` seulement), `android/receiver/src/main/kotlin/castbridge/receiver/ParentalHub.kt` (`filterHome`, `TRIAL_CLOSED_LABELS` → ids), `android/core/src/main/kotlin/castbridge/core/parental/ParentalModel.kt` (`KID_HOME` → `KID_HOME_IDS`, `kidHome(ids, p)`), `android/core/src/main/kotlin/castbridge/core/telemetry/Telemetry.kt` (`TV_FEATURES` + `langues`, `parental` reste absent volontairement).
- Nouveau : `android/core/src/test/kotlin/castbridge/core/parental/KidHomeIdsTest.kt`.
- Si un test existant référence `KID_HOME` ou `kidHome(` (chercher `grep -rn "kidHome\|KID_HOME" android/core/src/test`), il est possédé aussi et mis à jour.
- Hors zone : `TvCards.kt`, `HomeScreen.kt` (w11-03), `TrialPolicy.kt` (ses `CLOSED_TILES` sont déjà des ids : les utiliser), `Games.kt`.

## Signatures à respecter
```kotlin
// C/parental/ParentalModel.kt — ParentalRules
val KID_HOME_IDS = listOf("learn", "games", "library", "help", "parental")
fun kidHome(ids: List<String>, p: ChildProfile): List<String>   // garde l'ordre d'entrée ; "games" retiré si Category.GAMES in p.blocked
// R/ParentalHub.kt
fun filterHome(all: List<HomeTool>): List<HomeTool>   // filtre sur HomeTool.id ; essai : id !in TrialPolicy.CLOSED_TILES
```

## Étapes
1. `tile(feature, …)` passe `id = feature` au `HomeTool` ; la tuile « Contrôle parental » (`:604`) reçoit `id = "parental"` sans appel `TvConnect.feature` (règle existante) ; la tuile « Langues » garde `feature = "langues"`.
2. Fusion : supprimer la tuile « Recevoir du téléphone » (`:565`) ; la tuile « Aide » prend l'état `"Code ${ParentalHub.shownPin(pin)}"` et `on = true`. Deuxième tuile « Bluetooth » (`:576-581`) : garder, mais `feature = "bluetooth_visible"` n'existe pas dans le catalogue ⇒ la fusionner dans « Ajouter un téléphone » (`:545`) dont OK ouvre `choose("Téléphone", [Ajouter un téléphone, Rendre la TV visible (2 min)])`.
3. Ordre de `homeTools()` : upgrade (essai) · library · learn · langues · games · bluetooth (Ajouter un téléphone) · parental · remote · usb · downloads · internet · wifi_direct · admin · updates · settings · dev_options · help.
4. `menuItems()` : ne garder que ce qu'aucune tuile ne porte : Toute la bibliothèque (si `current == null`, **supprimer** : c'est la tuile), Stockage : où ranger (si non dans la tuile USB — elle y est : supprimer), Stockage : choisir un dossier (sélecteur système), Stockage : oublier le dossier choisi, Démarrer avec la TV, Lecture à distance (overlay), Confidentialité, Connexion au serveur, À propos : Assistance à distance, Tester le relais Bluetooth, Quiz (déjà dans Jeux : supprimer). Résultat attendu : **≤ 9 lignes**. Pendant une vidéo (`showMenu()` avec `current != null`) la même liste s'affiche : vérifier que rien d'utile pendant une lecture n'a disparu (Mises à jour reste joignable par la tuile après la vidéo ; acceptable).
5. `ParentalHub.filterHome` : par id (voir signature) ; supprimer `TRIAL_CLOSED_LABELS`. `ParentalRules.kidHome` par ids ; test `KidHomeIdsTest` : (a) profil enfant sans blocage ⇒ `[learn, games, library, help, parental]` ; (b) `GAMES` bloqué ⇒ sans `games` ; (c) l'ordre d'entrée est conservé ; (d) un id inconnu est retiré.
6. `Telemetry.kt` : ajouter `"langues"` à `TV_FEATURES` ; si un test fige la liste (`grep -rn TV_FEATURES android/core/src/test`), le mettre à jour.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.parental.KidHomeIdsTest'   # vert, 4 cas
gradle --offline :core:test   # tout vert
grep -c 'homeApi().openHelp()' receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt   # 1
grep -c 'TRIAL_CLOSED_LABELS' receiver/src/main/kotlin/castbridge/receiver/ParentalHub.kt       # 0
grep -c 'items += ' receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt               # ≤ 10
grep -c '"langues"' core/src/main/kotlin/castbridge/core/telemetry/Telemetry.kt                  # 1
```
Observable : accueil TV en production ⇒ 16 tuiles (18 − Recevoir − Bluetooth) ; MENU ⇒ ≤ 9 lignes ; mode enfant actif ⇒ tuiles Apprendre, Jeux, Bibliothèque, Aide, Contrôle parental ; essai ⇒ ni Bibliothèque, ni Clé USB, ni Téléchargements, ni Langues.

## Cas limites
`engineOrNull == null` (parental jamais configuré) ⇒ `filterHome` ne filtre que l'essai. Un `HomeTool` à `id = ""` (ancien appel) ⇒ conservé (jamais filtré) et signalé dans le rapport. `TrialPolicy.CLOSED_TILES` contient `receive` : la tuile n'existe plus, la route reste fermée (inchangé).

## À ne pas faire
Ne pas changer le nombre de tuiles au-delà de la fusion (c'est w11-10). Ne pas toucher à `StatusBarView`, `KeyBadgeOverlay`, `SettingsPanel`. Ne pas retirer d'entrée de menu qu'aucune tuile ne porte.

## Rapport
`STATUT`, tableau « fonction → chemin unique » (16 tuiles + ≤ 9 lignes), résultat des tests, capture du mode enfant.
