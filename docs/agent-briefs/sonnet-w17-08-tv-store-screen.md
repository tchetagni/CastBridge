# w17-08 — CastBridge-TV : tuile « Boutique », `StoreActivity` (D-pad, 720p, vues classiques : colonne de rayons, grille 4 × 2, fiche, « Louer » = demande + code court, « Mes locations » avec reste et alertes), branchement de `StoreApi`, essai et profil enfant en lecture

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (TV : liste humaine du propriétaire) · statut : **ATTEND la sortie du gel** (exception possible D-W17-11 : **TV d'abord**, lecture seule) ; w15-17 fusionné (`R/TvService.kt` : une ligne dans la chaîne d'extensions, règle W15 R2 : un seul cahier à la fois sur `TvService`)
> **Groupe : W17c-3** (vague W17c, TV) · prérequis : w17-02, 03, 04 fusionnés ; w16-01/10 **souhaités** (phrases par unité, bandeau) sinon `RentalStatus.message` existant · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.Store*' --tests 'castbridge.core.lint.*' && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 2,5 j) · audit Opus : échantillon

**Vague 17c · Effort L · Modèle : sonnet · Statut ATTEND.** Conception : DESIGN-W17 § 3 (tout), § 4.2, § 7.5, § 8.1 D-W17-2/5/6/7. Branche `claude/sonnet-w17-08`. Rapport : `docs/agent-reports/sonnet-w17-08.md`. TV de référence : GaiaOS 32 bits 720p, télécommande D-pad, peu de RAM : **aucune image**, aucun Compose, aucune WebView.

## Pourquoi (preuves)
- `R/PlayerActivity.kt:519-520` (`tile(feature, …)` → télémétrie + `ParentalHub.guardTile`), `:537-614` (`homeTools()` : insérer **une** tuile après `langues`, `:557-560`) ; `R/TvCards.kt:258` (`HomeTool`), `IconTile` ; `R/GamesUi.kt` (grille `Dx`, focus explicite : **patron à copier**) ; `R/LearnActivity.kt` (activité D-pad existante).
- `R/TvService.kt:251-258` (chaîne `ApiExtension` : `.then(LotsHub.api(this))` ⇒ ajouter `.then(StoreHub.api(this))`, **une ligne**) ; `R/LotsHub.kt:19-33` (patron d'un hub : `store(ctx)`, clés publiques `UpdateKeys.PUBLIC_KEYS + EXTRA_UPDATE_KEY`) ; `R/RentalHub.kt` (`statuses(app)`), `R/ActivationCenter.kt:100` (`trial()`), `R/ParentalHub.kt:268` (`kidHomeActive()`), `R/PolicyHub.kt` (`state` : drapeau `store.enabled`).
- `C/store/StoreApi.kt` (w17-04), `C/store/StoreView.kt` (`decide`, `Screen`), `C/store/RentRequests.kt` (`create`, `shortCode`), `C/store/StoreTexts.kt`, `C/store/StoreFlag.kt`.
- DESIGN-W11 § 4.7 (typographie TV : étiquettes ≥ 19 sp, états 16 sp, ≤ 150 ms, GPU seulement) ; `C/owner/KeyBadge.kt` (badge reste visible).

## Fichiers possédés
Nouveaux `R/StoreHub.kt` (objet : `files/store`, `StoreApi` avec faits réels : `LotsHub.store(ctx).manifest()`, `RentalHub.statuses`, `ActivationCenter.trial()`, `ParentalHub.kidHomeActive()`, `TvAccess.granted`, drapeau = `StoreFlag.enabled(settings = null, flagOrder = PolicyHub.state.flags["store.enabled"], compiledDefault = BuildConfig.DEBUG)`), `R/StoreActivity.kt`, `R/StoreViews.kt` (cartes, colonne, fiche, « Mes locations », écran « Demande enregistrée » avec le code court en 48 sp), `android/receiver/src/main/res/drawable/ic_t_store.xml` ; modifiés `R/PlayerActivity.kt` (**une** tuile `tile("store", …)` après `langues`, visible si `StoreHub.enabled(this)`), `R/TvService.kt` (**une** ligne `.then(StoreHub.api(this))`), `android/receiver/src/main/AndroidManifest.xml` (une activité `exported=false`). **Hors zone** : `R/LotsHub.kt`, `R/RentalHub.kt`, `R/ParentalHub.kt`, `R/LearnActivity.kt`, `C/**`, `S/**`, `R/WorksActivity.kt` (w10-09 : deviendra un rayon ici, plus tard).

## Étapes
1. `StoreHub` : `api(ctx)`, `enabled(ctx)`, `screen(ctx): StoreView.Screen` (reconstruit à l'ouverture et à chaque `RentalHub` changement : écoute existante de `ActivationCenter`), `request(ctx, item, choice): Result` (⇒ `RentRequests.create(facts, origin=tv)`), `pending(ctx)`.
2. `StoreActivity` : colonne gauche (Apprendre · Langues · Quiz · Mes locations ; ↑↓), grille 4 × 2 (← → OK, défilement par rangée, mémoire du focus par rayon), ligne de détail (≤ 20 mots, 21 sp), rappel des couleurs (verte Louer · jaune Mes locations · bleue Aide) **jamais l'unique chemin** (OK ouvre la fiche) ; Retour : fiche → grille → accueil (jamais de double Retour) ; textes **uniquement** `card.lines`, `StoreTexts`.
3. Fiche : titre, contenu (titres des lots), taille, état, boutons selon `card.rentButton` (Louer / Prolonger / Relouer) ou phrase `blocked.message` ; « Louer » ⇒ choix de durée : **trois lignes** (« Sans durée précise : 30 jours » · « Jours : 1 · 3 · 7 · 14 » · « Heures d'utilisation : 1 · 3 · 6 · 12 · 24 · 48 · 96 », libellés exacts W16 § 1.3, aide d'une ligne, date de fin réelle) ⇒ `StoreHub.request` ⇒ écran « Demande enregistrée : CM2 · 12 heures d'utilisation. Ouvrez CastBridge sur le téléphone, ou donnez ce code : **CM2-12H-7K3Q** » ; refus ⇒ phrase du cœur (« Demandez à un parent », « Version complète nécessaire », « 3 locations en cours »…).
4. Mes locations : une ligne par `RentalStatus` (phrase W16 si disponible, sinon `message`), badge d'alerte (`RentalWarning`), ligne « Test gratuit : les heures se terminent au plus tard le JJ/MM » (si w16-10 l'expose ; sinon absente, dit au rapport) ; demandes en attente : « Demande en cours : CM2 · 12 heures d'utilisation (03/10) · code CM2-12H-7K3Q ».
5. Tuile : `tile("store", R.drawable.ic_t_store, "Boutique", "Leçons, langues et quiz à envoyer ou à louer depuis le téléphone.", StoreHub.status(this) /* « 3 rayons » / « 1 demande » / « Boutique vide » */, StoreHub.hasCatalog(this)) { startActivity(StoreActivity) }` ; essai : visible (pas dans `CLOSED_TILES`) ; mode enfant : visible (`KID_HOME`, w17-02) ; drapeau éteint ⇒ **aucune tuile**, accueil identique à aujourd'hui.
6. Budget mesuré : `adb shell dumpsys meminfo castbridge.receiver` **par le propriétaire** (jamais par l'agent) avant/pendant `StoreActivity` : objectif < 1,5 Mo de delta PSS Java ; ouverture < 300 ms sur la TV de référence (chronomètre humain) ; aucune allocation d'image.
7. Lint de pureté ; `compileDebugKotlin` ; fumée W14 `--tv emu` (APK verrouillée de TEST, `FakeTv`/émulateur) : capture `/api/screenshot` de la grille ; liste humaine `docs/test-plans/STORE-PILOT.md` (w17-10) à faire par le propriétaire.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin
cd android && gradle --offline :core:test --tests 'castbridge.core.lint.*' --tests 'castbridge.core.journey.Store*'
grep -c '"store"' android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt   # ≥ 1 (une tuile)
grep -c 'StoreHub.api' android/receiver/src/main/kotlin/castbridge/receiver/TvService.kt   # = 1
grep -rn 'Demandez à un parent\|Version complète nécessaire\|Sur cette TV' android/receiver/src/main/kotlin/castbridge/receiver/Store*.kt   # 0 hit (tout vient du cœur)
grep -n 'exported="false"' android/receiver/src/main/AndroidManifest.xml | grep -i store    # activité non exportée
```
TV de référence (propriétaire) : navigation complète à la télécommande ; demande créée et code lisible à 3 m ; TV d'essai : vitrine visible, « Louer » refusé ; profil enfant : « Demandez à un parent » ; drapeau éteint : accueil inchangé ; captures `docs/img/store/tv-*.png`.

## Cas limites
Catalogue absent ⇒ rayons vides + « Boutique vide : rapprochez le téléphone » ; `files/store` corrompu ⇒ idem (jamais un plantage : `StoreFiles` tolérant) ; horloge douteuse ⇒ « Vérifiez l'heure de la TV » sur la location (phrase existante) ; `RentalHub` sans clé d'installation (`unavailable`) ⇒ vitrine normale, demande **acceptée** (la clé ne sert qu'à la livraison) ; 40 articles ⇒ 5 pages de grille, défilement par rangée sans animation.

## À ne pas faire
Pas de Compose, pas de WebView, pas d'image, pas de téléchargement ; pas de texte décidé dans `R/` ; ne pas modifier `LotsHub`, `RentalHub`, `ParentalHub`, `LearnActivity` ; aucune seconde tuile ; aucune commande `adb` par l'agent ; pas de montant.

## Rapport
`STATUT`, captures, mesures du propriétaire (mémoire, ouverture), ce qui dépend de w16-01/10, question : exception D-W17-11 (TV d'abord, lecture seule) ou attente ; grille 4 × 2 confirmée (D-W17-5).
