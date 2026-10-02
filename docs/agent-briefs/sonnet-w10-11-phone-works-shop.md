# w10-11 — CastBridge (téléphone) : Langues et Œuvres locales dans la Boutique, page chaîne, fiche, aperçus (second stock), stock scellé et livraison à la TV

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT (w5-11/w5-12 souhaités ; sans eux : écran autonome « Œuvres locales » et livraison par le propriétaire)
> **Groupe : W10d-1** (vague W10d) · prérequis : w10-01, w10-02 ; w5-11/12 souhaités ; après w8-15 s'il est lancé · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.lots.LotStoreTest' && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non
> **Amendement W17 (architecte, 2026-10-03)** : lire `docs/coordination/DESIGN-W17-STORE-TELEPHONE-ET-TV-2026-10-03.md` § 2, § 3.5, § 6. L'onglet « Œuvres locales » vit **dans `S/store/StoreScreen.kt`** (w17-07 : rayon `OEUVRES` de `StoreView`), **pas** dans `S/shop/ShopScreen.kt` ni par une « entrée sans W5 » dans `S/MainActivity.kt` (hors zone : l'entrée « Boutique » existe par w17-07) ; `WorksRuntime` relaie le catalogue des œuvres par **`POST /api/store/catalog`** (document `works`, w17-04) via `S/store/StoreRuntime.kt` (w17-06 : une ligne, proposée au rapport) ; « Louer 30 j / 7 j » devient le sélecteur W16 → `RentRequest` (même chemin que les classes). `S/LotsRuntime.kt` reste hors zone (w15-16).

**Vague 10d · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (w5-11/w5-12 souhaités ; sans eux : écran autonome « Œuvres locales » et livraison par le propriétaire).** Conception : DESIGN-W10 § 5.1, § 5.3, § 5.4, § 13. Branche `claude/sonnet-w10-11`. Rapport : `docs/agent-reports/sonnet-w10-11.md`. Dépend de w10-01, w10-02.

## Objectif
Le téléphone montre les trois familles (onglets **Leçons / Langues / Œuvres locales**), la page « chaîne » d'un producteur, la fiche d'une œuvre (aperçu lisible sur le téléphone, « Lire l'aperçu sur la TV », « Louer 30 j / 7 j », taille contre le budget `WorkStore` de la TV), télécharge les **aperçus** (`LotSync`, `feature=oeuvre`, édition essai) dans un **second stock** (300 Mo, distinct des 100 Mo), les livre à la TV (`DeliveryQueue`, priorité basse), et, avec W5, télécharge le lot **scellé** de l'œuvre louée dans `files/works/sealed/` puis le livre par `RentalDelivery` (activation puis `/api/rental/install`).

## Pourquoi (preuves)
- `docs/LOTS.md` § 5 (`LotStore` quota 100 Mo, `LotSync` Wi-Fi seulement, `DeliveryQueue`, transports) ; `C/lots/LotApi.kt:32` (`PHONE_MAX_BYTES` constante) ⇒ second `LotStore` avec son quota.
- `C/lots/RentalDelivery.kt:77-139` (`deliver(contract, catalogJson, lots, …)`) ; DESIGN-W5 § 4.5 (séquence), § 7 (onglet Boutique), w5-12 (`ShopRuntime`, `TvShopCache`, `ShopDelivery`).
- `S/LotsRuntime.kt:154` (catalogue de lots), `S/MainActivity.kt:106-131` (onglets).

## Fichiers possédés
Nouveaux `S/works/{WorksScreen,WorkDetailScreen,ProducerScreen,WorksRuntime,WorkStock,WorksTexts}.kt` ; `S/shop/ShopScreen.kt` (**si w5-11** : onglets), `S/shop/ShopRuntime.kt` (**si w5-12** : stock scellé des œuvres, `WorkStoreIndex` de la TV), `S/LotsRuntime.kt` (second `LotStore`, `feature=oeuvre` dans la synchronisation), `S/MainActivity.kt` (entrée « Œuvres locales » **sans** W5 seulement), `C/lots/LotStore.kt` (additif : quota paramétrable, défaut inchangé), `CT/lots/LotStoreTest.kt`. **Hors zone** : `C/works/**`, `C/shop/ShopTaxonomy.kt` (utiliser), `R/**`, `backend/`.

## Étapes
1. `LotStore(root, quota = LotBudget.PHONE_MAX_BYTES)` (additif) ; `WorkStock` = `LotStore(files/works, WorkBudget.PHONE_MAX_BYTES)` ; éviction : jamais un aperçu d'une œuvre **louée en cours** ni un lot scellé non livré.
2. `WorksRuntime` : lit le catalogue des œuvres (`GET /api/v1/catalog/works`, `SignedWorksCatalog.verify`, anti-retour, cache `files/works/catalog.json`), le relaie à la TV (`POST /api/shop/catalog` si W5, sinon `POST /api/oeuvres/catalog`) ; `LotSync` du stock des œuvres : **aperçus seulement** (`edition == TRIAL`), Wi-Fi seulement, après les lots d'Apprendre ; `DeliveryQueue` par TV, priorité basse, cible `WorkStore` (mêmes routes `/api/lots/*`, noms `castbridge-lot-oeuvre-*`).
3. Écrans (Compose, thème `Cb`) : `WorksScreen` (cartes, filtres genre/langue/longueur/producteur, tri, `ShopTaxonomy.filter`), `WorkDetailScreen` (couverture, synopsis, crédits, classification, durée, taille et « il manque X Mo sur la TV » d'après `GET /api/oeuvres`, aperçu : lecture locale via le lecteur du téléphone (`docs/PHONE-PLAYER.md`) depuis le zip du stock, ou « Lire l'aperçu sur la TV » (`POST /api/oeuvres/play`), « Louer 30 j (X XAF) » / « 7 j (Y) » ⇒ écran « Payer » de W5 avec l'article `loc-oeuvre-…` ; sans W5 : « Pour louer : votre point focal (code TV …) »), `ProducerScreen` (chaîne : bio, œuvres, « Louer toute la chaîne »). Langues : onglet avec « Gratuit (CC BY-SA 4.0) : Télécharger » (lien vers `FreeContentScreen` existant) et « Pack de confort » (article `achat-pack-langues-<code>`, textes de w10-12).
4. Avec W5 (`ShopRuntime`) : récupération du lot scellé (`Fulfilment.Rental.lots` avec `feature=oeuvre`) dans `files/works/sealed/<contrat>/` (Wi-Fi, reprise `Range`, quota du stock), puis `ShopDelivery` : `POST /api/activation/install` puis `RentalDelivery.deliver` (inchangé : les routes TV routent par fonction, w10-08) ; état « Payée · À livrer · Livrée » ; accusé.
5. Profil enfant actif sur le téléphone (W5 § 6.6 / W6) : onglet en lecture, œuvres > tranche masquées, pas de « Louer ».
6. Hors ligne : tout lisible depuis les caches ; aperçus déjà téléchargés lisibles.
7. Tests cœur : `LotStoreTest` quota paramétrable ; logique pure de « il manque X Mo » ; sélection des aperçus (`edition == TRIAL` et `feature == oeuvre`).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.LotStoreTest'   # vert
cd android && gradle --offline :sender:compileDebugKotlin                              # compile
# émulateur : catalogue d'exemple (w10-04) chargé ; aperçu téléchargé (Wi-Fi) ; livré à une TV émulée ; avec FakeShopApi (w5-01) : commande loc-oeuvre → lot scellé → livraison
```

## Cas limites
- Catalogue des œuvres plus ancien que le gardé : refusé, l'ancien reste.
- Lot scellé de 25 Mo sur réseau facturé : jamais sans accord explicite (règle `LotSync` existante).
- TV sans `WorkHub` (ancienne version) : `GET /api/oeuvres` 404 ⇒ « Mettez CastBridge-TV à jour pour les œuvres locales ».

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas modifier le quota de 100 Mo des lots ; pas de téléchargement sur réseau facturé ; ne pas éditer `C/works/**` ni `R/**` ; aucun montant codé ; textes français.

## Rapport
`STATUT`, dépendances rencontrées (w5-11/12 présents ?), captures, questions.
