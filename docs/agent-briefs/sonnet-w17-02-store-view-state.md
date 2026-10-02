# w17-02 — Cœur : `StoreView` (état pur de chaque article pour le téléphone et la TV), `StoreTexts` (toutes les phrases françaises), `StoreFlag`, « Boutique » en mode enfant, id `store` de la télémétrie

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT (pendant le gel : cœur seul)
> **Groupe : W17a-2** (vague W17a, cœur) · prérequis : w17-01 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.store.*' --tests 'castbridge.core.parental.*' --tests 'castbridge.core.TelemetryTest' --tests 'castbridge.core.policy.*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 17a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W17 § 2.3, § 2.5, § 3.4, § 6 (W12), § 8.1 D-W17-6/7/10/12. Branche `claude/sonnet-w17-02`. Rapport : `docs/agent-reports/sonnet-w17-02.md`. Règle de pureté W14 : **tout** ce qu'un écran affichera vient d'ici.

## Objectif
`StoreView.decide(facts)` rend, pour chaque `StoreItem` (w17-01), **un** état (`GRATUIT`, `ECHANTILLON`, `PAS_SUR_TV`, `ENVOYE`, `SUR_TV`, `LOUE`, `TERMINE`, `A_LOUER`, `BLOQUE(raison)`), ses lignes de carte (titre, contenu, taille, ligne TV, ligne location) et ses boutons (location : `Louer gratuitement` / `Prolonger` / `Relouer` / aucun ; livraison : exactement `LotsToDeliver.sendButton`). Mêmes valeurs sur les deux appareils (la TV n'a pas `ENVOYE`). `StoreTexts` porte toutes les phrases (§ 2.5). `StoreFlag` dit si la Boutique est allumée. « Boutique » entre dans `KID_HOME` ; `store` entre dans `TV_FEATURES`.

## Pourquoi (preuves)
- `C/lots/LotsToDeliver.kt:45-51` (`sendButton`), `C/lots/LotStatus.kt:4-6` (`LotStage`) ; `C/lots/TvLotStore.kt:16-29` (`TvManifest` : lots installés) ; `C/lots/RentalEngine.kt:37-52` (`RentalStatus` : `usable`, `remainingMs`, `remainingUsageMinutes`, `warning`, `message`) ; `C/lots/RentalDelivery.kt:62-70` (`TvRentalView.Rental` : vue téléphone) ; `C/lots/Entitlement.kt` (`Access.granted`) ; `C/lots/RentalPolicy.kt:23-28`.
- `C/parental/ParentalModel.kt:227-230` (`KID_HOME` par étiquette) ; `C/telemetry/Telemetry.kt:16, 179` (`TV_FEATURES` liste close, filtre `feature_used`) ; `C/policy/PolicyActions.kt:30` (`FLAGS` liste close : `flag.set`).
- W16 : `RentalUnit` et phrases par unité (w16-01) **si fusionné** : sinon `RentalStatus.message` existant.

## Fichiers possédés
Nouveaux `C/store/StoreView.kt`, `C/store/StoreTexts.kt`, `C/store/StoreFlag.kt`, `CT/store/StoreViewTest.kt`, `CT/store/StoreTextsTest.kt`, `CT/store/StoreFlagTest.kt` ; modifiés (**une ligne chacun**) `C/parental/ParentalModel.kt` (`KID_HOME` + « Boutique »), `C/telemetry/Telemetry.kt` (`TV_FEATURES` + `"store"`, `PHONE_FEATURES` + `"store"`), `C/policy/PolicyActions.kt` (`FLAGS` + `"store.enabled"`) ; `CT/parental/*` et `CT/TelemetryTest.kt` (une assertion chacun si une liste est figée par un test). **Hors zone** : `C/store/StoreCatalog.kt`, `C/store/RentRequest*.kt`, `C/store/StoreApi.kt`, `C/lots/**`, `S/**`, `R/**`.

## Étapes
1. **Rouge** : `StoreViewTest`, table ≥ 24 lignes : pour chaque état du § 2.3, les faits minimaux ⇒ état + libellés exacts + boutons ; cas bloqués § 2.5 (TV hors de portée, aucune TV, essai, enfant, quota 3, même bouquet autre unité, libre, famille inconnue, pilote fini sans prix, place, catalogue absent) ; « loué » avec `remainingUsageMinutes` ⇒ « Il vous reste 5 h 20 d'utilisation » et avec `remainingMs` seul ⇒ phrase existante `RentalStatus.message` ; `TERMINE` seulement si fin < 30 j ; priorité des états (loué > sur TV > échantillon > pas sur TV) ; la TV ne produit jamais `ENVOYE`.
2. `StoreView` : `data class Facts(store: Store, tvManifest: TvManifest?, rentals: List<RentalStatus> /* TV */ , tvRentals: TvRentalView? /* téléphone */, granted: Set<String> /* bouquets achetés/abonnés */, trialTv: Boolean, kidActive: Boolean, tvKnown: Boolean, tvReachable: Boolean, phoneStages: Map<LotId, LotStage>, pendingRequests: Set<String> /* bouquets demandés */, pilotEndMs: Long?, nowMs: Long, maxConcurrent: Int = 3)` ; `data class Card(item, state, lines: List<String>, rentButton: RentButton?, sendButton: LotsToDeliver.SendButton?, blocked: Blocked?)` ; `fun decide(f: Facts): Screen(shelves: List<ShelfView(shelf, section, cards)>, header: String /* « Catalogue du JJ/MM » */, banner: String? /* test gratuit, catalogue ancien > 30 j */)` ; `fun json(screen): String` + `parse` (pour `GET /api/store`, w17-04 : **JsonLite**, champs `state`, `lines`, `buttons`).
3. `StoreTexts` : constantes nommées pour **chaque** phrase du § 2.5 et des cartes ; test : aucune phrase ne contient « XAF », un chiffre de prix, « sender », « receiver » ; toutes commencent par une majuscule et finissent par `.`, `?` ou `…` sauf les étiquettes de bouton ; « Louer gratuitement » **exactement** ; « Demandez à un parent » **exactement** (W5 § 6.6).
4. `StoreFlag.enabled(settingsValue: Boolean?, flagOrder: Boolean?, compiledDefault: Boolean): Boolean` : priorité W12 > ordre signé > défaut ; test de table 8 lignes ; KDoc : « défaut compilé faux en release, vrai en debug : décision D-W17-10 ».
5. Une ligne : `KID_HOME` + « Boutique » (test : `kidHome(labels + "Boutique")` la garde, et un profil `GAMES` bloqué ne la retire pas) ; `TV_FEATURES`/`PHONE_FEATURES` + `store` (test : `feature_used{feature: store}` accepté) ; `FLAGS` + `store.enabled` (test : `flag.set name=store.enabled value=1` accepté, `store.foo` refusé).
6. **Vert** : porte ; `:core:test` complet.

## Critères d'acceptation
Porte verte ; ≥ 30 tests rouges puis verts ; `grep -rn "currentTimeMillis\|File(" android/core/src/main/kotlin/castbridge/core/store/StoreView.kt` vide ; diff hors `C/store/` ≤ 3 lignes de code ; `grep -n "Louer gratuitement\|Demandez à un parent" android/core/src/main/kotlin/castbridge/core/store/StoreTexts.kt` ≥ 2.

## Cas limites
Contrat `SUSPENDED` (horloge douteuse) ⇒ état `LOUE` avec la phrase existante « Vérifiez l'heure de la TV » ; `superUnlimited` ⇒ tout `SUR_TV` « droit illimité » ; article en attente de demande ⇒ ligne « Demande en cours (03/10) » et bouton de location **absent** ; article `GRATUIT` jamais bloqué par l'essai (un lot libre se télécharge en essai : `TrialPolicy` l'autorise via `/api/lots`).

## À ne pas faire
Pas d'I/O ; pas de texte dans un écran ; pas de conversion heures ↔ jours (W16 § 1.3) ; pas de prix ; ne pas élargir `FLAGS` au-delà d'une entrée.

## Rapport
`STATUT`, sorties rouge/vert, la table des états (copie), phrases à relire par le propriétaire, question : D-W17-6 (essai visible) et D-W17-7 (enfant visible) confirmées ?
