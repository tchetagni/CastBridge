# w17-07 — CastBridge (téléphone) : écran « Boutique » (rayons Apprendre / Langues / Quiz / Ma TV, cartes d'article, « Louer gratuitement » → sélecteur W16 → demande, « Envoyer à la TV » existant, demandes de la TV à confirmer), entrée à la place de « Locations », bouton dans « Données hors ligne »

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : **ATTEND la sortie du gel** (exception possible D-W17-11) et w17-06 fusionné
> **Groupe : W17c-2** (vague W17c, téléphone) · prérequis : w17-02 (`StoreView`, `StoreTexts`), w17-06 ; w16-11 **souhaité** (`RentalPickerScreen`/`PickerModel`) sinon sélecteur local **lisant** `PilotRules` ou la grammaire `defaut|<N>j|<H>h` · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.store.*' --tests 'castbridge.core.lint.*' && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 2 j) · audit Opus : échantillon

**Vague 17c · Effort L · Modèle : sonnet · Statut ATTEND.** Conception : DESIGN-W17 § 2 (tout), § 6, § 7.5, § 8.1 D-W17-1/12. Branche `claude/sonnet-w17-07`. Rapport : `docs/agent-reports/sonnet-w17-07.md`. **Rien n'est dupliqué** de `LotsScreen` : la Boutique appelle `LotsRuntime`.

## Pourquoi (preuves)
- `S/MainActivity.kt:107-110` (actions de la barre : « Locations » → `RentalDeliveryActivity.open`) ; `S/LotsScreen.kt:24-28` (`LotsEntry`), `:79-82` (ligne de boutons), `:94-113` (carte de lot : **le patron** de la carte d'article), `:107` (`LotsToDeliver.sendButton`), `:167-199` (`LanguagesSection` : boutons Langues) ; `S/LotsRuntime.kt:110-114, 229-240` (`downloadLanguage`, `deliverLot`, `downloadAndSendLanguage`), `:270-277` (`status`, `tvBudgetText`).
- `S/RentalDeliveryActivity.kt` (« Locations sur la TV », réutilisé tel quel pour « Livraison avancée ») ; `S/Theme.kt` (`Cb`, `CastTheme`).
- `C/store/StoreView.kt` (`decide`, `Card`, `RentButton`), `C/store/StoreTexts.kt`, `C/store/StoreFlag.kt` (w17-02) ; `S/store/StoreRuntime.kt` (w17-06 : `store()`, `facts()`, `confirm`, `refuse`, `refreshBundles`).
- DESIGN-W11 § 3.2 (`Plus › Boutique et clés › Boutique` : quand W11 arrive, l'action de la barre disparaît et l'écran reste).

## Fichiers possédés
Nouveaux `S/store/StoreActivity.kt` (hôte Compose, `exported=false`), `S/store/StoreScreen.kt` (rayons, cartes), `S/store/StoreItemCard.kt`, `S/store/StoreRequestsScreen.kt` (« Demandes de la TV » : confirmer / refuser), `S/store/StoreMyTvSection.kt` ; modifiés `S/MainActivity.kt` (**une** action : `if (StoreFlag.enabled(...)) "Boutique" → StoreActivity else "Locations"` inchangé), `S/LotsScreen.kt` (**un** `TextButton("Voir la Boutique")` à côté de « Envoyer à la TV », visible si drapeau), `android/sender/src/main/AndroidManifest.xml` (une activité). **Hors zone** : `S/LotsRuntime.kt`, `S/store/StoreRuntime.kt` (w17-06), `S/RentalPickerScreen.kt` (w16-11), `S/shop/**` (W5), `C/**`, `R/**`.

## Étapes
1. `StoreScreen(runtime)` : en-tête (puce TV existante en une ligne : `TvLinkManager.state`, `tvBudgetText()`, `header` de `StoreView`), 4 puces (Apprendre · Langues · Quiz · Ma TV), bannière (`banner` : « Test gratuit jusqu'au 01/11 … » / « Catalogue ancien »), sections par `ShelfView`, cartes `StoreItemCard(card)` : titre, `lines` **telles quelles**, boutons = `card.rentButton` (→ étape 2) et `card.sendButton` (→ `LotsRuntime.deliverLot` / `syncNow(only)` / `downloadLanguage`) ; état `BLOQUE` ⇒ bouton grisé + `blocked.message`.
2. Location : `rentButton` ⇒ sélecteur W16 (`RentalPickerScreen` si présent ; sinon un `PickerModel` **local minimal** lisant `PilotRules` ou la grammaire, trois groupes et libellés **exacts** W16 § 1.3, date de fin réelle) ⇒ confirmation « Louer CM2 · 12 heures d'utilisation · à utiliser avant le 15/11 · gratuit pendant le test » ⇒ `RentRequest(origin=phone)` ⇒ `runtime.submit(request)` (w17-06) ; retour : phrase de `StoreTexts`.
3. `StoreRequestsScreen` : liste des demandes de la TV (`PENDING`), chacune : « La TV demande : CM2 · 12 heures d'utilisation (03/10 20:41) », boutons **Confirmer** (adulte : si un code parental est configuré sur le téléphone, le demander par l'écran existant) / **Refuser** ; vide : « Aucune demande de la TV. »
4. `StoreMyTvSection` : `TvRentalView.lines()` (ou `unit`/reste W16), « Livraison avancée » → `RentalDeliveryActivity`, « Actualiser le catalogue » → `runtime.refreshBundles()`.
5. `MainActivity` : action unique ; `LotsScreen` : un bouton ; manifeste.
6. Accessibilité : `contentDescription` partout, cibles ≥ 48 dp, aucun texte < `bodySmall` ; hors ligne : tout lisible depuis les caches.
7. Lint de pureté vert (aucune chaîne d'état décidée ici : `grep` ci-dessous) ; `compileDebugKotlin` ; prévisualisation Compose avec un `Store` de fixtures (w17-01) ; fumée W14 `--tv fake` **PASS**.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin
cd android && gradle --offline :core:test --tests 'castbridge.core.lint.*'      # pureté
grep -rn 'Pas sur la TV\|Sur la TV\|Loué\|Demandez à un parent' android/sender/src/main/kotlin/castbridge/sender/store   # 0 hit (tout vient de StoreTexts/StoreView)
grep -rn 'XAF\|momo\|mobile money' android/sender/src/main/kotlin/castbridge/sender/store   # 0 hit
grep -c 'Locations' android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt   # ≥ 1 (chemin drapeau éteint conservé)
```
Observable (émulateur, `FakeTvMain` + `StoreApi`) : rayons remplis depuis les fixtures ; « Envoyer à la TV » livre un lot (même chemin qu'aujourd'hui) ; « Louer gratuitement » produit une demande ; une demande née sur la fausse TV apparaît et se confirme ; drapeau éteint ⇒ l'app est **identique** à aujourd'hui.

## Cas limites
Aucune TV enregistrée ⇒ cartes avec « Ajoutez d'abord votre TV » ; catalogue des bouquets absent ⇒ rayons par lot (mode dégradé) + phrase ; profil enfant actif sur la TV ⇒ boutons de location absents (fait `kidActive` de `GET /api/store`, pas une déduction locale) ; TV d'essai ⇒ phrase d'essai + « Activer la TV ».

## À ne pas faire
Ne pas réécrire `LotsScreen` ; ne pas copier `LanguagesSection` (l'appeler ou réutiliser ses fonctions de `LotsRuntime`) ; aucun montant ; aucun écran de paiement (W5) ; aucun appel HTTP direct ; aucune décision d'état ; ne pas toucher `S/LotsRuntime.kt`.

## Rapport
`STATUT`, captures textuelles des trois rayons et d'une demande, rapport F, interface que W5 (w5-11) devra étendre (`StoreRuntime` → `ShopRuntimeView`), question : D-W17-1 (remplacer « Locations ») confirmée ?
