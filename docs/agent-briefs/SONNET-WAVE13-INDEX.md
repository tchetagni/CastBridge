# Vague 13 pour agents Sonnet/Haiku — index (2026-10-02) : « Aucun blocage silencieux » (transferts CastBridge → CastBridge-TV)

Source : `docs/coordination/DESIGN-W13-AUCUN-BLOCAGE-SILENCIEUX-2026-10-02.md` (lire § 0-3 avant tout cahier ; § 2 = catalogue, § 3 = mécanisme). Protocole et règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »), gabarits `EXECUTOR-PROMPT-TEMPLATE.md` (A Haiku, B Sonnet, C Opus audit). Ce fichier ne modifie pas les index des vagues 1-12 ; le § « Changements aux cahiers antérieurs » liste ce que la vague 13 amende (W7 surtout).

**Incident fondateur (propriétaire, 2026-10-02)** : copie téléphone → TV figée, app « en vert », cause réelle = code PIN de la TV à ressaisir, rien à l'écran ne le disait. **Second cas (coordinateur, 2026-10-02)** : « Ouvrir avec CastBridge » depuis Telegram dit « Aucune TV ajoutée » alors qu'une TV est liée (état initial `NoTv` à froid, `S/TvLink.kt:104`) : correctif ciblé confié ailleurs ; W13 le classe et le fige par test.

**Modèle d'exécution par cahier** : `haiku` = tables de textes, docs, CI (mécanique, tout est donné) ; `sonnet` = le reste ; **audit Opus obligatoire** sur w13-01, w13-04, w13-05, w13-08 (authentification, PIN, verrou). Efforts en agent·jours (S ≈ 0,5-1, M ≈ 1,5-2).

**Prérequis** : vagues 1-3 fusionnées (`SafeFile` w1-02 souhaité pour `BlockerLog`, sinon écriture simple ; `routes.txt` w1-06). **Aucun cahier W4-W12 n'est requis** ; W13 tourne **avant W7** (aucun cahier W7 n'est lancé : vérifié, aucun rapport `w7-*`).

## Deux tranches ; fichiers disjoints à l'intérieur d'une tranche

| Tranche | Objet | Cahiers | Effort | Coût estimé (exécution) |
|---|---|---|---|---|
| **S1 — à livrer d'abord** | taxonomie `Blocker` + textes, chien de garde / pré-vol / `LinkHealth`, couche transfert sans échec muet, TV : codes + verrou + signal, téléphone : services, saisie PIN dans la carte, puce à trois vérités, tests catalogue + lint | w13-01, 02, 03, 04, 05, 07, 08, 09, 10 | ≈ 14,5 j → **≈ 10,5 j** en parallèle (3 groupes) | ≈ 12 $ + audit ≈ 3 $ |
| **S2** | écran TV (bandeau, D-pad), docs + liste terrain, CI | w13-06, 11, 12 | ≈ 2,5 j | ≈ 1,5 $ |

## Les 12 cahiers

| id | Cahier | Objet | Effort | Modèle | Audit Opus | Jauge (entrée / sortie, k jetons) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|
| w13-01 | `sonnet-w13-01-blocker-core.md` | `Blocker`, `BlockerCode` (47), `BlockerMap` (HTTP, exception, Bluetooth, pré-vol), `KNOWN_HTTP` | M | sonnet | **oui** | 250 / 15 | PRÊT | — |
| w13-02 | `sonnet-w13-02-blocker-texts.md` | `BlockerTexts` : table française figée (cause · action · bouton · puce) | S | haiku | non | 120 / 12 | PRÊT | w13-01 |
| w13-03 | `sonnet-w13-03-watchdog-preflight-health.md` | `TransferWatchdog`, `Preflight`, `LinkHealth` (3 vérités, état initial `UNKNOWN`), `BlockerLog`, `SupportCode` | M | sonnet | échantillon | 350 / 22 | PRÊT | w13-01 (w13-02 souhaité) |
| w13-04 | `sonnet-w13-04-transfer-layer-no-swallow.md` | `ResumableUpload/Download`, `TransferClient`, `Lanes`, `ResilientCall`, `LinkText.http` ⇒ `BlockerMap` ; fin de la boucle 403 ; repli jeton après PIN refusé | M | sonnet | **oui** | 450 / 25 | PRÊT | w13-01, w13-02 |
| w13-05 | `sonnet-w13-05-tv-server-codes-pin-signal.md` | TV cœur : champ `code` des corps d'erreur, PIN absent non compté, `onAuthRefused`, `PinNeededPolicy`, `BlockerLogTv`, `/api/link/blockers`, `routes.txt` | M | sonnet | **oui** | 400 / 18 | PRÊT | w13-01 |
| w13-06 | `sonnet-w13-06-tv-screen-pin-needed-dpad.md` | TV écran : bandeau « un téléphone attend le code », puce d'accueil, page D-pad « Derniers blocages », réglage | S-M | sonnet | non | 300 / 14 | PRÊT | w13-05 |
| w13-07 | `sonnet-w13-07-phone-upload-wiring.md` | téléphone : `UploadService`/`BtUploadService`/`TransferQueue*` branchés (chien de garde, `BlockerState`, notification « Envoi bloqué » + Réparer, lien profond, repli jeton, `QUEUE_NO_LINK`) | M | sonnet | échantillon | 450 / 22 | PRÊT | w13-03, w13-04 |
| w13-08 | `sonnet-w13-08-phone-pin-prompt-repair.md` | téléphone : `PinPrompt` dans la carte, `PinStore.putAll` (toutes les clés), `RepairSheet`, `castbridge://repair` | M | sonnet | **oui** | 350 / 18 | PRÊT | w13-07 |
| w13-09 | `sonnet-w13-09-phone-chip-three-levels.md` | téléphone : `TvLinkManager.health`, `HealthProbe`, puce à trois vérités dans `TvHome`/`TvLinkStatus`/`TvScreen`, `BlockerBanner` dans toutes les cartes, F2 corrigée, `OpenWithActivity` sur `UNKNOWN` | M | sonnet | non | 450 / 22 | PRÊT | w13-07, w13-08 |
| w13-10 | `sonnet-w13-10-tests-catalogue-lint.md` | `BlockerCatalogueTest` (≥ 39 cas, faux serveur scripté), `RouteStatusLintTest`, `NoSilentFailureSourceTest` | M | sonnet | non | 400 / 25 | PRÊT | w13-01, w13-04, w13-05 |
| w13-11 | `sonnet-w13-11-docs-field-checklist.md` | `docs/BLOCAGES.md` (catalogue, codes support, liste terrain `adb`/`curl`), TRANSFER/ADMIN/HANDOFF/CHANGELOG | S | haiku | non | 150 / 15 | PRÊT | S1 fusionnée |
| w13-12 | `sonnet-w13-12-ci-source-checks.md` | `check_w13_sources.sh`, test Python, workflows | S | haiku | non | 100 / 8 | PRÊT | S1 fusionnée |

Coûts (prix 2026-09 : haiku 1/5, sonnet 2/10, opus 4/20 $/M, jauges **estimées, non vérifiées**) : sonnet ≈ 9 × (0,8 + 0,2) ≈ 9-10 $ ; haiku ≈ 3 × 0,2 ≈ 0,6 $ ; audits Opus 4 × (150 k / 10 k) ≈ 3,2 $. **Total ≈ 13-15 $.**

## Matrice de propriété (preuve de disjonction par tranche)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`.

### S1
| id | Fichiers possédés |
|---|---|
| w13-01 | nouveaux `C/link/{Blocker,BlockerMap}.kt`, `CT/link/BlockerMapTest.kt` |
| w13-02 | nouveaux `C/link/BlockerTexts.kt`, `CT/link/BlockerTextsTest.kt` |
| w13-03 | nouveaux `C/link/{TransferWatchdog,Preflight,LinkHealth,BlockerLog,SupportCode}.kt`, `CT/link/{TransferWatchdogTest,PreflightTest,LinkHealthTest,BlockerLogTest,SupportCodeTest}.kt` |
| w13-04 | `C/tv/TvClient.kt`, `C/xfer/{TransferClient,Lanes,Lane,Scheduler}.kt`, `C/trust/{ResilientCall,LinkText}.kt` ; nouveau `CT/link/TransferBlockerTest.kt` |
| w13-05 | `C/tv/{ReceiverServer,Security}.kt`, `CT/SecurityTest.kt`, `tools/routes/routes.txt` ; nouveaux `C/link/{PinNeededPolicy,BlockerLogTv}.kt`, `CT/link/PinNeededPolicyTest.kt`, `CT/tv/ErrorCodesTest.kt` |
| w13-07 | `S/{UploadService,BtUploadService,TransferQueue,TransferQueueService}.kt` ; nouveaux `S/link/{BlockerState,PreflightAndroid,BlockerNotifications,BlockerLogPhone}.kt` |
| w13-08 | `S/PinStore.kt`, `S/MainActivity.kt` (intent `repair`), `android/sender/src/main/AndroidManifest.xml`, `android/sender/src/main/res/xml/{backup_rules,data_extraction_rules}.xml` ; nouveaux `S/link/{PinPrompt,RepairSheet,RepairDeepLink}.kt` |
| w13-09 | `S/{TvLink,LinkAndroid,TvHome,TvPairScreen,TvScreen,TvHub,TvTransferScreen,DlnaHandoff,OpenWithActivity}.kt`, `S/player/CastSheet.kt` ; nouveaux `S/link/{BlockerBanner,HealthProbe}.kt`, `CT/link/LinkHealthSourceTest.kt` |
| w13-10 | nouveaux `CT/link/{BlockerCatalogueTest,ScriptedTv,RouteStatusLintTest,NoSilentFailureSourceTest}.kt` |

Ordre interne de S1 (contrats) : **groupe a** w13-01 → w13-02 ∥ w13-03 ; **groupe b** (après a) w13-04 ∥ w13-05 → w13-10 ; **groupe c** (après b) w13-07 → w13-08 → w13-09. `S/TvLink.kt` : si le correctif ciblé « état initial » (hors W13) n'est pas encore fusionné à l'arrivée de w13-09, w13-09 ne touche que la zone `health` et le signale.

### S2
| id | Fichiers possédés |
|---|---|
| w13-06 | `R/{TvService,HomeScreen,TvPrefs,PlayerActivity}.kt` (zones nommées) ; nouveaux `R/{BlockersPanel,PinNeededNotifier}.kt` |
| w13-11 | nouveau `docs/BLOCAGES.md` ; `docs/{TRANSFER,ADMIN,HANDOFF,CHANGELOG}.md` |
| w13-12 | nouveaux `tools/checks/check_w13_sources.sh`, `tools/tests/test_check_w13_sources.py` ; `.github/workflows/{tools,android}.yml`, `docs/COORDINATION.md` (§ CI) |

Vérification de disjonction : aucun chemin n'apparaît deux fois dans une même tranche ; `C/tv/ReceiverServer.kt` : w13-05 seul ; `S/TvLink.kt` : w13-09 seul (zone `health`) ; `S/UploadService.kt` : w13-07 seul ; `S/PinStore.kt` : w13-08 seul ; `tools/routes/routes.txt` : w13-05 seul.

Lignes `À BRANCHER` attendues à la fusion S1 (posées par le coordinateur, une ligne chacune) : `TvHome`/`TvScreen`/`CastSheet` ⇒ `PinPrompt` et `RepairSheet` (w13-09 → w13-08) ; `MainActivity` ⇒ onglet + `RepairSheet` sur lien profond (w13-08 → w13-09) ; `UploadService.retryWithCredential` appelé par `PinPrompt.onDone` (w13-08 → w13-07).

## Graphe de dépendances

```
w13-01 ─┬─► w13-02 ─┐
        ├─► w13-03 ─┼─► w13-04 ─┬─► w13-07 ─► w13-08 ─► w13-09
        └─► w13-05 ─┘           └─► w13-10
S1 fusionnée ─► w13-06 ∥ w13-11 ∥ w13-12
```

## Ordre de lancement conseillé

1. **Jour 0** : w13-01 (sonnet) ; audit Opus de w13-01 à son rapport. Propriétaire : fournir le logcat de l'incident (`adb logcat -s UploadService:I TvLink:I TransferQueue:I`) et l'écran utilisé (B-W13-1) : cela choisit le **premier cas terrain** à rejouer.
2. **Jour 1** : w13-02 (haiku), w13-03, w13-05 en parallèle ; audit Opus de w13-05.
3. **Jour 3** : w13-04, puis audit Opus ; w13-10 dès w13-04/05 fusionnés. **Fusion b** : `gradle --offline :core:test` complet ; `python3 -m unittest tools.tests.test_routes` ; `grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/link/` vide.
4. **Jour 5** : w13-07 → w13-08 (audit Opus) → w13-09 ; **fusion S1** : lignes `À BRANCHER`, `:sender:compileDebugKotlin`, `:core:test` ; **sur le téléphone du propriétaire + TV de référence (32 bits)** : rejouer le cas de l'incident (faux PIN ⇒ carte « code à saisir », ressaisie dans la carte, envoi repart), 403 d'essai (plus de boucle), clé retirée, TV éteinte ; copie de l'APK TV dans le `Download` de la clé USB (règle du propriétaire) ; builds TV verrouillés seulement.
5. **Jour 8** : w13-06 (sonnet), w13-11, w13-12 (haiku) ; mise à jour `HANDOFF.md` § 0 et § 9 **dans le même commit** que la fusion.
6. Après la vague : W7 (ses cahiers lisent le § « Changements » ci-dessous), puis w6-17 (`SendGuard`) qui branche `Blocker.ofGateRefusal`.

## Changements aux cahiers antérieurs (sans les éditer ; l'exécutant d'un cahier encore à lancer lit ceci d'abord)

| Cahier | Changement | Repris par |
|---|---|---|
| **w7-08** (`LinkJournal`, `SelfTest`, `LinkTexts`) | `LinkJournal` **absorbe** `BlockerLog` (kind `BLOCKER`, même export redigé) ; `SelfTest.Verdict` porte un `Blocker?` et réutilise `Preflight` pour les sondes LAN ; `LinkTexts` n'écrit **aucun** texte pour un code `Blocker` (source : `BlockerTexts`) | w13-03, w13-02 |
| **w7-19** (« Réparer la connexion ») | `RepairScreen` reçoit `BlockerState.last` et rejoue `Preflight` ; `RepairSheet` (w13-08) en est la première version : étendre, ne pas dupliquer | w13-08 |
| **w7-20** (`LinkChip`, `ConnectionScreen`) | la **couleur** de la puce vient de `TvLinkManager.health` (trois vérités, fraîcheur) ; le texte de `LinkText`/`BlockerTexts.chip` ; `ConnectionScreen` affiche le code support | w13-09, w13-03 |
| **w7-21** (`XferCard`, file persistée, `UploadService`, `TransferQueue`) | W13 modifie d'abord `UploadService`, `BtUploadService`, `TransferQueue*` : w7-21 se **rebase** et conserve `BlockerState`, la notification « Envoi bloqué » et le repli jeton ; `XferCard` inclut `BlockerBanner` | w13-07, w13-09 |
| **w7-10** (`TransferLedger`) | une entrée du grand livre porte `blocker: String?` (wire) et le code support | w13-03 |
| **w7-15** (`/api/link/*`, `LinkDiagActivity`) | `/api/link/journal` inclut `BlockerLogTv` ; `LinkDiagActivity` reprend `BlockersPanel` | w13-05, w13-06 |
| **w7-12 / w7-14** (`TvService`, `HomeScreen`, `PlayerActivity`) | conserver le crochet `onAuthRefused` → `PinNeededNotifier`, la mention « un téléphone attend le code » de la puce, le réglage `pin_needed_banner` | w13-05, w13-06 |
| **w7-16** (`LinkRuntime`, façade `TvLinkManager`) | la façade garde `health` et `credentialForBase` ; `LinkHealth.initial(hasSavedTv)` reste l'état publié avant le premier pas | w13-09 |
| **w7-17** (lien profond `castbridge://tv`) | même schéma ; hôte `repair` déjà pris par w13-08 : ajouter `tv` à côté, un seul `intent-filter` par hôte | w13-08 |
| **w6-17** (`SendGuard`, chemins de données) | les refus **avant** envoi restent `PhoneGateTexts` ; brancher `Blocker.ofGateRefusal(id)` (contrat § 5.1 de la conception) pour un seul bandeau ; M-TV-REFUSED pendant l'envoi = `Blocker` W13 | w13-01 |
| **w2-09** (`TvReachability`) | déjà remplacé par W7 ; W13 n'y touche pas | — |
| **w8-07** (`BulkTransfer.Result.Refused`, abandonnée) | si W8 est relancée : `Refused` ⇔ `Blocker` `FATAL/ACTION`, `CHUNK_ACK` 6 ⇔ `TRIAL_CLOSED` | — |

## Décisions prises par l'architecte (renversables ; détail § 6 de la conception)
D-W13-1 saisie du PIN **dans la carte du transfert** (plus d'assistant muet) ; D-W13-2 la TV ne compte plus une requête **sans secret** dans le verrou ; D-W13-3 bandeau TV « un téléphone attend le code » activé par défaut, limité (1/60 s/pair, 3/10 min), sans identité, désactivable ; D-W13-4 PIN dans les préférences privées existantes, aucune dépendance ; D-W13-5 code support affiché des deux côtés ; D-W13-6 W13 **avant** W7, tranche S1 d'abord ; D-W13-7 PIN enregistré sous **toutes** les clés de la TV, ancien PIN effacé sur `PIN_WRONG` confirmé.

## Questions au propriétaire (une seule, non bloquante pour la conception)

| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **B-W13-1** | Logcat et écran exact de l'incident (« Transfert rapide » coché ? envoi depuis TvHome, Avancé, lecteur ou partage ?) | le **premier** cas terrain à rejouer à la fusion S1 (pas les cahiers) | fournir `adb logcat -s UploadService:I TvLink:I TransferQueue:I` du téléphone au jour 0 |
