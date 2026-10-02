# Vague 5 pour agents Sonnet/Haiku — index (2026-10-02) : boutique, locations en ligne, jetons du Quiz, espèces et bons

Source : `docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` (lire en entier avant tout cahier). Protocole et règles communes : `docs/COORDINATION.md` et l'en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »). **Ce fichier ne modifie pas les index des vagues 1-4** ; il liste au § « Changements aux cahiers w4 » ce que la vague 5 **retire ou amende** dans les cahiers w4 (sans les éditer).

**Décisions du propriétaire qui fondent la vague** (2026-10-02) : **P1** le point focal vend des **clés** (plus de location, plus de maître délégué) ; **P2** les **locations sont gérées en ligne par le serveur** ; **P3** **boutique** dans les deux applications (catalogue louable + jetons) ; **P4** **seul le Quiz** consomme des jetons (Sudoku, Échecs gratuits) ; **P5** **espèces et bons de recharge seulement** (aucun mobile money, aucun agrégateur, aucun cahier pour cela). Décisions antérieures conservées : D4 = NON (W4-A), D6 = mode réduit (W4-B), D5 = NON.

**Modèle d'exécution recommandé par cahier** : `haiku` = mécanique, textes, scripts simples ; `sonnet` = tout le reste. Les efforts sont en agent·jours (S ≈ 0,5-1, M ≈ 2, L ≈ 3-4).

**Prérequis fusionnés avant la vague 5** : **4a complète** (w4-01…06 : clé d'installation X25519, boîte v2, `KeystoreWrapper`, `LotCrypt`) — indispensable ; **4b** (w4-07…10 : `DegradedPolicy`, `GateState.Degraded`) ; **4c** amendée (w4-11, w4-14, w4-15, w4-16, w4-17 : délégation, journal chaîné, synchronisation ; w4-12 et w4-13 **dans leur version réduite**, § ci-dessous) ; w1-06 (`routes.txt`), w1-10 (vecteurs Java/Python), w2-01 (révocation TV), w3-03 (`OwnedLots` catalogue), w3-09 (relais révocations). protect-01…09 : indépendants, mais **protect-05** (battement de cœur) doit être fusionné **avant w5-10**.

## Cinq sous-vagues séquentielles ; fichiers disjoints à l'intérieur d'une sous-vague

| Sous-vague | Objet | Cahiers | Effort |
|---|---|---|---|
| **5a** | Cœur et formats : demande de boutique, bons, code de commande, grille étendue, bons de jetons, porte-jetons, commodités du Quiz, portes essai/réduit, émission sans maître, miroirs Java/Python et vecteurs | w5-01 … w5-05 | ≈ 11 j |
| **5b** | Serveur : contrats de location (clé aléatoire, KEK, scellement, cache), commandes/paiements/bons/reçus, grand livre des jetons, console, battement de cœur | w5-06 … w5-10 | ≈ 13 j |
| **5c** | Téléphone et outils : écrans Boutique, passerelle (demande, livraison, relais), mode point focal réduit (clés, bons, confirmation), outils du propriétaire (délégation sans maître, grille jetons, fabrication des bons) | w5-11 … w5-14 | ≈ 11 j |
| **5d** | TV : écran Boutique D-pad, routes et client en ligne, porte-jetons + Quiz, contrôle parental des achats, conditions et reçus | w5-15 … w5-19 | ≈ 12 j |
| **5e** | Docs, CGV, campagne de test, fiche point focal, CI | w5-20 … w5-24 | ≈ 6 j |

## Les 24 cahiers

| id | Cahier | Objet | Effort | Modèle | Statut | Dépend de |
|---|---|---|---|---|---|---|
| w5-01 | `sonnet-w5-01-shop-core-formats.md` | `C/shop/**` : `ShopRequest`, `OrderRef`, `VoucherCode`, `VoucherBatch`, `ShopCatalog` (bouquets + grille + réglages jetons), `ShopPolicy`, `ShopOrder` (états), `ShopApi` (interface + factice), `Receipt` v2 ; `shop-vectors.json` | L | sonnet | PRÊT | w4-11 (`PriceGrid`, `Receipt`) |
| w5-02 | `sonnet-w5-02-tokens-core.md` | `C/tokens/**` : enveloppe `type=tokens` (`TokenGrant`), `TokenWallet` (fichier chaîné + HMAC d'installation), `TokenPolicy`, `TokenSync` ; `tokens-vectors.json` | L | sonnet | PRÊT | w4-01 (`SecretWrapper`, `InstallKey`) |
| w5-03 | `sonnet-w5-03-quiz-core-boosts.md` | `QuizBoosts` (seconde chance, joker en plus, changer de question) dans `QuizGame`/`QuizRoom`, « points de défi » séparés des jetons, état JSON | M | sonnet | PRÊT | — (w5-02 pour le branchement réel, interface seule ici) |
| w5-04 | `sonnet-w5-04-gate-rental-core.md` | `Feature.SHOP`/`QUIZ_TASTER`, `TrialPolicy`/`DegradedPolicy` (routes boutique, partie découverte), `ActivationProof`, `RentalIssuing.rightWithKey` (sans maître), plafond 60 j en ligne, `routes.txt` | M | sonnet | PRÊT | w4-01, w4-07 |
| w5-05 | `sonnet-w5-05-shop-java-python.md` | Miroirs Java (`B/shop/wire/**`) et Python des formats de w5-01/02 ; rejeu des deux fichiers de vecteurs | M | sonnet | PRÊT | w5-01, w5-02, w4-05, w4-17 |
| w5-06 | `sonnet-w5-06-server-rentals-online.md` | `B/shop/rental/**` : KEK `rental-kek.key`, `rental_contract`, boîte v2 (XDH), scellement + cache disque, routes lots scellés, réémission, fenêtre d'essai gratuite ; `ActivationService.issueWithRights` ; migration `shop` ; `castbridge.shop` | L | sonnet | PRÊT | w5-04, w5-05, w4-05 |
| w5-07 | `sonnet-w5-07-server-orders-vouchers.md` | `B/shop/order/**` : `ShopRequestVerifier`, `OrderService`, `PaymentMethod` (bon, espèces agent, geste propriétaire), `VoucherService`, `ReceiptService`, `ShopController` ; `/api/v1/agent/orders/confirm`, `/agent/vouchers/stock` | L | sonnet | PRÊT | w5-06, w4-16 |
| w5-08 | `sonnet-w5-08-server-tokens.md` | `B/shop/tokens/**` : `token_account/ledger/grant`, bons de jetons signés, rapport de dépenses, réconciliation, anomalies, crédit d'accueil ; `EnvelopeIssuer.tokensGrant` | L | sonnet | PRÊT | w5-06, w5-05 |
| w5-09 | `sonnet-w5-09-server-admin-shop.md` | `/admin/shop/**` : commandes, paiements, contrats, bons (import d'un lot signé, révocation TOTP), jetons (ajustement TOTP), reçus, remboursements, CSV ; `ShopAnomalies` | M | sonnet | PRÊT | w5-06, w5-07, w5-08 |
| w5-10 | `sonnet-w5-10-heartbeat-shop-fields.md` | Battement de cœur : `tokensSeq`, `tokensMac`, `walletState`, `shopCatalogAt` (client cœur + serveur), recoupement avec `token_grant` | S | haiku | PRÊT (après protect-05) | protect-05, w5-08 |
| w5-11 | `sonnet-w5-11-phone-shop-ui.md` | CastBridge : onglet « Boutique » (leçons à louer, jetons, payer : code de recharge / espèces chez le point focal, commandes, reçus, ma TV), liens Apprendre/Jeux | L | sonnet | PRÊT | w5-01 (`ShopApi`), w5-12 en parallèle |
| w5-12 | `sonnet-w5-12-phone-gateway.md` | `ShopRuntime` : `HttpShopApi`, cache de la demande de la TV, sondage des commandes, téléchargement des lots scellés (Wi-Fi, reprise), livraison activation + lots + bons de jetons, relais catalogue/grille/bon en attente/rapport de dépenses, tâche périodique | L | sonnet | PRÊT | w5-01, w5-02, w5-06…08 (serveur de préprod ou `FakeShopApi`) |
| w5-13 | `sonnet-w5-13-agent-mode-shrink.md` | Mode « Point focal » réduit : clés + **vente de bons** + **confirmation de commandes** ; suppression du scellement et de la livraison de lots ; `SaleFlow` items `bon|…`, `commande|…` ; synchronisation des confirmations | M | sonnet | PRÊT (D7) | w4-13, w4-14 (fusionnés), w5-01 |
| w5-14 | `sonnet-w5-14-owner-tools-vouchers.md` | Console/bureau : délégation sans `master=` (`confirmOrders`, `sellVouchers`, `maxConfirmXafPerDay`), plus de maître, grille avec `jetons|…` et `tokens.*`, `tools/vouchers/make_vouchers.py` (lots signés, codes secrets, stock), `agent-vectors.json` régénéré | M | sonnet | BLOQUÉ partiel (D-W5-1, D-W5-2 pour les fichiers réels) | w4-11, w4-12 (fusionnés), w5-01 |
| w5-15 | `sonnet-w5-15-tv-shop-screen.md` | CastBridge-TV : `ShopActivity` D-pad (5 sections, cartes, clavier de saisie du bon, hors ligne, essai/réduit/enfant), tuile « Boutique », icône, « À propos : Boutique et jetons » | L | sonnet | PRÊT | w5-01, w5-16 en parallèle (interface `ShopStore`) |
| w5-16 | `sonnet-w5-16-tv-shop-hub-online.md` | `ShopHub` (routes `/api/shop/*`, `/api/tokens/*`, `GET /api/activation/proof`), `ShopStore` (cache signé), `ShopClient` (TV en ligne : mêmes routes serveur, lots dans le budget 10 Mo), `RentalOnlineInstaller`, télémétrie `shop` | L | sonnet | PRÊT | w5-01, w5-02, w5-04, w5-06…08 |
| w5-17 | `sonnet-w5-17-tv-token-wallet-quiz.md` | `TokenHub` (porte-jetons, clé HMAC dérivée de la clé d'installation), écrans Quiz : solde, confirmation de dépense, seconde chance / joker en plus / changer de question, « points de défi », partie découverte en essai | L | sonnet | PRÊT | w5-02, w5-03, w5-04 |
| w5-18 | `sonnet-w5-18-tv-parental-spending.md` | `Category.PURCHASES` (« Achats et jetons »), allocation quotidienne de jetons par profil enfant, « PIN pour acheter », profil enfant = jamais d'achat, rapport parental, lacune `games`/`sudoku` corrigée ; réglages côté téléphone | M | sonnet | PRÊT | w5-02, w5-17 en parallèle (interface `TokenHub.spendGuard`) |
| w5-19 | `sonnet-w5-19-shop-terms-receipts.md` | Acceptation des CGV boutique (version, date) sur TV et téléphone, magasin local des reçus (TV + téléphone), textes | S/M | haiku | PRÊT (texte CGV = brouillon w5-21, version « 0 ») | w5-01 |
| w5-20 | `sonnet-w5-20-docs-shop.md` | `docs/SHOP.md`, `docs/TOKENS.md` ; RENTAL-LOTS § 15 réécrit, LOTS § 1 (exception TV en ligne), TRIAL-EDITION, QUIZ, GAMES, PARENTAL, API-SERVER, ACTIVATION-FORMAT (types `tokens`), OWNER-CONSOLE, ACTIVATION-TOOLS, LICENSE-ADMIN, ADMIN, TELEMETRY, HANDOFF | M | sonnet | PRÊT | w5-01…19 |
| w5-21 | `sonnet-w5-21-legal-shop-tokens.md` | `docs/legal/CGV-boutique.md`, `BON-DE-RECHARGE.md`, `REGISTRE-TRAITEMENTS-boutique.md`, `JETONS-MINEURS-INDICATEURS.md` (brouillons, pas un avis juridique) | M | sonnet | BLOQUÉ partiel (juriste, D7) | w3-13 (si fusionné : même dossier) |
| w5-22 | `sonnet-w5-22-shop-test-campaign.md` | `tools/shop-test/` (fumée : commande avec bon → exécution → installation sur émulateur), `tools/rental-test` adapté (plus de maître), `docs/TEST-CAMPAIGN.md` § Boutique (40 étapes TV + téléphone + agent) | M | sonnet | PRÊT (propriétaire présent pour la TV) | w5-06…19 |
| w5-23 | `sonnet-w5-23-fiche-point-focal-v2.md` | `docs/FICHE-POINT-FOCAL.md` et `docs/VENTE-TERRAIN.md` : clés, bons (stock, serial, vente, perte), confirmation de commandes, versements | S | haiku | BLOQUÉ partiel (D7, D-W5-2) | w4-18 (fusionné), w5-13 |
| w5-24 | `sonnet-w5-24-ci-tools-shop.md` | `tools.yml` (tests vouchers, vecteurs boutique/jetons, shop-test en mode factice), `requirements-dev.txt`, script `tools/release/check_server_key.sh` (la clé publique du serveur est-elle dans `activation-trusted-keys.txt` ?), COORDINATION § CI | S | haiku | PRÊT | w5-05, w5-14, w5-22 |

## Matrice de propriété (preuve de disjonction par sous-vague)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OL/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`, `DK/` = `tools/activation-desktop/src/main/kotlin/castbridge/desktop/`, `B/` = `backend/src/main/java/castbridge/server/`, `BT/` = `backend/src/test/java/castbridge/server/`, `TPL/` = `backend/src/main/resources/templates/admin/`.

### 5a
| id | Fichiers possédés |
|---|---|
| w5-01 | nouveaux `C/shop/{ShopRequest,OrderRef,VoucherCode,VoucherBatch,ShopCatalog,ShopPolicy,ShopOrder,ShopApi,FakeShopApi,ShopVectors}.kt`, `CT/shop/**`, `tools/activation/shop-vectors.json` ; `C/sales/PriceGrid.kt`, `C/sales/Receipt.kt`, `CT/sales/{PriceGridTest,ReceiptTest}.kt` |
| w5-02 | nouveaux `C/tokens/{TokenGrant,TokenWallet,TokenPolicy,TokenSync,TokenVectors}.kt`, `CT/tokens/**`, `tools/activation/tokens-vectors.json` |
| w5-03 | `C/quiz/{QuizGame,QuizRoom,Wallet,QuizHttp}.kt`, nouveau `C/quiz/QuizBoosts.kt`, `CT/quiz/**` (et `CT/Quiz*Test.kt` s'ils sont à la racine) |
| w5-04 | `C/owner/{TrialPolicy,DegradedPolicy,FeatureGate,LicensedIssuer}.kt`, nouveau `C/owner/ActivationProof.kt`, `C/lots/{RentalKeys,RentalDurations}.kt`, `tools/routes/routes.txt`, `CT/owner/{TrialRoutesTest,DegradedRoutesTest,LicenseAndGateTest,ActivationProofTest}.kt`, `CT/lots/{RentalTest,RentalDurationsTest}.kt` |
| w5-05 | nouveaux `B/shop/wire/{ShopRequest,OrderRef,VoucherCode,VoucherBatch,TokenGrant,ShopWireVectors}.java`, `BT/shop/wire/ShopVectorsTest.java` ; `tools/activation/verify_vectors.py`, `tools/tests/test_verify_vectors.py` |

### 5b
| id | Fichiers possédés |
|---|---|
| w5-06 | nouveaux `B/shop/ShopProperties.java`, `B/shop/ShopFeature.java`, `B/shop/rental/**`, migration `V6x__shop.sql`, `BT/shop/rental/**` ; `B/licenses/ActivationService.java` (**une** méthode additive), `backend/src/main/resources/application.yml` (bloc `castbridge.shop`), `backend/README.md` (§ secrets) |
| w5-07 | nouveaux `B/shop/order/**`, `B/shop/agent/**`, `BT/shop/order/**`, `BT/shop/agent/**` |
| w5-08 | nouveaux `B/shop/tokens/**`, `BT/shop/tokens/**` ; `B/licenses/EnvelopeIssuer.java` (méthode additive) |
| w5-09 | nouveaux `B/shop/admin/**`, `B/shop/ShopAnomalies.java`, `TPL/shop*.html`, `BT/shop/admin/**` ; `TPL/lic-nav.html` (un lien) |
| w5-10 | `C/device/DeviceReport.kt`, `B/devices/DeviceReport.java`, `B/devices/DeviceService.java` (additif), `BT/DevicesApiTest.java` |

### 5c
| id | Fichiers possédés |
|---|---|
| w5-11 | nouveaux `S/shop/{ShopScreen,ShopPayScreen,ShopOrdersScreen,ShopTokensScreen,VoucherEntry,ShopTexts}.kt` ; `S/MainActivity.kt`, `S/LearnScreen.kt`, `S/GamesScreen.kt` |
| w5-12 | nouveaux `S/shop/{ShopRuntime,HttpShopApi,TvShopCache,ShopDelivery,ShopSyncJob,PendingVoucherRelay}.kt`, `CT/lots/RentalDeliveryTest.kt` ; `C/lots/RentalDelivery.kt`, `S/LotsRuntime.kt`, `S/RentalDeliveryActivity.kt` |
| w5-13 | `S/focal/**`, `C/sales/{SaleFlow,LedgerSync}.kt`, `CT/sales/{SaleFlowTest,LedgerSyncTest}.kt` |
| w5-14 | `OL/{ConsoleActivity,OwnerStore,DelegationsTab}.kt`, `C/owner/{Delegation,OwnerCli,PhoneConsole}.kt`, `DK/{Cli,Gui,Desk,DelegationStore}.kt`, `tools/activation-desktop/src/test/**`, `tools/prices/**`, `content/prices.json`, nouveaux `tools/vouchers/{make_vouchers.py,README.md}`, `tools/tests/{test_make_vouchers,test_sign_prices}.py`, `tools/activation/agent-vectors.json`, `CT/owner/{DelegationTest,AgentVectorsTest,OwnerCliTest,PhoneConsoleTest}.kt` |

### 5d
| id | Fichiers possédés |
|---|---|
| w5-15 | nouveaux `R/shop/{ShopActivity,ShopViews,VoucherKeypad,ShopTexts}.kt`, `android/receiver/src/main/res/drawable/ic_t_shop.xml` ; `R/PlayerActivity.kt`, `R/HomeScreen.kt`, `android/receiver/src/main/AndroidManifest.xml` (une activité `exported=false`) |
| w5-16 | nouveaux `R/shop/{ShopHub,ShopStore,ShopClient,RentalOnlineInstaller}.kt` ; `R/TvService.kt`, `R/RentalHub.kt`, `R/LotsHub.kt`, `C/lots/RentalApi.kt`, `CT/lots/RentalApiTest.kt`, `C/telemetry/Telemetry.kt`, `B/telemetry/EventCatalog.java` |
| w5-17 | nouveaux `R/tokens/{TokenHub,WalletKeys}.kt` ; `R/{QuizActivity,QuizHub,Games}.kt`, `R/QuizViews.kt` (si présent), `R/QuizScreens/**` (si w3-11 fusionné) |
| w5-18 | `R/{ParentalHub,ParentalActivity,ParentalUi}.kt`, `C/parental/{ParentalModel,ParentalEngine,ParentalApi}.kt`, `CT/Parental*Test.kt`, `S/ParentalScreen.kt` |
| w5-19 | nouveaux `C/shop/ShopTerms.kt`, `C/shop/ReceiptStore.kt`, `R/shop/ShopTermsView.kt`, `S/shop/ShopTermsScreen.kt`, `CT/shop/{ShopTermsTest,ReceiptStoreTest}.kt` |

### 5e
| id | Fichiers possédés |
|---|---|
| w5-20 | nouveaux `docs/SHOP.md`, `docs/TOKENS.md` ; `docs/{RENTAL-LOTS,LOTS,TRIAL-EDITION,QUIZ,GAMES,PARENTAL,API-SERVER,ACTIVATION-FORMAT,OWNER-CONSOLE,ACTIVATION-TOOLS,LICENSE-ADMIN,ADMIN,TELEMETRY,HANDOFF}.md` |
| w5-21 | nouveaux `docs/legal/{CGV-boutique,BON-DE-RECHARGE,REGISTRE-TRAITEMENTS-boutique,JETONS-MINEURS-INDICATEURS}.md` (et `docs/legal/README.md` s'il n'existe pas) |
| w5-22 | nouveaux `tools/shop-test/**` ; `tools/rental-test/**`, `docs/TEST-CAMPAIGN.md` |
| w5-23 | `docs/FICHE-POINT-FOCAL.md`, `docs/VENTE-TERRAIN.md` |
| w5-24 | `.github/workflows/tools.yml`, `tools/requirements-dev.txt`, nouveau `tools/release/check_server_key.sh`, `docs/COORDINATION.md` (§ CI) |

Vérification de disjonction : aucun chemin n'apparaît deux fois dans une même sous-vague. Chevauchements **entre** sous-vagues (résolus par l'ordre 5a → 5b → 5c → 5d → 5e et une fusion entre chaque) : `C/shop/**` (w5-01 puis w5-19), `C/lots/RentalApi.kt` (w5-16 seul en vague 5 ; w4-04/w4-09 avant), `R/RentalHub.kt` (w5-16 ; w4-03/w4-15 avant), `R/PlayerActivity.kt` (w5-15 ; protect-02, w4-08 avant), `S/MainActivity.kt` (w5-11 ; w4-13 avant), `C/owner/Delegation.kt` (w5-14 ; w4-11 avant), `B/licenses/{ActivationService,EnvelopeIssuer}.java` (w5-06, w5-08 : méthodes additives distinctes), docs.

## Graphe de dépendances

```
Prérequis : 4a (w4-01…06) ──► w5-02, w5-04, w5-06, w5-16, w5-17 ;  4b (w4-07…10) ──► w5-04 ;  4c amendée (w4-11, 14, 15, 16, 17) ──► w5-07, w5-13, w5-14 ;
            protect-05 ──► w5-10 ;  w1-06 ──► w5-04 ;  w1-10 / w4-05 / w4-17 ──► w5-05 ;  w3-13 (si fait) ──► w5-21.

5a : w5-01 ─┬─► w5-05        w5-03 (indépendant)       w5-04 (indépendant)
     w5-02 ─┘
5b : w5-05 ──► w5-06 ──┬─► w5-07 ──┐
                       └─► w5-08 ──┼─► w5-09        w5-10 (après protect-05 et w5-08)
                                   │
5c : w5-01 ──► w5-11 ∥ w5-12 (interface ShopApi de w5-01) ;  w5-13 ;  w5-14  (les quatre en parallèle)
5d : w5-16 ∥ w5-15 (interface ShopStore de w5-16 : w5-15 code contre une implémentation mémoire) ;  w5-17 ∥ w5-18 ;  w5-19
5e : w5-20, w5-21, w5-22, w5-23, w5-24 en parallèle (w5-20 et w5-23 après fusion de 5d)
```

## Ordre de lancement conseillé

1. **Jour 1** : w5-01, w5-02, w5-03, w5-04 en parallèle ; dès les rapports de w5-01 et w5-02 : w5-05. **Fusion 5a** : `:core:test` complet, `verify_vectors.py`, `./mvnw test` (vecteurs), vecteurs existants intacts (`git diff --quiet tools/activation/{test,rental,rental-v2,agent}-vectors.json` sauf `agent-vectors.json` régénéré par w5-14 **plus tard**, pas ici).
2. **Jour 4** : w5-06 seul (il fixe le schéma et `ShopProperties`) ; puis w5-07 et w5-08 en parallèle ; puis w5-09 ; w5-10 quand protect-05 est fusionné. **Fusion 5b** : `./mvnw test`, module **éteint par défaut**, démarrage de préprod avec `castbridge.shop.enabled=true` et un `rental-kek.key` de test ; **vérifier que la clé publique du serveur figure dans `activation-trusted-keys.txt`** de la TV (sinon rien ne s'installera : `tools/release/check_server_key.sh` de w5-24, ou à la main).
3. **Jour 9** : w5-11, w5-12, w5-13, w5-14 en parallèle. **Fusion 5c** : `:core:test`, `:sender:compileDebugKotlin`, `:ownerlib:compileDebugKotlin`, `:activation-desktop:test`, tests Python ; parcours sur émulateur contre la préprod ou `FakeShopApi`.
4. **Jour 13** : w5-16 et w5-15 en parallèle, w5-17 et w5-18 en parallèle, w5-19. **Fusion 5d** : `:receiver:compileDebugKotlin`, installation sur la TV de référence (32 bits) : tuile Boutique, saisie d'un bon de test, location de test livrée par le téléphone, dépense de jetons dans le Quiz, profil enfant bloqué.
5. **Jour 17** : w5-20…24. Campagne w5-22 avec le propriétaire : première commande réelle (bon de test), première confirmation par un point focal de test, premier reçu, premier remboursement de test ; mettre `castbridge.shop.enabled=true` en production **seulement** après D-W5-1/D-W5-2/D9-bis/D7 et la relecture juridique de w5-21.
6. Après la vague : scellement à deux niveaux (si plusieurs TV par téléphone devient courant), rotation de la KEK, ordre `rights.refresh` automatique, Play Store (facturation obligatoire : à étudier avant toute publication), paiement supplémentaire (hors décision actuelle).

## Changements aux cahiers w4 (sans les éditer : l'exécutant d'un cahier w4 encore à lancer lit ceci d'abord ; un cahier déjà fusionné est corrigé par les cahiers w5 indiqués)

| Cahier w4 | Changement | Repris par |
|---|---|---|
| **w4-01** (boîte v2, cœur) | inchangé. Précision : `RentalKeys.masterFrom` reste **pour les tests et les vecteurs v1 seulement** (KDoc à mettre à jour) | w5-04 |
| **w4-02** (outils, demande v2) | inchangé ; mais les écrans d'émission n'exposent **plus** de location (déjà le cas depuis RENTAL-LOTS § 15) | — |
| **w4-03** (TV : clé d'installation) | inchangé ; `GET /api/activation` doit exposer `installKeyProtection` (déjà prévu) : la boutique l'affiche | w5-16 |
| **w4-04** (lots au repos) | inchangé ; **note** : `LotSealing.Rental(contrat)` sera appelé aussi par l'installation en ligne de la TV (`RentalOnlineInstaller`) | w5-16 |
| **w4-05** (Java/Python v2) | inchangé ; **la phrase « le serveur n'émet pas de location » est caduque** : `RentalBoxV2.java` doit exposer `makeBox(installPub, ephSeed, product, period, rentalKey)` côté **émission** (pas seulement rejeu) ; si w4-05 est déjà fusionné sans cela, w5-06 l'ajoute dans `B/shop/rental/RentalBoxIssuer.java` | w5-06 |
| **w4-06** (docs 4a) | ne pas écrire RENTAL-LOTS § 15 ; laisser « conception serveur : voir W5 » | w5-20 |
| **w4-07…w4-10** (mode réduit) | inchangés ; w5-04 ajoute `Feature.SHOP`, `QUIZ_TASTER` et les routes boutique à `DegradedPolicy`/`TrialPolicy` **après** eux | w5-04 |
| **w4-11** (délégation, cœur) | **amendé** : la délégation **n'a plus** `agentx=` ni `master=` (étapes 2 `wrapMaster/openMaster` et vecteurs `master-wrap` : **supprimés**) ; `maxRentalDays` est toujours **0** (`Delegation.verify` refuse > 0) ; nouveaux champs optionnels `confirmOrders=1`, `sellVouchers=1`, `maxConfirmXafPerDay=<n>` ; `DelegatedVerifier` refuse **toute** ligne `rental` d'un agent ; `SalesLedger.Entry.item` admet `bon\|<article>\|<serial>` et `commande\|<ref>\|<montant>` ; la dépendance à w4-01 disparaît. Si w4-11 est déjà fusionné avec `master=` : w5-14 retire les champs et régénère `agent-vectors.json` | w5-14 |
| **w4-12** (outils propriétaire) | **amendé** : étape 1 (`master=` dans `owner-vault.txt`, `rentalMaster`, `exportMaster`) **supprimée** ; étape 3 : plus de `maitre importer/exporter`, `--location-max-jours` disparaît, options `--confirmer-commandes`, `--vendre-bons`, `--plafond-confirmation-xaf` ; cas limite `MASTER_SWITCH_MS` **supprimé** ; la grille gagne `jetons\|…` et `tokens.*` | w5-14 |
| **w4-13** (app point focal) | **amendé** : `FocalSealer.kt`, la livraison de lots dans `FocalDelivery.kt` et toute vente de location (`RentalSpec`) **supprimées** ; `SaleFlow` ne connaît que `cle-essai`, `cle-production`, `bon`, `commande` ; nouveaux écrans « Vendre un bon » et « Confirmer une commande » ; « Préparer » ne télécharge plus les lots | w5-13 |
| **w4-14** (journal, synchronisation) | inchangé ; la synchronisation transporte aussi les entrées `commande\|…` et `bon\|…` (format inchangé) | w5-13 (client), w5-07 (serveur) |
| **w4-15** (TV : ticket) | **amendé** : `DelegatedVerifier` sur la TV refuse une activation d'agent portant `rental` (déjà la règle `maxRentalDays = 0`) ; rien d'autre | — |
| **w4-16** (serveur agents) | **amendé** : colonnes `may_confirm_orders`, `may_sell_vouchers`, `max_confirm_xaf_per_day` ; anomalies `VOUCHER_*` et confirmations hors quota ; les routes `/agent/orders/confirm` et `/agent/vouchers/stock` sont dans w5-07 (`B/shop/agent/**`), pas dans `B/agents/**` ; **w4-16 doit exposer** `AgentService.verifyDelegation(token)` et `AgentLedgerVerifier.verifyEntry(entry, delegation)` publics (sinon w5-07 les demande dans son rapport) | w5-07, w5-09 |
| **w4-17** (Java/Python agents) | **amendé** : `Delegation.java` sans `master=`/`agentx=`, avec les trois nouveaux champs ; vecteurs régénérés par w5-14 et rejoués ici | w5-05 (rejeu), w5-14 (vecteurs) |
| **w4-18** (docs, fiche) | **amendé** : la fiche du point focal ne décrit plus la location ; elle décrit les bons et la confirmation de commandes | w5-23 |
| `sonnet-w2-06`, `sonnet-w2-10` | restent abandonnés (remplacés par w5-07, w5-11, w5-12) | — |
| `sonnet-w3-13` (brouillons légaux) | inchangé ; w5-21 ajoute quatre documents dans le même dossier | w5-21 |
| `protect-05` | inchangé ; w5-10 ajoute des champs **après** lui | w5-10 |

## Décisions prises par l'architecte (renversables ; détail au § 13 de la conception)
Bon de recharge = chemin principal, commande en espèces = complément ; aucun maître partagé (clé aléatoire par contrat sous `rental-kek.key`) ; fenêtre d'essai des lots servie par le serveur ; location en ligne ≤ 60 j, 3 contrats actifs par licence ; jetons = trois commodités du Millionnaire (5/2/3), sans expiration, 10 d'accueil, plafond hors ligne 60, **jamais** de mise redistribuée en jetons achetés (« points de défi » séparés) ; essai = boutique en lecture + 3 parties découverte/jour, aucune commande ; mode réduit = lecture + bon de clé ; profil enfant = jamais d'achat, allocation 0 par défaut ; commande valable 72 h ; réémission de boîte gratuite ≤ 3 ; module serveur éteint par défaut ; la TV ne télécharge que ses propres locations, à la demande.

## Questions au propriétaire (les seules qui bloquent)

| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **D-W5-1** | Paquets de jetons (tailles, prix XAF), prix en jetons des commodités (5/2/3 ?), jetons d'accueil (10 ?), expiration (aucune ?) | w5-14 (grille réelle), w5-21 | 20 / 60 / 150 ; 5/2/3 ; 10 ; aucune |
| **D-W5-2** | Dénominations et premier tirage des bons de recharge ; lots affectés par point focal ou libres | w5-14 (fabrication), w5-23 | `jetons\|60` et `loc-<bouquet>\|30` pour 3 bouquets phares, 200 bons chacun, lots affectés |
| **D9-bis** | Montants XAF des clés et des locations par bouquet | grille | inchangé (W4) |
| **D7** | Nom commercial et contact (reçus, « point focal le plus proche », CGV) | textes | inchangé (W4) |

## Routage des modèles (Fable, 2026-10-02) — vague 5 (confirme la colonne « Modèle » : aucune divergence)

Source : `docs/coordination/ROUTAGE-AGENTS-EXECUTION-2026-10-02.md` (grille, jauges, audits, dispatch) ; table machine `docs/agent-briefs/routing.json` ; `python3 tools/agents/dispatch-plan.py --wave <vague> --done <ids>` donne les cahiers lançables. Chaque cahier porte un en-tête « Modèle · Groupe · Jauge ». Modèle **explicite** à chaque lancement ; un seul build JVM à la fois (`tools/agents/gradle-lock.sh`) ; au plus 3 agents en parallèle.

- **haiku** (4) : w5-10, w5-19, w5-23, w5-24 — cahiers à convertir en forme mécanique (avant/après) avant lancement.
- **sonnet** (20) : w5-01, w5-02, w5-03, w5-04, w5-05, w5-06, w5-07, w5-08, w5-09, w5-11, w5-12, w5-13, w5-14, w5-15, w5-16, w5-17, w5-18, w5-20, w5-21, w5-22.
- **audit Opus avant fusion** (8) : w5-02, w5-04, w5-06, w5-07, w5-08, w5-12, w5-16, w5-17.
- **non lançables** : aucun.
- **opus** n'exécute jamais ; **fable** ne figure dans aucun routage.
