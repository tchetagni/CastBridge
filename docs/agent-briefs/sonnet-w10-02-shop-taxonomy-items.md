# w10-02 — Cœur : taxonomie de la boutique à trois familles, articles œuvres/chaînes/pack Langues, réglages `works.*` de la grille

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT (w5-01 souhaité)
> **Groupe : W10a-2** (vague W10a) · prérequis : w10-01 ; w5-01 souhaité · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.shop.*' --tests 'castbridge.core.sales.*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 10a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (w5-01 souhaité).** Conception : DESIGN-W10 § 2.2, § 2.3, § 5.1, § 13 ; DESIGN-W5 § 3.1 (articles, grille). Branche `claude/sonnet-w10-02`. Rapport : `docs/agent-reports/sonnet-w10-02.md`. Dépend de w10-01 (`WorksCatalog`, `WorkEntry`).

## Objectif
Un modèle **pur** de la vitrine à trois familles (Apprendre, Langues, Œuvres locales) : catégories, filtres (genre, langue, longueur, producteur, classe), tri, et les **articles** `loc-oeuvre-<id>|<j>`, `loc-oeuvre-<id>-7j|7`, `loc-chaine-<producteur>|<j>`, `achat-pack-langues-<code>` ; la grille signée accepte `set=works.shareProducer|<n>`, `set=works.shareAgent|<n>`, `set=works.minPayoutXaf|<n>` ; si w5-01 est fusionné, `ShopItem` et `ShopCatalog` sont **étendus** (additif), sinon un analyseur local est fourni et la fusion est notée pour w10-11.

## Pourquoi (preuves)
- DESIGN-W5 § 3.1 : articles `loc-<bouquet>|<rentalDays>` ; un article absent de la grille n'est ni vendable ni affiché avec un prix ; § 3.2 : `BundleCatalog` inchangé.
- `C/lots/EditionPolicy.kt:8-10` : `Bundle(id, type, lots, title, rawBytes, rentalDays)` : les bouquets `oeuvre-*`, `chaine-*`, `pack-langues-*` viennent du catalogue des bouquets (w10-04).
- `C/sales/PriceGrid.kt` (w4-11 ; **vérifier sa présence** : sinon coder l'analyseur de lignes `set=` dans `C/shop/WorkItems.kt` et le signaler).
- `C/lots/RentalPolicy.kt:23-27` : un lot libre n'est jamais louable ⇒ `achat-pack-langues-*` est un **achat**, jamais `loc-`.

## Fichiers possédés
Nouveaux `C/shop/ShopTaxonomy.kt`, `C/shop/WorkItems.kt`, `CT/shop/{ShopTaxonomyTest,WorkItemsTest}.kt` ; `C/sales/PriceGrid.kt` (additif : lignes `set=works.*`), `CT/sales/PriceGridTest.kt` ; **si w5-01 fusionné** : `C/shop/ShopCatalog.kt` (additif). **Hors zone** : `C/works/**` (utiliser), `C/lots/**`, `R/**`, `S/**`, `backend/`.

## Étapes
1. `WorkItems.kt` : `sealed class WorkItem { Work(id, days) ; Channel(producer, days) ; LanguesPack(code) }` ; `parse("loc-oeuvre-ndolo-kwata-01|30")`, `parse("loc-oeuvre-ndolo-kwata-01-7j|7")` (le suffixe `-7j` est **dans l'identifiant du bouquet**, pas dans l'id de l'œuvre : `workId()` le retire), `parse("loc-chaine-ndolo|30")`, `parse("achat-pack-langues-zh|0")` ; `bundleId()` ; `toItemString()` ; bornes (`days` 1..60 ; achat `0`). Si `C/shop/ShopItem` (w5-01) existe : ajouter `ShopItem.Work`, `ShopItem.Channel`, `ShopItem.Purchase` et faire déléguer `ShopItem.parse` ; sinon `WorkItem` autonome.
2. `PriceGrid` : lignes `set=works.shareProducer|<0..100>`, `set=works.shareAgent|<0..100>` (somme ≤ 100 ; le reste = plateforme), `set=works.minPayoutXaf|<n>` ; défauts 60, 15, 5000 ; `canonicalPayload` **inchangé** pour une grille sans ces lignes (test : vecteur de w4-11 rejoué à l'identique) ; `works(): WorksSettings`.
3. `ShopTaxonomy.kt` : `enum Family { APPRENDRE, LANGUES, OEUVRES }` (libellés « Leçons », « Langues », « Œuvres locales ») ; `data class Category(family, id, label, parent?)` : Apprendre par **cycle** (maternelle, primaire, collège, lycée, supérieur : dérivé de `LearnCatalog.cursusOfLevel` si disponible, sinon table locale) puis classe ; Langues par langue ; Œuvres par `WorkKind` ; `data class Listing(family, bundleId, item: String, title, subtitle, priceXaf: Int?, days: Int, bytes, rating: Rating?, producer: String?, langs, durationS: Int?, orderable: Boolean, reason: String?)` ; `fun build(bundles: BundleCatalog, grid: PriceGrid?, works: WorksCatalog?, learnScopes: ...): List<Listing>` ; filtres purs `filter(listings, Filter(family, category, lang, length: SHORT|MEDIUM|LONG, producer, band: AgeBand?))` (une œuvre > tranche **disparaît** sous profil enfant) ; tris `NEW` (generatedAt / ordre du catalogue), `AZ`, `PRICE` ; `orderable` faux si prix absent, `days > 60`, lot libre, ou famille inconnue ; `reason` français.
4. Longueur : `SHORT < 300 s ≤ MEDIUM < 900 s ≤ LONG`.
5. Tests : table des 3 familles × (prix présent/absent) × (enfant/adulte) ; `-7j` ; chaîne ; pack Langues jamais `loc-`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.shop.*' --tests 'castbridge.core.sales.*'   # vert
grep -rn 'XAF [0-9]' android/core/src/main/kotlin/castbridge/core/shop   # 0 (aucun montant)
```

## Cas limites
- Bouquet `oeuvre-x` présent dans le catalogue des bouquets mais absent du catalogue des œuvres : listé « métadonnées indisponibles », non commandable.
- Deux bouquets `oeuvre-x` et `oeuvre-x-7j` : même fiche, deux prix.
- Grille signée avant W10 : `works()` renvoie les défauts.

## À ne pas faire
Pas de commit sur les branches partagées ; pas d'`import android` ; ne pas modifier `SignedBundleCatalog` ni `canonicalPayload` d'une grille existante ; pas de HTTP ; aucun montant ; textes français.

## Rapport
`STATUT`, signatures publiques, si w5-01 était présent (et ce qui a été étendu), questions.
