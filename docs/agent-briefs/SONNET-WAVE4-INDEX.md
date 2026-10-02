# Vague 4 pour agents Sonnet — index (2026-10-02)

Sources : `docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md` (4a), `docs/coordination/DESIGN-W4-MODE-DEGRADE.md` (4b), `docs/coordination/DESIGN-W4-VENTE-TERRAIN.md` (4c). Protocole et règles communes : `docs/COORDINATION.md` et l'en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>`, rapport `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »). **Ce fichier ne modifie pas l'index des vagues 1-3.**

**Décisions du propriétaire qui fondent la vague** : D4 = **NON** (la KEK dérivée des empreintes et les lots en clair se **corrigent**) ; D8/D9 = **espèces à un point focal** (pas de mobile money, pas de commande en ligne) ; D6 = **mode réduit** à la fin d'une clé de production ; D5 = NON (shell SSH gardé : la conception 4a en tient compte) ; D1, D2, D13, D17 = oui (sans effet direct ici).

**Cahiers remplacés (ne plus les lancer ; s'ils sont déjà fusionnés, leurs paragraphes sont réécrits par w4-06 / w4-18)** : `sonnet-w1-13` (doc « honnête » de la faille) → w4-01…06 ; `sonnet-w2-06` (boutique téléphone) et `sonnet-w2-10` (commandes serveur) → w4-11…18. `sonnet-w2-04` reste valable (il a laissé `TvGate` intact : w4-07 prend la suite).

**Trois sous-vagues séquentielles** ; **à l'intérieur d'une sous-vague, les fichiers sont disjoints** (matrice ci-dessous) : ses cahiers tournent en parallèle, dans l'ordre de lancement conseillé. Un fichier non listé est hors zone.

| Sous-vague | Objet | Cahiers | Effort total |
|---|---|---|---|
| **4a** | Enveloppe des clés de location v2 (clé d'installation X25519, Keystore), lots chiffrés au repos, vecteurs v2, outils, serveur/Python, docs | w4-01 … w4-06 | ≈ 11 j |
| **4b** | Mode réduit à la fin d'une clé de production | w4-07 … w4-10 | ≈ 4 j |
| **4c** | Vente au comptant par les points focaux : délégation, app agent, journal chaîné, serveur de réconciliation, docs et fiche | w4-11 … w4-18 | ≈ 19 j |

## Les 18 cahiers

| id | Cahier | Objet | Effort | Statut | Dépend de |
|---|---|---|---|---|---|
| w4-01 | `sonnet-w4-01-rental-box-v2-core.md` | X25519 pur Kotlin, `SecretWrapper`, `InstallKey`, boîte `v2:`, lecture v1 bornée (coucher 2027-01-01), demande v2 (`install=`), `rental-vectors-v2.json` | M | PRÊT | — |
| w4-02 | `sonnet-w4-02-rental-box-v2-issuer-tools.md` | Console téléphone, bureau, `OwnerCli` : demande v2, refus sans clé d'installation, option « enveloppe v1 » | M | PRÊT | w4-01 |
| w4-03 | `sonnet-w4-03-rental-box-v2-tv.md` | TV : `KeystoreWrapper`, clé d'installation, demande v2, ouverture v2, état de protection | M | PRÊT | w4-01 (déployer après w4-02, w4-05) |
| w4-04 | `sonnet-w4-04-lots-at-rest.md` | `LotCrypt` : `lot.enc` + clé de lot enveloppée (contrat ou magasin), lecture en mémoire avec cache, `RentalApi` sans clair durable, Quiz optionnel | L | PRÊT | w4-01, w4-03, **w1-01** (`allowBackup`) |
| w4-05 | `sonnet-w4-05-rental-box-v2-java-python.md` | Serveur et Python : demande v2 tolérée, boîte v2 rejouée (XDH / `cryptography`) | M | PRÊT | w4-01, **w1-10** |
| w4-06 | `sonnet-w4-06-rental-box-v2-docs.md` | RENTAL-LOTS, ACTIVATION-FORMAT, TRIAL-EDITION, OWNER-CONSOLE, ACTIVATION-TOOLS, LICENSE-ADMIN, ADMIN, HANDOFF | S | PRÊT | w4-01…05 |
| w4-07 | `sonnet-w4-07-degraded-core.md` | `TvAccess.degraded`, droits durables (achats survivent au renouvellement), `GateState.Degraded`, `DegradedPolicy`, badge « CLÉ TERMINÉE », tests réécrits | M | PRÊT | **w2-04** (`endedSummary`), **w1-05**, **w1-06** (`routes.txt`) |
| w4-08 | `sonnet-w4-08-degraded-tv.md` | TV : `restriction()/reduced()`, garde de routes, 23 sites relus, tuiles, écran de renouvellement, réévaluation toutes les 15 min | M | PRÊT | w4-07 |
| w4-09 | `sonnet-w4-09-degraded-lots-rentals.md` | `OwnedLots` en mode réduit, `/api/rental/install` 403, `reconcile` explicite, liste de contrôle TV | S | PRÊT | w4-07 (**w3-03** souhaitable) |
| w4-10 | `sonnet-w4-10-degraded-docs.md` | Formats, TRIAL-EDITION § 17, ADMIN, CGV (clause), HANDOFF | S | PRÊT | w4-07…09 |
| w4-11 | `sonnet-w4-11-agent-delegation-core.md` | `KeyScope.DELEGATE`, `Delegation`, `DelegatedVerifier`, `TicketedActivation`, `PriceGrid`, `SalesLedger`, `Receipt`, rejeu avec clés déléguées, `agent-vectors.json` | L | PRÊT | w4-01 (boîte v2 pour `master=`) |
| w4-12 | `sonnet-w4-12-agent-owner-tools.md` | Console : onglet « Points focaux », maître explicite, révocation ; bureau : `delegation`, `maitre`, `grille-prix` ; `tools/prices/sign_prices.py` | M | BLOQUÉ partiel (D9-bis montants) | w4-11 |
| w4-13 | `sonnet-w4-13-agent-phone-mode.md` | CastBridge : mode « Point focal » (`S/focal/**`), `SaleFlow` (cœur), émission avec ticket, scellement, livraison, `TRUSTED_KEYS` du téléphone | L | PRÊT (D7 pour le contact) | w4-11 (w4-14 en parallèle) |
| w4-14 | `sonnet-w4-14-agent-ledger-sync.md` | Journal à ajout seul (`fsync`), synchronisation `POST /api/v1/agent/sync`, écran des ventes, reçus | M | PRÊT | w4-11 (w4-16 pour le bout en bout) |
| w4-15 | `sonnet-w4-15-agent-tv.md` | TV : accepter `<délégation>\|<activation>`, `DelegationStore`, affichage « Point focal », révocation d'agent | M | PRÊT | w4-11, **w2-01** |
| w4-16 | `sonnet-w4-16-agent-server.md` | Serveur : tables agents, synchronisation, anomalies, `/admin/agents` (versements TOTP, révocation `cbr1`), `/api/v1/catalog/prices` | L | PRÊT | w4-11, w4-17 |
| w4-17 | `sonnet-w4-17-agent-java-python.md` | Java/Python : `delegation`, ticket, journal, grille, reçu ; `agent-vectors.json` rejoué | M | PRÊT | w4-11 |
| w4-18 | `sonnet-w4-18-agent-docs-training.md` | `docs/VENTE-TERRAIN.md`, `docs/FICHE-POINT-FOCAL.md`, mises à jour des docs existants | M | BLOQUÉ partiel (D7, D9-bis) | w4-11…17 |

## Matrice de propriété (preuve de disjonction par sous-vague)

Préfixes : `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `OL/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`, `DK/` = `tools/activation-desktop/src/main/kotlin/castbridge/desktop/`, `B/` = `backend/src/main/java/castbridge/server/`, `BT/` = `backend/src/test/java/castbridge/server/`.

### Sous-vague 4a

| id | Fichiers possédés |
|---|---|
| w4-01 | nouveaux `C/owner/X25519.kt`, `C/crypto/SecretWrapper.kt`, `C/lots/InstallKey.kt`, `C/lots/RentalVectorsV2.kt`, `tools/activation/rental-vectors-v2.json`, `CT/owner/X25519Test.kt`, `CT/lots/InstallKeyTest.kt`, `CT/lots/RentalVectorsV2Test.kt` ; `C/lots/RentalKeys.kt`, `C/lots/RentalLedger.kt`, `C/owner/OwnerFrames.kt`, `C/owner/LicensedIssuer.kt`, `C/owner/LotKeys.kt` (KDoc), `CT/lots/RentalTest.kt`, `CT/lots/TrialWindowTest.kt` |
| w4-02 | `OL/ConsoleActivity.kt`, `C/owner/OwnerCli.kt`, `C/owner/PhoneConsole.kt`, `C/owner/ProductionForm.kt`, `DK/{Cli,Gui,GuiRights,Desk,Vectors}.kt`, `tools/activation-desktop/src/test/**`, `CT/owner/{OwnerCliTest,PhoneConsoleTest,ProductionFormTest}.kt` |
| w4-03 | nouveau `R/KeystoreWrapper.kt` ; `R/ActivationCenter.kt`, `R/RentalHub.kt`, `R/TvService.kt` (ligne `/api/activation` seulement), `android/core/src/main/resources/castbridge/admin.html` |
| w4-04 | nouveaux `C/lots/LotCrypt.kt`, `CT/lots/LotCryptTest.kt` ; `C/lots/{LotApi,TvLotStore,RentalApi,LotAdapters}.kt`, `C/learn/{LearnLotConsumer,LearnLots}.kt`, `C/langues/LangLotConsumer.kt`, `C/quiz/QuizLots.kt` (optionnel), `R/{LotsHub,LearnHub,LanguesHub}.kt`, `CT/{LearnLotsTest,LangLotConsumerTest,QuizLotsTest}.kt`, `CT/lots/{LotsTvTest,RentalApiTest,LotsTestKit}.kt` |
| w4-05 | `B/licenses/DeviceIdentity.java`, `B/licenses/WireActivation.java`, nouveau `B/licenses/RentalBoxV2.java`, `BT/licenses/{RentalVectorsV2Test,DeviceIdentityTest}.java`, `tools/activation/verify_vectors.py`, `tools/requirements-dev.txt`, `tools/tests/test_verify_vectors.py` (s'il existe) |
| w4-06 | `docs/{RENTAL-LOTS,ACTIVATION-FORMAT,TRIAL-EDITION,OWNER-CONSOLE,ACTIVATION-TOOLS,LICENSE-ADMIN,ADMIN,HANDOFF}.md` |

### Sous-vague 4b

| id | Fichiers possédés |
|---|---|
| w4-07 | `C/owner/Activation.kt` (`TvAccess`, `TvGate` seulement), `C/owner/FeatureGate.kt` (sauf `ActivationReceiver`), nouveau `C/owner/DegradedPolicy.kt`, `C/owner/{KeyBadge,KeyStatusJson,TrialPolicy}.kt`, `CT/owner/{DegradedModeTest,DegradedRoutesTest,UsageCeilingTest,ClockRollbackTest,LicenseAndGateTest,KeyBadgeTest}.kt`, `CT/KeyStatusJsonTest.kt`, `tools/routes/{routes.txt,list_routes.py}`, `tools/tests/test_routes.py` |
| w4-08 | `R/{ActivationCenter,TvService,PlayerActivity,HomeScreen,ActivationActivity,BtServer,UsbImporter,SshControl,Games,ParentalHub,LanguesActivity,KeyBadgeOverlay}.kt`, `android/core/src/main/resources/castbridge/admin.html` |
| w4-09 | `C/lots/{OwnedLots,EditionPolicy,RentalApi,RentalSweeper}.kt`, `CT/lots/{OwnedLotsDegradedTest,EditionTest,RentalApiTest}.kt`, `docs/TEST-CAMPAIGN.md` |
| w4-10 | `docs/{ACTIVATION-FORMAT,TRIAL-EDITION,OWNER-CONSOLE,ADMIN,HANDOFF}.md`, `docs/legal/CGV-location.md` (ou `docs/legal/README.md`) |

### Sous-vague 4c

| id | Fichiers possédés |
|---|---|
| w4-11 | `C/owner/Keys.kt`, `C/owner/License.kt`, nouveaux `C/owner/{Delegation,DelegatedVerifier,TicketedActivation,AgentVectors}.kt`, `C/sales/{PriceGrid,SalesLedger,Receipt}.kt`, `tools/activation/agent-vectors.json`, `CT/owner/{DelegationTest,TicketedActivationTest,AgentVectorsTest,LicenseAndGateTest}.kt`, `CT/sales/{PriceGridTest,SalesLedgerTest,ReceiptTest}.kt` |
| w4-12 | `OL/{ConsoleActivity,OwnerStore}.kt`, nouveau `OL/DelegationsTab.kt`, `C/owner/{OwnerCli,PhoneConsole}.kt`, `DK/{Cli,Gui,Desk,KeyFile}.kt`, nouveau `DK/DelegationStore.kt`, `tools/activation-desktop/src/test/**`, nouveaux `tools/prices/{sign_prices.py,README.md}`, `tools/tests/test_sign_prices.py`, `content/prices.json`, `CT/owner/{OwnerCliTest,PhoneConsoleTest}.kt` |
| w4-13 | nouveaux `S/focal/{FocalActivity,FocalStore,FocalScreens,FocalSealer,FocalDelivery,PendingSales}.kt`, `C/sales/SaleFlow.kt`, `CT/sales/SaleFlowTest.kt` ; `S/MainActivity.kt`, `android/sender/build.gradle.kts`, `S/RentalDeliveryActivity.kt` |
| w4-14 | nouveaux `C/sales/{LedgerFile,LedgerSync}.kt`, `S/focal/{LedgerFile,LedgerSyncJob,LedgerScreen,ReceiptShare}.kt`, `CT/sales/{LedgerFileTest,LedgerSyncTest}.kt` |
| w4-15 | `R/ActivationCenter.kt`, nouveau `R/DelegationStore.kt`, `R/ActivationActivity.kt`, `R/RentalHub.kt` (`ActivationInstallApi` seulement), `R/OwnerBtHost.kt` |
| w4-16 | nouveau paquet `B/agents/**`, migration `V6x__agents.sql`, `templates/admin/agents*.html`, `templates/admin/lic-nav.html`, `application.yml` (bloc `castbridge.agents`), `B/licenses/{LicenseKeyring,RegistryStore}.java` (méthodes additives), `BT/agents/**`, `BT/licenses/LicenseKeyringTest.java` |
| w4-17 | nouveaux `B/licenses/{Delegation,TicketedActivation,AgentLedgerEntry,PriceGrid,ReceiptCode}.java`, `BT/licenses/AgentVectorsTest.java` ; `B/licenses/EnvelopeVerifier.java` (point d'extension additif), `tools/activation/verify_vectors.py` |
| w4-18 | nouveaux `docs/VENTE-TERRAIN.md`, `docs/FICHE-POINT-FOCAL.md` ; `docs/{ACTIVATION-FORMAT,RENTAL-LOTS,OWNER-CONSOLE,ACTIVATION-TOOLS,LICENSE-ADMIN,API-SERVER,TELEMETRY,HANDOFF}.md` |

Vérification de disjonction (à relancer par le coordinateur si un cahier change) : aucun chemin n'apparaît deux fois dans une même sous-vague ; les chevauchements **entre** sous-vagues (`R/ActivationCenter.kt` : w4-03, w4-08, w4-15 ; `C/owner/Activation.kt` : w4-07 seul mais `ActivationVerifier` reste intact pour w4-11 ; `verify_vectors.py` : w4-05, w4-17 ; `R/RentalHub.kt` : w4-03, w4-15 ; docs) sont résolus par l'ordre séquentiel 4a → 4b → 4c et une fusion entre chaque sous-vague.

## Graphe de dépendances

```
Prérequis des vagues 1-3 (fusionnés avant) : w1-01 (allowBackup) → w4-04 ; w1-05/w1-06 → w4-07 ; w1-10 (vecteurs Java/Python v1) → w4-05 ; w2-01 (révocation TV) → w4-15 ;
                                              w2-03 (OWNER_CONTACT) → w4-13/w4-15 (facultatif) ; w2-04 (endedSummary) → w4-07 ; w3-03 (OwnedLots catalogue) → w4-09 (souhaitable) ; w3-09 (relais cbr1) → révocation d'agent sur les TV.

4a : w4-01 ─┬─ w4-02 ─┐
            ├─ w4-03 ─┼─ w4-04 (après w4-03)      w4-06 (docs, à la fin)
            └─ w4-05 ─┘
     Déploiement sur les TV : après w4-02 et w4-05 (les outils lisent la demande v2 avant que la TV ne l'émette).

4b : w4-07 ─┬─ w4-08
            └─ w4-09        w4-10 (docs, à la fin)

4c : w4-11 ─┬─ w4-12
            ├─ w4-13 ─┐ (parallèle, fichiers disjoints)
            ├─ w4-14 ─┘
            ├─ w4-15
            └─ w4-17 ── w4-16        w4-18 (docs + fiche, à la fin)
```

## Ordre de lancement conseillé

1. **Jour 1** : w4-01 seul (il fixe les API de toute la sous-vague 4a). Dès son rapport : w4-02, w4-03, w4-05 en parallèle ; puis w4-04 (après w4-03) ; w4-06 en dernier. **Fusion 4a** : `:core:test`, `:activation-desktop:test`, `./mvnw test`, `verify_vectors.py`, compilation `:receiver`/`:sender`/`:ownerlib`, installation sur la TV de référence (vérifier la protection Keystore affichée et le flux « réinstallation ⇒ réémission »).
2. **Jour 5** : w4-07 seul ; puis w4-08 et w4-09 en parallèle ; w4-10. **Fusion 4b** + les étapes « Mode réduit » de `docs/TEST-CAMPAIGN.md` sur la TV (clé de production de 1 jour).
3. **Jour 8** : w4-11 seul ; puis w4-12, w4-13, w4-14, w4-15, w4-17 en parallèle ; w4-16 après w4-17 ; w4-18 à la fin. **Fusion 4c** : vente de bout en bout sur émulateur (délégation de test), puis avec le propriétaire : première délégation réelle, première vente test, première synchronisation, premier versement enregistré.
4. Après la vague : rotation du maître des locations (identifiant de maître dans la ligne `rental`), Quiz au repos si l'étape 7 de w4-04 n'a pas été faite, scanner QR dans l'app du point focal (aucune bibliothèque QR dans le dépôt aujourd'hui), journal d'agent multi-téléphone.

## Questions au propriétaire (les seules qui bloquent)

| # | Question | Bloque | Recommandation |
|---|---|---|---|
| D9-bis | Grille de prix : durées de clé vendues par un point focal (30 / 90 / 365 jours ?) et montants XAF par article (`cle-production|<jours>`, `loc-<bouquet>|<rentalDays>`) | w4-12 (fichier réel), w4-18 (fiche) | Démarrer avec trois durées et un prix par bouquet ; la mécanique accepte toute grille |
| D7 | Nom commercial et contact (WhatsApp) à imprimer sur le reçu et la fiche, et à compiler (`OWNER_CONTACT`, w2-03) | w4-13 (ligne du reçu), w4-18 | Fournir les deux ; sans eux la ligne est omise, rien n'est inventé |

Décisions **prises par l'architecte** (renversables, listées à la fin de chaque conception) : coucher de l'enveloppe v1 au 2027-01-01 ; repli en clair signalé si le Keystore échoue ; essai terminé = verrouillage total ; locations en cours continuent en mode réduit, Quiz/Échecs fermés, Sudoku ouvert ; délégation 90 j (180 max), 200 ventes, jamais de clé illimitée ni d'achat définitif par un agent ; maître unique du propriétaire enveloppé dans la délégation ; remboursement après livraison = acte du propriétaire sur le serveur.

## Routage des modèles (Fable, 2026-10-02) — vague 4

Source : `docs/coordination/ROUTAGE-AGENTS-EXECUTION-2026-10-02.md` (grille, jauges, audits, dispatch) ; table machine `docs/agent-briefs/routing.json` ; `python3 tools/agents/dispatch-plan.py --wave <vague> --done <ids>` donne les cahiers lançables. Chaque cahier porte un en-tête « Modèle · Groupe · Jauge ». Modèle **explicite** à chaque lancement ; un seul build JVM à la fois (`tools/agents/gradle-lock.sh`) ; au plus 3 agents en parallèle.

- **haiku** (3) : w4-06, w4-10, w4-18 — cahiers à convertir en forme mécanique (avant/après) avant lancement.
- **sonnet** (15) : w4-01, w4-02, w4-03, w4-04, w4-05, w4-07, w4-08, w4-09, w4-11, w4-12, w4-13, w4-14, w4-15, w4-16, w4-17.
- **audit Opus avant fusion** (8) : w4-01, w4-03, w4-04, w4-05, w4-07, w4-11, w4-15, w4-16.
- **non lançables** : aucun.
- **opus** n'exécute jamais ; **fable** ne figure dans aucun routage.
