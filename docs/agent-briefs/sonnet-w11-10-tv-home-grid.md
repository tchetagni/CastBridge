# w11-10 — CastBridge-TV : accueil à 6 grandes tuiles (`HomeGrid`), focus explicite depuis `TvFocusGraph`, mémoire de focus, touches de couleur, veille, interrupteur `NAV_V2`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-d** (vague W11) · prérequis : w11-04, w11-05, w11-06 · porte : `grep -c 'TvFocusGraph.home' android/receiver/src/main/kotlin/castbridge/receiver/HomeGrid.kt android/receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt  # ≥ 1`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui

**Vague 11d (TV) · Effort L (≈ 3,5 j) · Modèle : sonnet · Statut PRÊT (après 11a, w11-05, w11-06).** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 4.1, § 4.3-4.7, § 5, § 7.3, § 9.4-9.5. Branche `claude/sonnet-w11-10`. Rapport : `docs/agent-reports/sonnet-w11-10.md`.

## Objectif
Derrière `BuildConfig.NAV_V2` (Gradle `-PnavV2=true`, défaut `false`), l'accueil TV montre la rangée « Reprendre » (≤ 6 cartes) puis une **grille 3 × 2** de grandes tuiles (`TvNav.tiles(state)`), avec des voisins de focus **explicites** (`nextFocusUpId`…) calculés par `TvFocusGraph.home(...)`, une mémoire de focus au retour, quatre touches de couleur en raccourci et un rappel à 4 pastilles, l'en-tête réduit (logo, badge court, horloge, ≤ 3 puces de statut). Les anciennes rangées « Récemment ajoutés », « Sur la clé USB », « Toutes les vidéos », « Autres fichiers » passent dans « Regarder » (la grille existante). `NAV_V2=false` conserve l'accueil actuel.

## Pourquoi (preuves)
- `android/receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt:140-146` : 5 rangées + rangée d'outils de 18 tuiles (`:183-213`) ; focus initial toujours réinitialisé (`:113`, `show()` → `reload(focusFirst = true)`).
- `PlayerActivity.kt:1103` : seule la touche BLEUE est un raccourci ; `:1052-1064` : gestion des touches sur l'accueil.
- `HomeScreen.kt:126` : la puce « ● Prêt à recevoir · code 12•••• » dans l'en-tête ; conception § 4.6 : fusionnée dans la tuile « Téléphone ».
- `TvCards.kt:34` et § 4.7 : étiquette ≥ 19 sp (tuiles de la grille : 27 sp `Type.TITLE`), état 19 sp.

## Fichiers possédés
- Nouveaux : `android/receiver/src/main/kotlin/castbridge/receiver/HomeGrid.kt` (`HomeGrid`, `BigTile`, `ColorHint`), `android/receiver/src/main/kotlin/castbridge/receiver/NavStateTv.kt` (calcule `NavState` depuis `ActivationCenter`, `ParentalHub`, `TvAccess.degraded` si w4-07 est fusionné).
- Modifiés : `android/receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt` (quand `NAV_V2` : `rowsBox` = Reprendre + `HomeGrid` ; en-tête sans la puce « prêt · code » ; `show(restoreFocus = true)` ; veille), `android/receiver/build.gradle.kts` (`NAV_V2`), `android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt` (**seulement** : `homeApi()` gagne `fun navTiles(): List<HomeTool>` et `fun openTile(id: String)` ; `onKeyDown` : touches de couleur ; appel de `KeyBadgeOverlay.setPlaying` de w11-02 dans `hideScreens()`/`showHome()`), `android/receiver/src/main/kotlin/castbridge/receiver/StatusBarView.kt` (`maxChips = 3` sur l'accueil : au-delà, « +n »).
- Hors zone : `PlusPanel.kt`, `PhonePageActivity.kt` (w11-11/12 : ce cahier appelle `openTile("plus")` → `showSettings()` et `openTile("phone")` → `PairActivity.open` **provisoirement**), `LibraryScreen.kt`, `TvCards.kt`.

## Signatures à respecter (contrat pour w11-11, w11-12)
```kotlin
// HomeScreen.Api (ajouts)
fun navTiles(): List<HomeTool>          // 6 HomeTool au plus, id = TvNav ids, status dynamique ≤ 3 mots (TileText.status)
fun openTile(id: String)                // "library" → showLibrary(); "learn" → LearnActivity; "games" → GamesActivity; "phone" → page Téléphone (w11-12, provisoire PairActivity); "parental" → ParentalActivity; "plus" → PlusPanel (w11-11, provisoire showSettings())
fun tileActions(id: String): List<Pair<String, () -> Unit>>   // MENU / appui long : "library" → [Clé USB, Téléchargements] ; "learn" → [Langues, Profils] ; "phone" → [Diagnostic Internet]
// HomeGrid
class HomeGrid(act: Activity, graph: TvFocusGraph, tiles: List<HomeTool>, onOpen: (String) -> Unit, onActions: (String) -> Unit)
```

## Étapes
1. Gradle `NAV_V2` ; `NavStateTv.current()`.
2. `HomeGrid` : 2 `LinearLayout` horizontaux de `BigTile` (340 × 150 dp, radius `R_LG`, icône 56 dp à gauche, étiquette 27 sp `TvFonts.bold`, état 19 sp `TEXT2`, `focusable` + `TvStyle.focusZoom`) ; ids de vue stables (`View.generateViewId()` mémorisés par id de tuile) ; pour chaque tuile, `nextFocusLeftId/RightId/UpId/DownId` posés d'après `graph.next(...)` (`resume:j` → carte j de la `RecyclerView`, via `nextFocusUpId` vers la `RecyclerView` elle-même et `requestFocus` sur l'enfant j) ; aucune recherche de focus automatique.
3. `HomeScreen` (`NAV_V2`) : rangée « Reprendre » (adapter existant `RowAdapter`, liste `LibrarySections.RESUME` puis les 6 plus récents si vide) ; **si** `WorkHub` (w10-08/09) existe : rangée « Œuvres locales » (cartes des aperçus présents + dernière carte « Tout voir » → `WorksActivity`), filtrée par `ParentalHub` ; **jamais** de 7e tuile (D-W11-9 ; w10-09 doit lire l'index W11) ; sous elles `HomeGrid` ; `ColorHint` en bas (4 pastilles : rouge Téléphone, vert Apprendre, jaune Jeux, bleu Regarder ; 16 sp) ; héros : `heroTitle` = étiquette de la tuile ou titre de la carte, `heroSub` = état (une ligne) ; `show(restoreFocus)` : relit `TvPrefs("home_focus")` (`"tile:learn"` ou `"resume:0"`), sinon `TvFocusGraph.initialFocus` ; chaque changement de focus écrit la clé ; retour d'une vidéo ⇒ `resume:0`.
4. `PlayerActivity.onKeyDown` (accueil, `current == null`) : `PROG_RED` → `openTile("phone")`, `PROG_GREEN` → `openTile("learn")`, `PROG_YELLOW` → `openTile("games")`, `PROG_BLUE` et `LIBRARY_KEYS` → `openTile("library")`, `GUIDE`/`INFO` → `openTile("plus")` ; MENU sur une tuile → `tileActions`.
5. Veille : après 10 min sans touche sur l'accueil (`Handler`), `rowsBox.alpha = 0.4f` + `heroTitle.alpha = 0.4f` (animation 300 ms) ; toute touche restaure ; pas d'autre animation.
6. `StatusBarView` : `maxChips` (3 sur l'accueil) ; `bar.hidden` reflète le surplus.
7. Télémétrie : `TvConnect.feature(id, "tile")` conservé via `tile()` ; `"plus"` et `"phone"` seront ajoutés au catalogue par w11-14 (en attendant, `feature("settings","tile")` pour `plus`, `feature("bluetooth","tile")` pour `phone`).

## Critères d'acceptation
```sh
cd android && grep -c 'NAV_V2' receiver/build.gradle.kts receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt   # ≥ 1 chacun
grep -c 'nextFocusRightId\|nextFocusDownId\|nextFocusUpId\|nextFocusLeftId' receiver/src/main/kotlin/castbridge/receiver/HomeGrid.kt   # ≥ 4
grep -c 'TvFocusGraph.home' receiver/src/main/kotlin/castbridge/receiver/HomeGrid.kt receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt   # ≥ 1
grep -c 'KEYCODE_PROG_RED\|KEYCODE_PROG_GREEN\|KEYCODE_PROG_YELLOW' receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt   # 3
grep -c '"home_focus"' receiver/src/main/kotlin/castbridge/receiver/HomeScreen.kt   # ≥ 2
gradle --offline :core:test   # vert
```
Observable (`-PnavV2=true`, émulateur TV 1280 × 720 puis **TV de référence GaiaOS 32 bits**) : 6 tuiles visibles sans défiler ; depuis le focus initial, « Plus » en ≤ 5 appuis ; HAUT depuis la 1re rangée va à « Reprendre » puis à la puce puis aux statuts ; aucune impasse de focus (parcourir les 4 directions sur chaque nœud) ; retour d'une vidéo ⇒ focus sur « Reprendre » ; retour d'Apprendre ⇒ focus sur « Apprendre » ; touches de couleur ; mode enfant ⇒ 4 tuiles (sans Téléphone, Plus), grille recentrée ; essai ⇒ 6 tuiles, « Plus » état « Version complète » ; `NAV_V2=false` ⇒ accueil inchangé.

## Cas limites
Bibliothèque vide ⇒ pas de rangée Reprendre, focus initial `tile:library`, héros « Bienvenue… » (texte existant `HomeScreen.kt:167-168` **réduit** à : « Envoyez une vidéo depuis le téléphone : touche rouge pour voir le code »). Télécommande sans touches de couleur ⇒ rien ne manque. 4 tuiles (enfant) ⇒ une rangée de 3 + une de 1 : le graphe gère. Changement d'état en direct (profil enfant activé) ⇒ `refreshTools()` reconstruit la grille et garde le focus si la tuile existe encore, sinon `initialFocus`.

## À ne pas faire
Ne pas supprimer `tools()`/`fillTools` (rendu legacy). Ne pas écrire `PlusPanel` ni la page Téléphone. Ne pas allumer `NAV_V2` par défaut. Pas de `notifyDataSetChanged` sur la rangée Reprendre si la liste n'a pas changé (déjà `set(l)`), pas de flou ni d'animation de liste.

## Rapport
`STATUT`, captures (production, essai, enfant), parcours de focus sur la TV de référence (tableau nœud × direction), coûts observés vs. `TvFocusGraphTest`, mémoire RSS avant/après (`adb shell dumpsys meminfo`).
