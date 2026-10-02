# w11-08 — CastBridge (téléphone) : accueil « Que voulez-vous faire ? » — suggestions contextuelles, « Reprendre sur la TV », carte d'envoi unique ; `TvHome.kt` découpé
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-c** (vague W11) · prérequis : w11-05, w11-07 · porte : `grep -c 'QuickActions.suggest' android/sender/src/main/kotlin/castbridge/sender/nav/PhoneHome.kt  # 1`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 11c (téléphone) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w11-05 ; contrat `HomeHost` de w11-07).** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 3.3, § 7.1, § 8.1, § 9.3. Branche `claude/sonnet-w11-08`. Rapport : `docs/agent-reports/sonnet-w11-08.md`.

## Objectif
L'écran d'accueil (destination « Accueil » de w11-07) tient en **≤ 12 cibles et ≤ 40 mots** : puce TV (w11-07), 3-4 **suggestions** issues de `QuickActions.suggest`, rangée « Reprendre sur la TV », **une** carte « Envoi : 42 % · 2 en attente » (envoi + file fusionnés), bandeau « TV en mode enfant (prénom) » ou rangée « Reliez une TV en production pour tout débloquer » selon l'état. Les six tuiles, la carte « Classer les séries » et la ligne « Avancé » quittent l'accueil.

## Pourquoi (preuves)
- `android/sender/src/main/kotlin/castbridge/sender/TvHome.kt:168-357` : 34 cibles et ≈ 190 mots sur l'accueil (conception § 1.1) ; tuiles `:322-336` ; carte séries `:257-280` ; deux cartes d'envoi `:198-219` et `:226-250` ; « Avancé » `:351-356`.
- `TvHome.kt:139-160` (`pick`), `:161-165` (`cmd`), `:389-402` (`Poster`), `:404-456` (`FirstConnection`) : logique à **conserver** telle quelle.

## Fichiers possédés
- Nouveau : `android/sender/src/main/kotlin/castbridge/sender/nav/PhoneHome.kt` (`PhoneHome(host: HomeHost)`).
- Modifié : `android/sender/src/main/kotlin/castbridge/sender/TvHome.kt` : extraire en fonctions `internal` réutilisables (sans changer leur corps) `rememberTvClient()`, `rememberSendPicker(...)`, `SendCard(...)` (fusion envoi + file), `ResumeRow(...)`, `NowPlayingCard(...)`, `SeriesCard(...)` ; `TvHome` (legacy) les appelle et reste identique visuellement pour `NAV_V2=false`.
- Modifié : `android/sender/src/main/kotlin/castbridge/sender/TvLibraryScreen.kt` : `TvLibraryDialog` affiche `SeriesCard` en tête de liste (la carte quitte l'accueil).
- Hors zone : `nav/Shell.kt` (w11-07), `MainActivity.kt`, `TvPairScreen.kt`, `ConnectScreens.kt`.

## Signatures à respecter
```kotlin
@Composable fun PhoneHome(host: HomeHost)   // appelé par AppShell(home = { PhoneHome(it) })
internal object QuickDispatch { fun run(id: String, host: HomeHost, ctx: android.content.Context, client: TvClient?, pick: (move: Boolean) -> Unit) }
```
Ids de suggestion (de `QuickActions`) → action : `resume` → `cmd { play(name, resumeMs) }` du premier élément de `LibrarySections.RESUME` ; `send` → `pick(false)` ; `move` → `pick(true)` ; `watch` → `TvLibraryDialog` ; `remote` → `RemoteActivity.open` ; `lesson` → `host.go("learn")` ; `game` → `host.go("games")` ; `add_tv` → `host.openConnection()` ; `upgrade` → `ActivateTvActivity.open` ; `live` → feuille de diffusion de `PhoneLibraryScreen` (ouvre `host.go("files")`) ; `drill` (langue) → `host.go("learn")` ; `pilot` → `host.go("learn")` (sous-onglet « Piloter la TV » si exposé, sinon l'onglet).

## Étapes
1. Découper `TvHome.kt` (extractions pures ; `TvHome` legacy inchangé à l'écran : vérifier en `NAV_V2=false`).
2. `PhoneHome` : `Column(verticalScroll)` : `TvChip` (w11-07) → bandeau d'état (enfant / minimal, un seul, ≤ 8 mots) → `ResumeRow` (≤ 6 affiches) → titre « Que voulez-vous faire ? » (`titleLarge`) → grille 2 colonnes de `SuggestionCard` (hauteur ≥ 64 dp, icône de charte + libellé ≤ 3 mots + titre tronqué pour « Reprendre ») → `SendCard` (visible seulement pendant un envoi ou une file non vide) → `NowPlayingCard` si lecture.
3. `QuickContext` construit depuis : `LibrarySections.build(items)` (titre de reprise), `info.playing`, `NavStateProvider` (w11-07 ; si absent, `NavState()` par défaut), dernière classe (`LearnLotsHooks` / préférence `last_class` si elle existe, sinon `null`).
4. Télémétrie : `PhoneConnect.feature("quick_action", id)` — **ne pas** ajouter l'id au catalogue ici (w11-14 le fait avec la liste close) ; utiliser `PhoneConnect.feature("send")`, `"move"`, `"remote"`, `"tv_library"` existants pour les actions correspondantes.
5. Budget : test manuel « ≤ 40 mots » : compter avec `uiautomator dump` (w11-13 fournit le script ; ici, à la main).

## Critères d'acceptation
```sh
cd android && grep -c 'fun PhoneHome' sender/src/main/kotlin/castbridge/sender/nav/PhoneHome.kt   # 1
grep -c 'QuickActions.suggest' sender/src/main/kotlin/castbridge/sender/nav/PhoneHome.kt            # 1
grep -c 'Task(cbv' sender/src/main/kotlin/castbridge/sender/nav/PhoneHome.kt                        # 0 (pas de tuiles)
grep -c 'internal fun SendCard\|internal fun ResumeRow\|internal fun SeriesCard\|internal fun NowPlayingCard' sender/src/main/kotlin/castbridge/sender/TvHome.kt   # 4
grep -c 'SeriesCard(' sender/src/main/kotlin/castbridge/sender/TvLibraryScreen.kt                  # ≥ 1
```
Observable (`-PnavV2=true`) : TV reliée, vidéo en pause sur la TV ⇒ suggestions « Reprendre « titre » · Envoyer une vidéo · Télécommande » ; rien en cours ⇒ « Envoyer une vidéo · Regarder sur la TV · Une leçon · Un jeu » ; aucune TV ⇒ « Ajouter ma TV » en premier ; pendant un envoi, une seule carte « Envoi : 42 % · 2 en attente » avec « Annuler » ; ≤ 12 cibles, ≤ 40 mots ; en `NAV_V2=false`, l'ancien accueil est inchangé.

## Cas limites
Bibliothèque TV vide ⇒ pas de rangée Reprendre, 4 suggestions. Essai ⇒ « Regarder en direct · Une leçon d'essai · Sudoku · Passer en production ». Mode enfant ⇒ 2 suggestions (leçon, jeu) + bandeau. File avec un échec ⇒ la carte garde « Effacer les échecs ».

## À ne pas faire
Ne pas modifier `pick`, `cmd`, `FirstConnection`, `AddTvFlow`. Ne pas réintroduire de tuile ni de sous-titre. Ne pas inventer de libellé hors `NavTexts`/§ 8.1. Ne pas toucher à `Shell.kt`.

## Rapport
`STATUT`, captures des quatre contextes, comptage cibles/mots, confirmation que `NAV_V2=false` est inchangé (capture avant/après).
