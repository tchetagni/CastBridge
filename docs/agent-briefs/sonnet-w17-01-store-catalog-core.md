# w17-01 — Cœur : `StoreCatalog` (fusion du catalogue de lots signé et du catalogue des bouquets signé en articles de Boutique, rayons, alias, bornes de taille)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT (pendant le gel : cœur seul)
> **Groupe : W17a-1** (vague W17a, cœur) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.store.*' --tests 'castbridge.core.lots.*Catalog*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 17a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W17-STORE-TELEPHONE-ET-TV-2026-10-03.md` § 1, § 2.2, § 3.2, § 8.3. Branche `claude/sonnet-w17-01`. Rapport : `docs/agent-reports/sonnet-w17-01.md`. Dire « CastBridge » (téléphone) / « CastBridge-TV ». Règle W15 R5 : aucun fichier `S/`, `R/`.

## Objectif
Une fonction pure qui, à partir des **deux catalogues signés existants** (`LotManifest` = `castbridge-lot-catalog-v1` ; `SignedBundleCatalog.Verified` = `castbridge-bundle-catalog-v1`), produit la liste des **articles** de la Boutique : un article par bouquet (titre, rayon, lots et leurs titres/tailles/éditions, famille libre/réservé, taille totale, alias court), plus un article par lot **orphelin** (lot d'aucun bouquet : rayon déduit de la fonction). Mode dégradé sans catalogue des bouquets. Bornes de taille des documents (256 Ko / 64 Ko). Aucun prix, aucun état (l'état est w17-02).

## Pourquoi (preuves)
- `C/lots/LotManifest.kt` (catalogue signé, `LotMeta` : `C/lots/LotApi.kt:13` titre, octets, `edition`) ; `C/lots/EditionPolicy.kt:8-30` (`Bundle(id, type, lots, title, rawBytes, rentalDays)`, `BundleCatalog.containing`, `lotsOf`) ; `C/lots/SignedBundleCatalog.kt:19-69` (`Verified.catalog`, `generatedAt`, `dateFr`).
- `C/lots/RentalPolicy.kt:4-19` (`LotFamily`, `LotFamilies.explicit`) ; `C/lots/LotEditions.kt` (`fullOf`, `trialOf`).
- `S/LotsRuntime.kt:126` (`classScopes` : fonctions `learn`/`quiz`), `:100-107` (lots `langues`) : **lecture seule**, pour reproduire les mêmes regroupements.
- **BLOQUÉ** : `content/TRIAL-MANIFEST.json` absent ; `Bundle.type` réel inconnu ⇒ rayons par `type` connu (`classe`, `langues`, `quiz`), sinon par fonction des lots, sinon « Autres ».

## Fichiers possédés
Nouveaux `C/store/StoreCatalog.kt`, `C/store/StoreAlias.kt`, `CT/store/StoreCatalogTest.kt`, `CT/store/StoreAliasTest.kt`, `CT/store/fixtures/store-lots-catalog.json`, `CT/store/fixtures/store-bundles-catalog.json` (catalogues **non signés** `UNSIGNED` pour les tests de fusion ; la vérification de signature reste testée ailleurs). **Hors zone** : `C/lots/**`, `C/store/StoreView.kt` (w17-02), `C/store/RentRequest*.kt` (w17-03), `C/store/StoreApi.kt` (w17-04).

## Étapes
1. **Rouge** : `StoreCatalogTest` : (a) deux fixtures ⇒ 3 articles de classe (CM2 : `learn:cm2` + `quiz:cm2`, 4,2 Mo = somme des octets quand `rawBytes = 0`, sinon `rawBytes`), rayon `APPRENDRE`, sous-rayon « Primaire » (déduit : `cp, ce1, ce2, cm1, cm2` ; « Secondaire » : `6e…tle*` ; « Supérieur » : `droit-l1`, `gce-*` ; sinon « Autres ») ; (b) lots `langues:*` sans bouquet ⇒ un article par lot, rayon `LANGUES`, famille `FREE` quand `families.of` le dit, « inconnue » sinon ; (c) lot `-trial` rattaché à l'article de son jumeau complet, jamais un article seul ; (d) sans catalogue des bouquets ⇒ un article par lot complet, rayon par fonction, `degraded = true` ; (e) document de lots > 256 Ko ou de bouquets > 64 Ko ⇒ `StoreCatalog.Refused` avec phrase FR ; (f) ordre stable (rayon, sous-rayon, ordre de `classScopes`, puis identifiant) ; (g) `generatedAt` le plus ancien des deux = `catalogAt` affiché « Catalogue du JJ/MM ».
2. `StoreCatalog` : `data class StoreItem(id: String /* bouquet ou "lot:<key>" */, title, shelf: Shelf, section: String, lots: List<LotMeta>, trialLots: List<LotMeta>, family: LotFamily?, bytes: Long, bundle: Bundle?, alias: String)` ; `enum Shelf { APPRENDRE, LANGUES, QUIZ, OEUVRES, AUTRES }` ; `fun build(lots: List<LotMeta>, bundles: BundleCatalog?, families: LotFamilies): Store` avec `Store(items, catalogAtLots, catalogAtBundles, degraded)` ; `fun checkSize(json: String, maxBytes: Long): String?` (phrase FR ou null) ; constantes `MAX_LOTS_CATALOG_BYTES = 256 shl 10`, `MAX_BUNDLES_CATALOG_BYTES = 64 shl 10`.
3. `StoreAlias.of(title, id, taken: Set<String>)` : ≤ 6 caractères `[A-Z0-9]`, sans accent (« Classe CM2 » → `CM2`, « Terminale C » → `TLEC`, « Droit L1 » → `DRL1`), suffixe `2`, `3` en cas de collision dans l'ordre du catalogue ; **déterministe** (même entrée ⇒ même alias) ; test de table 12 lignes + collision.
4. Doc KDoc en français sur chaque classe ; aucune I/O, aucune date système (`catalogAt` = chaînes des catalogues).
5. **Vert** : porte ; `:core:test` complet.

## Critères d'acceptation
Porte verte ; ≥ 10 tests rouges puis verts ; `grep -rn "System.currentTimeMillis\|java.io.File" android/core/src/main/kotlin/castbridge/core/store/StoreCatalog.kt android/core/src/main/kotlin/castbridge/core/store/StoreAlias.kt` vide ; aucune modification hors zone (`git status --porcelain`).

## Cas limites
Bouquet citant un lot absent du catalogue de lots (ignoré, compté dans `Store.warnings`) ; deux bouquets contenant le même lot (le lot appartient aux deux articles, pas de doublon d'article) ; lot `quiz:cm2` seul sans bouquet et sans `learn:cm2` ⇒ rayon `QUIZ` ; catalogue vide ⇒ `Store(items = [])`, jamais une exception.

## À ne pas faire
Pas de prix ; pas d'état (sur la TV, loué…) ; pas de lecture de fichiers ; ne pas modifier `C/lots/**` ; pas de texte d'écran autre que les phrases de refus de taille.

## Rapport
`STATUT`, sorties rouge/vert, les rayons déduits des fixtures, question : valeurs réelles de `Bundle.type` (BLOQUÉ) et liste des sous-rayons à confirmer par le propriétaire.
