# w11-07 — CastBridge (téléphone) : coquille de navigation — barre du bas à 5 destinations, feuille « Plus », puce TV unique, interrupteur `NAV_V2`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-c** (vague W11) · prérequis : w11-01, w11-05 · porte : `grep -c 'fun RootLegacy' android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt  # 1`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui

**Vague 11c (téléphone) · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après w11-01, w11-05 ; après w7-19 si W7 est lancée avant : D-W11-10).** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 3.1, § 3.2, § 3.5, § 5, § 6.2, § 9.4, § 11. Branche `claude/sonnet-w11-07`. Rapport : `docs/agent-reports/sonnet-w11-07.md`.

## Objectif
Derrière `BuildConfig.NAV_V2` (Gradle `-PnavV2=true`, défaut `false`), `MainActivity.Root()` rend une **`NavigationBar`** Material à 5 destinations (Accueil, Fichiers, Apprendre, Jeux, Parents) tirées de `PhoneNav.destinations(state)`, une barre du haut réduite à logo + ⋮ « Plus », une **feuille du bas « Plus »** groupée tirée de `PhoneNav.plus(state)`, et une **puce TV** d'une ligne qui ouvre une feuille « Connexion » (action unique de `LinkView`, « Mes TV », « Dépannage » = l'ancien `DiagnosticDialog`). Les écrans existants sont réutilisés tels quels ; `NAV_V2=false` conserve l'ancien `Root()` (après w11-01).

## Pourquoi (preuves)
- `MainActivity.kt:93-133` : `Scaffold` avec `TopAppBar` + `ScrollableTabRow` à 6 onglets (`:115-123`), dont deux hors écran sur un téléphone étroit (commentaire `:114`).
- `TvPairScreen.kt:81-96` : la puce de liaison porte titre, détail, indice, un bouton d'action et deux `TextButton` permanents (« Mes TV », « Diagnostic »).
- w6-16 (`docs/agent-briefs/sonnet-w6-16-phone-gate-runtime-wall.md`, « À ne pas faire ») : « Ne pas masquer d'onglet » ⇒ les destinations fermées restent visibles avec un cadenas et ouvrent le mur.
- Conception § 6.2 : la puce « TV cible » de W6 **est** cette puce (une seule puce de liaison par écran).

## Fichiers possédés
- Nouveaux : `android/sender/src/main/kotlin/castbridge/sender/nav/Shell.kt` (`AppShell`, `BottomNav`, `PlusSheet`, `TvChip`, `ConnectionSheet`), `android/sender/src/main/kotlin/castbridge/sender/nav/NavStateProvider.kt` (calcule `NavState` depuis `ActivationScreenState`/`TvLinkManager`/`ParentalSession` ; `PhoneGateRuntime` de w6-16 si présent, sinon `phoneMinimal = false`).
- Modifiés : `android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt` (`Root()` : `if (BuildConfig.NAV_V2) AppShell(...) else RootLegacy()`), `android/sender/build.gradle.kts` (`buildConfigField("boolean", "NAV_V2", …)` lu de `-PnavV2`), `android/sender/src/main/kotlin/castbridge/sender/TvPairScreen.kt` (extraire `TvLinkStatus` en deux composables : `TvChipLine(link)` à une ligne et `TvLinkActions(link, …)` ; l'ancien `TvLinkStatus` reste pour `NAV_V2=false`).
- Hors zone : `TvHome.kt` (w11-08 : l'accueil est un paramètre `home: @Composable () -> Unit` de `AppShell`, ce cahier y place `TvHub()` provisoirement), `ConnectScreens.kt`, `LearnScreen.kt`, `GamesScreen.kt`, `ParentalTab.kt`, `PhoneLibrary.kt`, écrans W5 (`S/shop/**`).

## Signatures à respecter (contrat pour w11-08, w11-09)
```kotlin
// S/nav/Shell.kt
@Composable fun AppShell(state: NavState, home: @Composable (HomeHost) -> Unit)
interface HomeHost { fun openPlus(); fun openConnection(); fun go(destination: String) }
@Composable fun TvChip(link: LinkUi, onClick: () -> Unit)                 // 1 ligne : point + nom + état ≤ 3 mots
@Composable fun PlusSheet(groups: List<PlusGroup>, onPick: (String) -> Unit, onDismiss: () -> Unit)
```
Correspondance id → écran (dans `Shell.kt`, fonction `openEntry(id)`) : `remote` → `RemoteActivity.open` ; `tv_library` → `TvLibraryDialog` (client courant) ; `file_exchange` → `TvTransferDialog` ; `downloads` → `DownloadsEntry` (sa `Dialog`) ; `shop` → `ShopScreen` si la classe existe (w5-11), sinon ligne absente ; `activate` → `ActivateTvActivity.open` ; `rentals` → `RentalDeliveryActivity.open` ; `lots` → `LotsScreen` ; `free_content` → `FreeContentActivity.open` ; `settings` → `SettingsScreen` ; `advanced` → `TvHubAdvanced()` plein écran avec « Accueil » en retour ; `help` → dialogue d'aide (3 étapes, texte repris de `PlayerActivity.kt:448-454` côté TV, adapté au téléphone) ; `focal` → `S/focal` si présent.

## Étapes
1. Gradle : propriété `navV2` (défaut `false`) → `BuildConfig.NAV_V2`.
2. `Shell.kt` : `AppShell` = `Scaffold(topBar = logo + ⋮, bottomBar = Column { CastMiniBar; NavigationBar })` ; destination sélectionnée mémorisée (`last_tab` de w11-01 réutilisé avec les nouveaux ids) ; une destination `LOCKED` affiche un cadenas (`Icons.Filled.Lock` en badge) et, à la sélection, ouvre `GateWall` si w6-16 est présent, sinon un `AlertDialog` avec le texte `PhoneGateTexts` si présent, sinon « Reliez une TV en production pour tout débloquer ».
3. Destination → contenu : `home` → paramètre `home` ; `files` → `PhoneLibraryScreen()` ; `learn` → `LearnScreen()` ; `games` → `GamesScreen()` ; `parents` → `ParentalTab()`.
4. `PlusSheet` : `ModalBottomSheet`, titres de groupe en `labelMedium` majuscules, lignes = icône + libellé (pas de sous-titre), 2 colonnes ; cadenas sur `Lock.LOCKED`.
5. `TvChip` + `ConnectionSheet` : glyphes de W7 § 8.2 (`●` liée, `◐` reconnexion, `○` hors de portée, `✕` action requise) dérivés de `LinkView.tone`/`LinkState` ; la feuille contient `TvLinkActions` (bouton d'action unique de `LinkView`, « Mes TV » → `ManageTvsDialog`, « Réparer la connexion » → `S/link/RepairScreen.kt` de w7-19 **si la classe existe**, sinon `DiagnosticDialog`) ; en minimal W6, une ligne « Preuve : il y a 2 h · production » si `ProofStore` existe. Textes : `LinkTexts` (w7-08) s'il existe, sinon `LinkText` existant ; aucun texte nouveau dans `Shell.kt`.
6. TalkBack : `contentDescription` sur chaque destination = libellé ; sur la puce = `view.detail`.
7. Ne rien supprimer : `RootLegacy()` = l'ancien `Root()` renommé.

## Critères d'acceptation
```sh
cd android && grep -c 'NAV_V2' sender/build.gradle.kts sender/src/main/kotlin/castbridge/sender/MainActivity.kt   # ≥ 1 chacun
grep -c 'NavigationBarItem' sender/src/main/kotlin/castbridge/sender/nav/Shell.kt    # ≥ 1 (5 via boucle)
grep -c 'PhoneNav.destinations\|PhoneNav.plus' sender/src/main/kotlin/castbridge/sender/nav/Shell.kt   # 2
grep -c 'fun RootLegacy' sender/src/main/kotlin/castbridge/sender/MainActivity.kt   # 1
gradle --offline :core:test   # vert (aucun changement cœur attendu)
```
Observable (`-PnavV2=true`, émulateur) : 5 destinations toutes visibles sur un écran de 360 dp de large, libellés sur 1 ligne ; ⋮ ouvre « Plus » avec 4 groupes et ≤ 12 lignes ; chaque ligne ouvre l'écran listé ; la puce TV tient sur 1 ligne ; toucher la puce ouvre la feuille avec au plus 3 boutons ; en essai (TV en essai) la ligne « Passer en production » est première ; grand texte 1,3× : libellés encore sur 1 ligne ou icônes seules + libellé actif.

## Cas limites
Aucune TV ajoutée ⇒ puce « ○ Aucune TV » et feuille avec « Ajouter ma TV » seul. Deux TV ⇒ la feuille propose le sélecteur de `ManageTvsDialog`. Écran de consentement et mise à jour obligatoire : inchangés, avant `AppShell`. Rotation : destination conservée (`rememberSaveable`).

## À ne pas faire
Ne pas modifier le contenu des écrans existants. Ne pas allumer `NAV_V2` par défaut. Ne pas masquer une destination. Ne pas réécrire `TvHome.kt` (w11-08). Pas de nouvelle chaîne hors `NavTexts` (les libellés viennent de w11-05).

## Rapport
`STATUT`, captures (barre du bas, Plus, puce + feuille Connexion, état minimal avec cadenas), table id → écran vérifiée à la main, taille du `Root()` ancien conservé.
