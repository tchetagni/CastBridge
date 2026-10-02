# Vague 10 pour agents Sonnet/Haiku — index (2026-10-02) : boutique à trois familles (Apprendre, Langues, Œuvres locales) et marché des producteurs locaux

Source : `docs/coordination/DESIGN-W10-BOUTIQUE-PRODUCTEURS-LOCAUX-2026-10-02.md` (lire en entier avant tout cahier). Protocole et règles communes : `docs/COORDINATION.md` et l'en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »). **Ce fichier n'édite pas les index des autres vagues** ; le § « Changements aux cahiers W4/W5/W6 » liste ce que la vague 10 amende sans les éditer. Dépendances fonctionnelles : W4 (4a boîte v2, 4b mode réduit), W5 (boutique, locations en ligne), W6 (preuve de TV). **Vagues conçues en parallèle le 2026-10-02** (W7 synchronisation, W8 transport, W9 lots génératifs, W11 navigation : non fusionnées) : aucune dépendance fonctionnelle, mais des **fichiers partagés** ⇒ § « Coexistence avec W7/W8/W9/W11 » ci-dessous (DESIGN § 13 bis).

**Demande du propriétaire (2026-10-02)** : « expérimente la boutique qui inclura les producteurs de contenus locaux (vidéos, audios de sketch par exemple, …) » ; précision : la boutique vend **d'abord** les packs Apprendre (réservés, location) et Langues (libres CC BY-SA, achat de confort / mises à jour) ; les producteurs locaux sont une **troisième famille** ; même flux que W5 ; le pilote teste les trois familles ensemble.

**Modèle d'exécution recommandé** : `haiku` = mécanique, gabarits, scripts simples, campagne ; `sonnet` = tout le reste. Efforts en agent·jours (S ≈ 0,5-1, M ≈ 2, L ≈ 3-4).

**Prérequis** : **aucun pour le pilote technique minimal** (w10-01, 04, 05-lots, 08, 09, 10, 13 : fonctionnent avec la boîte v1 et l'outil de bureau, comme `tools/rental-test`) ; **souhaités** : 4a (w4-01…06 : `LotCrypt`, `LotOpener`, boîte v2), 4b (w4-07 `DegradedPolicy`), w5-01 (`ShopItem`, `ShopCatalog`), w5-06/07 (locations en ligne, commandes : la vente en ligne des œuvres en dépend), w5-11/12/15/16 (boutiques), w5-18 (parental achats), w6-09 (`X-CB-TV-Proof`), w1-06 (`routes.txt`), w3-09 (relais d'ordres).

## Cinq sous-vagues séquentielles ; fichiers disjoints à l'intérieur d'une sous-vague

| Sous-vague | Objet | Cahiers | Effort |
|---|---|---|---|
| **10a** | Cœur et outils : modèle d'œuvre + catalogue signé + budgets, taxonomie à trois familles + grille, Studio producteur, registre de contenu et catalogues | w10-01 … w10-04 | ≈ 7 j |
| **10b** | Serveur : fonctions/validateur/relais, producteurs (migration, console, relevés, retrait), télémétrie des lectures | w10-05 … w10-07 | ≈ 7,5 j |
| **10c** | TV : second magasin + consommateur + lecture loopback + routes, écran Œuvres, contrôle parental | w10-08 … w10-10 | ≈ 7 j |
| **10d** | Téléphone (œuvres dans la boutique, stock, aperçus), famille Langues, outil de livraison du pilote, kit du pilote papier | w10-11 … w10-14 | ≈ 6 j |
| **10e** | Docs, brouillons juridiques, campagne de test et CI | w10-15 … w10-17 | ≈ 3,5 j |

## Les 17 cahiers

| id | Cahier | Objet | Effort | Modèle | Statut | Dépend de |
|---|---|---|---|---|---|---|
| w10-01 | `sonnet-w10-01-works-core.md` | `C/works/**` : `Work` (`work.json`), `SignedWorksCatalog`, `WorkBudget`, `WorkStoreIndex`, classification ↔ `parental.Rating` ; `works-vectors.json` | M | sonnet | PRÊT | — |
| w10-02 | `sonnet-w10-02-shop-taxonomy-items.md` | `C/shop/ShopTaxonomy.kt` (trois familles, catégories, filtres, tri), articles `loc-oeuvre`, `loc-chaine`, `achat-pack-langues`, lignes `set=works.*` de la grille | M | sonnet | PRÊT (w5-01 souhaité) | w10-01 |
| w10-03 | `sonnet-w10-03-producer-studio.md` | `tools/producer-studio/studio.py` : inspecter, transcoder (presets), apercu, empreinte, emballer, verifier, soumettre ; tests | L | sonnet | PRÊT | w10-01 (format `work.json`) |
| w10-04 | `sonnet-w10-04-content-registry-catalogs.md` | `content/oeuvres/**`, `trial_edition.py` (bouquets `oeuvre-*`/`chaine-*`, `tout` exclut les œuvres, `sign-works-catalog`), `build_lots.py` (ramasse les lots d'œuvres), `bundles-rental.json` | M | sonnet | PRÊT | w10-01 |
| w10-05 | `sonnet-w10-05-server-lots-works.md` | `LotService` (fonctions, plafond par fonction), `WorkLotValidator`, `WorksCatalogController`, lots complets `oeuvre` jamais servis en clair, migration `V<n>__producers.sql` + entités | L | sonnet | PRÊT | w10-01 (format), w10-04 (vecteurs de catalogue) |
| w10-06 | `sonnet-w10-06-server-producers-console.md` | `B/producers/**` : import de soumission, file de revue, décision, classification, prix proposé → fragment de grille, publication, `SaleListener`, relevés signés, versements (TOTP), retrait + ordre `work.takedown`, pages `/admin/producers/**` | L | sonnet | PRÊT (w5-07 souhaité pour le branchement de `SaleListener`) | w10-05 |
| w10-07 | `sonnet-w10-07-telemetry-work-play.md` | `work_play` (cœur + serveur), agrégation `producer_play_stat`, page stats, consentement | S | sonnet | PRÊT | w10-05 |
| w10-08 | `sonnet-w10-08-tv-workstore-hub.md` | `R/WorkHub.kt` (second `TvLotStore`, budget, volume), `R/WorkConsumer.kt`, routage par fonction (`RentalApi.storeFor`, `LotsHub`), `/api/oeuvres*`, `/stream/oeuvre/<id>` loopback mémoire, adoption USB, `TrialPolicy`/`DegradedPolicy`, `routes.txt` | L | sonnet | PRÊT | w10-01 ; w4-04 souhaité |
| w10-09 | `sonnet-w10-09-tv-works-screen.md` | `R/WorksActivity.kt` D-pad (catégories, cartes, fiche, lecture, compte à rebours, « Louer »), tuile, icône, textes, télémétrie `work_play` côté TV | L | sonnet | PRÊT | w10-08 (interface `WorkHub` ; en parallèle contre une implémentation mémoire) |
| w10-10 | `sonnet-w10-10-tv-parental-works.md` | classification des œuvres dans `ParentalHub` (filtre enfant, PIN -16), boutique TV masque > tranche, rapport parental (titre d'œuvre si `shareTitles`) | M | sonnet | PRÊT | w10-01, w10-08 (interface) |
| w10-11 | `sonnet-w10-11-phone-works-shop.md` | `S/works/**` : onglets Langues / Œuvres dans la Boutique (ou écran autonome sans W5), page chaîne, fiche, aperçus (second `LotStore` 300 Mo, `LotSync feature=oeuvre`), stock scellé, livraison via `RentalDelivery` | L | sonnet | PRÊT (w5-11/12 souhaités) | w10-01, w10-02 |
| w10-12 | `sonnet-w10-12-langues-family.md` | Famille Langues : lots libres toujours autorisés (vérifier `EditionPolicy`), achat `achat-pack-langues-<code>` → `shop_order.kind=PURCHASE` + `issueWithRights` ligne `purchase`, textes « contenu libre » + bouton archive, CGV | M | sonnet | PRÊT (w5-06 requis pour le serveur ; sinon cœur + textes seulement) | w10-02 |
| w10-13 | `sonnet-w10-13-pilot-delivery-tool.md` | `tools/producer-studio/livrer.py` : généralise `rental_test.py` (clé de bureau réelle, `emettre --location`, `lot-chiffrer`, upload + `/api/rental/install`, LAN ou relais téléphone), `--status`, `--sweep`, journal | M | sonnet | PRÊT | w10-04 (lots), w10-08 (TV) |
| w10-14 | `sonnet-w10-14-pilot-paper-kit.md` | `content/oeuvres/pilote/` : `ventes.csv` (gabarit), `releve.py` (parts, relevé texte), fiche point focal pilote, questionnaire foyer, guide d'entretien producteur, fiche incident, affiche-catalogue | S | haiku | PRÊT | — |
| w10-15 | `sonnet-w10-15-docs-w10.md` | `docs/OEUVRES.md`, `docs/PRODUCTEURS.md` ; mises à jour LOTS, RENTAL-LOTS, MEDIA-POLICY, PARENTAL, TRIAL-EDITION, API-SERVER, TELEMETRY, SHOP (si w5-20), HANDOFF | M | sonnet | PRÊT | 10a-10d |
| w10-16 | `sonnet-w10-16-legal-producers.md` | `docs/legal/{ACCORD-PRODUCTEUR,CHARTE-MODERATION,CGV-oeuvres-langues,REGISTRE-TRAITEMENTS-producteurs,CHECKLIST-JURISTE-producteurs}.md` (brouillons) | M | sonnet | **BLOQUÉ partiel** (juriste, D7, D-W10-3/5/9) | w3-13 / w5-21 si fusionnés (même dossier) |
| w10-17 | `sonnet-w10-17-test-campaign-ci-w10.md` | `docs/TEST-CAMPAIGN.md` § Œuvres (≈ 35 étapes), `tools.yml` (tests Studio, catalogue, vecteurs), `tools/shop-test` étendu si présent | S | haiku | PRÊT | 10a-10d |

**Pilote technique minimal** (vendre une œuvre comme une location ordinaire dans le build actuel, sans W5) : w10-01, w10-04, w10-05 (partie lots seulement), w10-08, w10-09, w10-10, w10-13 ≈ 12 j.

## Matrice de propriété (preuve de disjonction par sous-vague)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `B/` = `backend/src/main/java/castbridge/server/`, `BT/` = `backend/src/test/java/castbridge/server/`, `TPL/` = `backend/src/main/resources/templates/admin/`.

### 10a
| id | Fichiers possédés |
|---|---|
| w10-01 | nouveaux `C/works/{Work,WorksCatalog,WorkBudget,WorkStoreIndex,WorkVectors}.kt`, `CT/works/**`, `tools/activation/works-vectors.json` |
| w10-02 | nouveaux `C/shop/ShopTaxonomy.kt`, `C/shop/WorkItems.kt`, `CT/shop/{ShopTaxonomyTest,WorkItemsTest}.kt` ; `C/sales/PriceGrid.kt` (lignes `set=works.*`, additif), `CT/sales/PriceGridTest.kt` ; **si w5-01 fusionné** : `C/shop/ShopCatalog.kt` (additif) |
| w10-03 | nouveaux `tools/producer-studio/{studio.py,presets.json,README.md}`, `tools/tests/test_producer_studio.py` |
| w10-04 | nouveaux `content/oeuvres/{registry.json,lots.json,README.md}`, `content/oeuvres/exemple/**` (œuvre d'exemple synthétique, ≤ 300 Ko), `tools/trial-edition/trial_edition.py`, `tools/trial-edition/test_trial_edition.py`, `tools/content-lots/build_lots.py`, `tools/tests/test_content_tools.py`, `content/bundles-rental.json`, `tools/free-content/license-tags.json` (commentaire) |

### 10b
| id | Fichiers possédés |
|---|---|
| w10-05 | `B/lots/LotService.java`, `B/lots/LotController.java`, nouveaux `B/lots/{WorkLotValidator,WorksCatalogController}.java`, `B/config/CastbridgeProperties.java` (additif), migration `V<n>__producers.sql`, nouveaux `B/producers/{Producer,ProducerWork,ProducerWorkVersion,ProducerFingerprint,ProducerReview,ProducerTakedown,ProducerSale,ProducerStatement,ProducerPlayStat}.java` + dépôts, `BT/lots/**`, `BT/producers/model/**`, `backend/src/main/resources/application.yml` (bloc `castbridge.producers`, `castbridge.lots.max-bytes`) |
| w10-06 | nouveaux `B/producers/{ProducerService,WorkReviewService,StatementService,TakedownService,SaleListener,ProducerAdminController,ProducerApiController,ProducerCsv}.java`, `TPL/producers*.html`, `BT/producers/service/**` ; `B/orders/PolicyCatalog.java` (action `work.takedown`, additif) ; `TPL/lic-nav.html` (un lien) |
| w10-07 | `C/telemetry/Telemetry.kt`, `CT/telemetry/**`, `B/telemetry/EventCatalog.java`, nouveau `B/producers/PlayStatAggregator.java`, `BT/telemetry/**` |

### 10c
| id | Fichiers possédés |
|---|---|
| w10-08 | nouveaux `R/WorkHub.kt`, `R/WorkConsumer.kt`, `R/WorkStream.kt` ; `C/lots/RentalApi.kt`, `C/lots/TvLotStore.kt` (additif : `adoptFrom` paramétrable), `C/tv/ReceiverServer.kt` (zone `/stream/` seulement), `R/LotsHub.kt`, `R/RentalHub.kt` (branchement), `R/TvService.kt` (chaîne `ApiExtension`), `C/owner/TrialPolicy.kt`, `C/owner/DegradedPolicy.kt` (si présent), `tools/routes/routes.txt`, `CT/lots/{RentalApiTest,WorkStoreTest}.kt`, `CT/owner/{TrialRoutesTest,DegradedRoutesTest}.kt`, `C/policy/PolicyActions.kt` (action `work.takedown`) |
| w10-09 | nouveaux `R/WorksActivity.kt`, `R/WorksViews.kt`, `R/WorksTexts.kt`, `android/receiver/src/main/res/drawable/ic_t_works.xml` ; `R/PlayerActivity.kt` (`homeTools` : une tuile), `R/HomeScreen.kt` (si nécessaire), `android/receiver/src/main/AndroidManifest.xml` (une activité `exported=false`), `C/tv/HomeTools.kt` (si w3-11 fusionné) |
| w10-10 | `R/ParentalHub.kt`, `R/ParentalActivity.kt`, `C/parental/{ParentalEngine,ParentalReports}.kt` (additif), `CT/Parental*Test.kt` ; `R/shop/ShopActivity.kt` (si w5-15 fusionné : filtre de classification, additif) |

### 10d
| id | Fichiers possédés |
|---|---|
| w10-11 | nouveaux `S/works/{WorksScreen,WorkDetailScreen,ProducerScreen,WorksRuntime,WorkStock,WorksTexts}.kt` ; `S/shop/ShopScreen.kt` (si w5-11 : onglets), `S/shop/ShopRuntime.kt` (si w5-12 : stock scellé, `WorkStoreIndex`), `S/LotsRuntime.kt` (second `LotStore`), `S/MainActivity.kt` (entrée sans W5), `C/lots/LotStore.kt` (additif : quota paramétrable), `CT/lots/LotStoreTest.kt` |
| w10-12 | `C/lots/EditionPolicy.kt` (lots libres toujours autorisés, additif), `C/lots/RentalPolicy.kt` (commentaire), `CT/lots/EditionTest.kt`, nouveaux `C/shop/LanguesOffer.kt`, `CT/shop/LanguesOfferTest.kt` ; **si w5-06/07 fusionnés** : nouveaux `B/shop/order/PurchaseFulfilment.java`, `BT/shop/order/PurchaseFulfilmentTest.java` ; textes `S/langues/LanguesShopTexts.kt`, `R/LanguesHub.kt` (lien archive) |
| w10-13 | nouveaux `tools/producer-studio/livrer.py`, `tools/tests/test_livrer.py`, `tools/producer-studio/LIVRAISON.md` ; `tools/rental-test/README.md` (renvoi) |
| w10-14 | nouveaux `content/oeuvres/pilote/{ventes.csv,releve.py,FICHE-POINT-FOCAL-PILOTE.md,QUESTIONNAIRE-FOYER.md,ENTRETIEN-PRODUCTEUR.md,FICHE-INCIDENT.md,AFFICHE-CATALOGUE.md,README.md}`, `tools/tests/test_releve.py` |

### 10e
| id | Fichiers possédés |
|---|---|
| w10-15 | nouveaux `docs/OEUVRES.md`, `docs/PRODUCTEURS.md` ; `docs/{LOTS,RENTAL-LOTS,MEDIA-POLICY,PARENTAL,TRIAL-EDITION,API-SERVER,TELEMETRY,HANDOFF,CONTENT-PUBLISH}.md`, `docs/SHOP.md` (si w5-20) |
| w10-16 | nouveaux `docs/legal/{ACCORD-PRODUCTEUR,CHARTE-MODERATION,CGV-oeuvres-langues,REGISTRE-TRAITEMENTS-producteurs,CHECKLIST-JURISTE-producteurs}.md` (+ `docs/legal/README.md` s'il n'existe pas) |
| w10-17 | `docs/TEST-CAMPAIGN.md` (§ Œuvres), `.github/workflows/tools.yml`, `tools/requirements-dev.txt`, `tools/shop-test/**` (si présent), `docs/COORDINATION.md` (§ CI) |

Vérification de disjonction : aucun chemin n'apparaît deux fois dans une même sous-vague. Chevauchements **entre** sous-vagues (résolus par l'ordre 10a → 10b → 10c → 10d → 10e et une fusion entre chaque) : `C/shop/**` (w10-02 puis w10-12), `C/lots/*` (w10-08 : `RentalApi`, `TvLotStore` ; w10-11 : `LotStore` ; w10-12 : `EditionPolicy`), `R/ParentalHub.kt` (w10-10 ; w5-18 et w6-13 avant), `R/PlayerActivity.kt` (w10-09 ; w5-15, protect-02 avant), `C/owner/TrialPolicy.kt` (w10-08 ; w5-04 avant), `B/lots/**` (w10-05 ; w1-11 avant), docs.

## Graphe de dépendances

```
Prérequis souhaités : 4a ──► w10-08 (LotOpener) ;  w5-01 ──► w10-02 ;  w5-06/07 ──► w10-06 (SaleListener), w10-12 (serveur) ;  w5-11/12/15/16 ──► w10-11, w10-09 (boutique) ;  w5-18 ──► w10-10 ;  w1-06 ──► w10-08 (routes.txt)

10a : w10-01 ──┬─► w10-02        w10-03 (format work.json de w10-01 : codé contre la spécification § 3.2, vérifié à la fusion)
               └─► w10-04
10b : w10-01, w10-04 ──► w10-05 ──┬─► w10-06
                                  └─► w10-07
10c : w10-01 ──► w10-08 ∥ w10-09 (interface WorkHub) ∥ w10-10 (interface WorkHub.rating)
10d : w10-02 ──► w10-11 ∥ w10-12 ;  w10-04 + w10-08 ──► w10-13 ;  w10-14 (indépendant, peut partir jour 1)
10e : w10-15, w10-16, w10-17 en parallèle après 10d (w10-16 et w10-14 peuvent partir jour 1)
```

## Ordre de lancement conseillé

1. **Jour 1** : w10-01, w10-03, w10-04, w10-14, w10-16 en parallèle ; dès le rapport de w10-01 : w10-02. **Fusion 10a** : `:core:test`, tests Python (`tools/tests`, `tools/trial-edition`, `tools/producer-studio`), `git diff --quiet tools/activation/{test,rental,rental-v2,agent,shop,tokens}-vectors.json` (intacts).
2. **Jour 3** : w10-05 ; puis w10-06 et w10-07 en parallèle. **Fusion 10b** : `./mvnw test`, module `castbridge.producers` **éteint par défaut**, un lot `oeuvre` complet jamais servi par `/api/v1/lots/...` (test), préprod avec le catalogue des œuvres d'exemple.
3. **Jour 6** : w10-08 et w10-09 en parallèle (interface `WorkHub` publiée par w10-08 dans son rapport dès le jour 6 matin ; w10-09 code contre une implémentation mémoire), w10-10. **Fusion 10c** : `:receiver:compileDebugKotlin`, installation sur la TV de référence (32 bits) : aperçu lu, œuvre louée par `livrer.py` (w10-13 peut être lancé dès que w10-08 compile), compte à rebours, balayage après avance d'horloge (`adb shell cmd alarm set-time`), **mesure mémoire** pendant la lecture d'une œuvre de 25 Mo.
4. **Jour 9** : w10-11, w10-12, w10-13 en parallèle. **Fusion 10d** : `:sender:compileDebugKotlin`, parcours sur émulateur contre `FakeShopApi` (si w5-01) ou en mode « livraison par le propriétaire ».
5. **Jour 12** : w10-15, w10-17 (w10-16 déjà parti). Campagne w10-17 avec le propriétaire sur la TV de référence et un téléphone. **Puis** le pilote (DESIGN § 10) : `castbridge.producers.enabled=true` en production **seulement** après D-W10-1…9, relecture juridique de w10-16 et grille signée.
6. **Après la vague** : conteneur long format `castbridge-work-v1` (DESIGN § 5.6, deux cahiers), comptes producteurs en ligne (relevés), bouquets multi-producteurs, « pouce », filigrane si H6 l'exige.

## Changements aux cahiers W4/W5/W6 (sans les éditer ; l'exécutant d'un cahier encore à lancer lit ceci d'abord ; un cahier déjà fusionné est corrigé par les cahiers w10 indiqués)

| Cahier | Changement | Repris par |
|---|---|---|
| **w4-04** (lots au repos) | `LotOpener`/`SealedZipCache` : le cache de 2 lots est **par magasin** ; le magasin des œuvres en demande un de 1 ≤ 25 Mo (paramètre, défaut inchangé) | w10-08 |
| **w4-07** (`DegradedPolicy`) | `/api/oeuvres` (lecture) ouvert en mode réduit ; `/api/oeuvres/play` ouvert pour les aperçus et les locations en cours | w10-08 |
| **w5-01** (`ShopItem`, `ShopCatalog`) | `ShopItem.Work(id, days)`, `ShopItem.Channel(producer, days)`, `ShopItem.Purchase(bundleId)` ; `ShopCatalog.build` reçoit le catalogue des œuvres et le manifeste du `WorkStore` ; lignes `set=works.*` | w10-02 |
| **w5-04** (`TrialPolicy`) | `/api/oeuvres` et `/api/oeuvres/play` (aperçus seuls) dans l'allowlist d'essai ; `routes.txt` | w10-08 |
| **w5-06** (serveur locations) | scelle des lots `oeuvre` ≤ 25 Mo ; le contrat gratuit `essai\|tout` **ne couvre pas** `oeuvre-*`/`chaine-*` (le bouquet `tout` les exclut : w10-04) ; `shop_order.kind` gagne `PURCHASE` | w10-05, w10-12 |
| **w5-07** (commandes) | appelle `SaleListener.onFulfilled/onRefunded` (interface de w10-06) ; `PurchaseFulfilment` pour `achat-*` | w10-06, w10-12 |
| **w5-09** (console boutique) | lien « Producteurs » | w10-06 |
| **w5-11**, **w5-15** (boutiques) | onglets/sections « Langues », « Œuvres locales », page chaîne, fiche œuvre, taille contre `WorkBudget` | w10-11, w10-09 |
| **w5-12** (passerelle) | stock d'œuvres scellées séparé (300 Mo), aperçus par `LotSync` (`feature=oeuvre`) ; livraison inchangée | w10-11 |
| **w5-16** (`ShopHub`) | `POST /api/shop/catalog` accepte le catalogue des œuvres ; sans W5, `POST /api/oeuvres/catalog` (w10-08) | w10-08 |
| **w5-18** (parental achats) | inchangé ; w10-10 ajoute le filtre de classification des œuvres **après** lui | w10-10 |
| **w5-20**, **w5-21** (docs, CGV) | compléments œuvres / Langues | w10-15, w10-16 |
| **w6-09** (`X-CB-TV-Proof`) | inchangé : les routes d'œuvres scellées sont des « lots complets » | — |
| `docs/MEDIA-POLICY.md` § 1 | « œuvres locales : fonction `oeuvre`, magasin et budget propres, hors de la règle 3 Mo » | w10-15 |

## Coexistence avec W7/W8/W9/W11 (fichiers partagés ; ordre à respecter ou fusion entre les deux)

| Cahier W10 | Fichier partagé | Avec | Règle |
|---|---|---|---|
| w10-08 | `C/tv/ReceiverServer.kt`, `tools/routes/routes.txt`, `R/TvService.kt`, `R/LotsHub.kt`, `R/RentalHub.kt` | w8-10, w7-13, w7-14 | **après** eux ; `WorkStore` placé sous `Download/CastBridge/Medias/lots/oeuvre/` (emplacement de w9-14) ; `GET /api/oeuvres` devient une source de `SyncSources` (w7-13) après fusion |
| w10-09 | `R/PlayerActivity.kt` (`homeTools`), `R/HomeScreen.kt` | w11-04 → w11-10 → w11-11 → w11-12 | **après** w11-10 : tuile `oeuvres` via `HomeGrid`/`navTiles` ; sinon `homeTools` |
| w10-10 | `R/ParentalHub.kt` (`filterHome`), `C/parental/ParentalModel.kt` | w11-04, w5-18, w6-13 | **après** eux ; `KID_HOME_IDS` += `oeuvres` (conditionnel) |
| w10-07 | `C/telemetry/Telemetry.kt` | w11-04, w11-14 | **après** eux (id `oeuvres`) |
| w10-11 | `S/LotsRuntime.kt`, `S/MainActivity.kt` | w8-15, w11-01, w11-07 | **après** w8-15 ; entrée via le `Shell` de w11-07 s'il est fusionné |
| w10-12 | `R/LanguesHub.kt` | w9-14 | **après** w9-14 (une ligne) |
| w10-15 | `docs/MEDIA-POLICY.md` (§ 1), `docs/HANDOFF.md` | w9-16 (§ 5), w8-19, w11-14 | séquentiel (sections distinctes) |
| w10-04 | `tools/content-lots/build_lots.py` | — (w9-09 écrit `tools/content-gen/build_media_lots.py`, distinct) | aucun conflit |

## Routage des exécutants (en-têtes posés sur les 17 cahiers ; lignes à ajouter dans `docs/agent-briefs/routing.json` par le coordinateur, fichier non possédé par W10)

| id | modèle | effort | groupe | jauge (entrée / sortie) | audit Opus | pourquoi |
|---|---|---|---|---|---|---|
| w10-01 | sonnet | M | W10a-1 | 400 k / 20 k | échantillon | format signé nouveau, fichiers neufs, tests JVM |
| w10-02 | sonnet | M | W10a-2 | 400 k / 20 k | non | modèle pur, extension de w5-01 |
| w10-03 | sonnet | L | W10a-3 | 800 k / 40 k | non | presets et déterminisme : jugement ; découpage possible (presets+inspecter / emballer+verifier) |
| w10-04 | sonnet | M | W10a-4 | 400 k / 20 k | non | outils Python existants, catalogue signé (texte canonique) |
| w10-05 | sonnet | L | W10b-1 | 800 k / 40 k | **oui** | migration, garde de service des lots (C = 2) |
| w10-06 | sonnet | L | W10b-2 | 800 k / 40 k | **oui** | argent, relevés signés, ordre `POLICY` (C = 2) ; découpage possible (revue / relevés+retrait) |
| w10-07 | sonnet | S | W10b-3 | 150 k / 8 k | non | catalogue d'événements + agrégation |
| w10-08 | sonnet | L | W10c-1 | 800 k / 40 k | **oui** | routage des lots scellés, garde loopback, fichiers partagés (C = 2, D = 2) |
| w10-09 | sonnet | L | W10c-2 | 800 k / 40 k | non (TV : propriétaire) | UX D-pad |
| w10-10 | sonnet | M | W10c-3 | 400 k / 20 k | non | règles parentales pures + colle |
| w10-11 | sonnet | L | W10d-1 | 800 k / 40 k | non | Compose + stock + livraison |
| w10-12 | sonnet | M | W10d-2 | 400 k / 20 k | **oui** | émission d'un droit d'achat par le serveur (C = 2) |
| w10-13 | sonnet | M | W10d-3 | 400 k / 20 k | non | outil propriétaire, HTTP factice |
| w10-14 | haiku | S | W10d-4 | 60 k / 5 k | non | gabarits et script simple |
| w10-15 | sonnet | M | W10e-1 | 400 k / 20 k | non | docs exigeantes, lecture des rapports |
| w10-16 | sonnet | M | W10e-2 | 400 k / 20 k | non | rédaction juridique prudente (BLOQUÉ partiel) |
| w10-17 | haiku | S | W10e-3 | 60 k / 5 k | non | campagne et CI mécaniques |

## Décisions prises par l'architecte (renversables ; détail DESIGN § 9)
Une œuvre = un lot `oeuvre:<id>` ≤ 25 Mo + aperçu `-trial` ≤ 60 s ; second magasin TV 200 Mo (volume vidéo, clé USB d'abord), stock téléphone 300 Mo ; lecture en mémoire par loopback, repli fichier privé ; aucun nouveau format cryptographique au pilote ; plateforme signe et scelle, producteurs sans clé ; pas de filigrane forensique ; classification plateforme ≥ producteur, pas d'adulte ; aperçus visibles en essai ; locations livrées continuent après retrait sauf ordre `work.takedown` ; pas d'avis publics ; chaîne = 100 % au producteur, pas de bouquet multi-producteurs ; relevé mensuel signé, seuil 5 000 XAF (hypothèse), versement espèces ou bons ; Langues = libre gratuit + pack de confort sans mesure technique sur le libre ; module serveur éteint par défaut.

## Questions au propriétaire (les seules qui bloquent)

| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **D-W10-1** | Pilote papier maintenant + technique minimal en parallèle ? | calendrier | oui, les deux |
| **D-W10-2** | Prix d'hypothèse du pilote | grille du pilote, affiches (w10-14) | 150 / 300 / 500 / D9-bis / 500 XAF, 30 j |
| **D-W10-3** | Parts producteur / point focal / plateforme ; affichage | accord (w10-16), relevés (w10-06) | 60 / 15 / 25 ; afficher |
| **D-W10-4** | Qui verse (propriétaire seul ?), bons acceptés ? | w10-06, w10-14 | propriétaire seul ; bons si demandés |
| **D-W10-5** | Contenu politique partisan exclu ? | charte (w10-16) | exclu jusqu'à avis juridique |
| **D-W10-6** | Aperçus visibles en essai ? | w10-08 | oui |
| **D-W10-7** | « Pouce » / avis ? | w10-07, w10-09 | non |
| **D-W10-8** | Budget TV des œuvres, volume | w10-08 | 200 Mo, clé USB d'abord |
| **D-W10-9** | Pack Langues vendu comme service ? | w10-12, w10-16 | oui, à valider par un juriste |
| **D7**, **D9-bis** (W4) | contact, prix Apprendre | textes, grille | inchangé |
