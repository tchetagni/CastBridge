# Conception W14 — Barrière anti-régression : aucune APK remise au propriétaire, aucune fusion touchant liaison/transfert/confiance/UI, sans parcours rejoués

> Document de conception (Fable, architecte, 2026-10-02). **Aucun code n'est modifié par ce document** ; l'exécution se fait par les cahiers `docs/agent-briefs/sonnet-w14-NN-*.md` (index : `SONNET-WAVE14-INDEX.md`), sur ordre explicite du coordinateur. Branche de référence : `integration/agents`. Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`. Lecture seule sur le dépôt : tout fichier cité a été vérifié le 2026-10-02 ; aucune commande `gradle`/`adb` n'a été lancée.
>
> **Signal du propriétaire (2026-10-02)** : « Il y a trop de régression. » Cinq régressions de terrain en un jour sur ses vrais appareils (S21+ Android 14 ; TV GaiaOS 32 bits ; LAN 192.168.0.x) : **R-01** copie figée, puce verte, PIN à ressaisir sans message ; **R-02** « Ouvrir avec CastBridge » dit « Aucune TV ajoutée » (Copier grisé) alors que la TV est connectée par le code ; **R-03** « téléphone de confiance » cassé (TV réinstallée ⇒ registre perdu ; « Réassocier » supprimait la TV avant que le nouvel appairage réussisse) ; **R-04** aucune progression sur la TV pendant une copie, écran figé sur « Prêt à recevoir » (vu deux fois) ; **R-05** plus tôt : échecs de `connect()` Bluetooth à l'activation, activation non propagée au téléphone, Bluetooth lent, dialogues de permission en boucle. **Constat commun** : ~2 100 tests JVM du cœur verts, **0** test dans `:sender` et `:receiver`, **0** `androidTest`, la CI ne lance que `:core:test :sshd:test` (`.github/workflows/android.yml`), et les agents du jour n'ont vérifié que la **compilation** du code d'écran. Rien ne rejoue un **parcours** téléphone + TV avant la remise d'une APK.

## 0. En dix lignes

1. **Cause racine des régressions** : les parcours vivent **entre** le téléphone et la TV (secret présenté, état de liaison, progression des deux côtés), et aucune couche de test ne les traverse ; les décisions d'état des écrans (`reachable`, `LinkUi.NoTv`, « Aucune TV ») sont prises **dans les écrans Android**, donc hors de portée des tests JVM (§ 1.2).
2. **Barrière à trois étages**, du moins cher au plus cher : **(J)** parcours **JVM** en processus (vraie `ReceiverServer` + vraie `LinkDriver`/`FakeTv` Bluetooth + vrai `TransferClient`, horloge injectée) : 31 des 36 parcours critiques, < 2 min, en CI ; **(F)** suite de **fumée scriptée** `tools/smoke/` contre la TV émulée (`cbconnect_tv`, arm64) ou la **fausse TV du Mac** et le téléphone (émulé ou réel par `adb`) : 21 parcours, < 10 min, rapport PASS/FAIL d'une page ; **(H)** liste **humaine** de 12 points en 15 min sur la vraie TV, pour ce que ni J ni F ne prouvent (Bluetooth réel, GaiaOS 32 bits, clé USB, dialogues Samsung).
3. **36 parcours critiques** figés avec préconditions, étapes, résultat observable **sur les deux appareils**, gravité et lieu de vérification : `docs/test-plans/PARCOURS-CRITIQUES.md`. Chacune des cinq régressions a son parcours (R-01 → P-07/P-08, R-02 → P-22/P-24, R-03 → P-02/P-04, R-04 → P-11/P-36, R-05 → P-25/P-26/P-35).
4. **Règle de pureté** : toute décision d'état visible (puce, « Aucune TV », boutons actifs, texte de notification, règle de rétrogradation) est une **fonction pure du cœur** (comme `SendChoices.decide` l'est depuis le correctif R-02) ; un **test lint** (`CT/lint/UiStatePurityTest`) lit les sources de `:sender`/`:receiver` et **échoue** quand un écran décide lui-même (§ 3.4).
5. **Fumée sans toucher la TV du propriétaire** : la fausse TV du Mac (`FakeTvMain`, scénarios « neuve / réinstallée / essai / pleine / lente / PIN changé ») et la TV émulée sont **sacrifiables** ; la vraie TV n'est jamais touchée qu'en **lecture seule** (`GET /api/*`, `cbdev status`, `/api/screenshot`) par le relais `nc` du téléphone.
6. **Discipline de version** : voies **beta** (ce qui est aujourd'hui) et **stable** (après 1.0.0) ; étage **candidate** (`-rc.N` dans le nom, `versionCode` déjà réservé) installé **seulement** sur l'émulateur puis sur le téléphone du propriétaire, **jamais** sur la TV avant la liste H ; test de **migration en place** scripté (installer N-1, peupler confiance/PIN/activation/bibliothèque, installer N, comparer) ; **retour arrière** = APK précédente gardée sur la clé + republication sous nouveau code (la TV refuse `code <` en release : `R/UpdateInstaller.kt`).
7. **Journal des régressions** `docs/REGRESSIONS.md` (symptôme, cause, test ajouté, version) amorcé avec R-01…R-05 ; règle « **tout défaut terrain commence par un test rouge** » (aucun correctif fusionné sans le test qui échouait avant lui).
8. **Processus agents** : porte par type de cahier (exécutant : porte étroite + parcours J de sa zone ; coordinateur : J complet + F avant fusion ; Opus : audit du harnais et de tout diff `C/trust`, `C/xfer`, `C/tv/ReceiverServer`) ; **interdiction** de fusionner un cahier touchant un écran sans rapport F ; **au plus 2** cahiers risqués (liaison/confiance/transfert) en parallèle ; W14 **avant** W13 et W7, et W13/W7 reçoivent chacun un parcours J obligatoire par cahier.
9. **Effort** : 15 cahiers, ≈ 18 agent·jours, ≈ 20 $ d'exécution + ≈ 4 $ d'audit (estimés) ; **première tranche** (6 cahiers, ≈ 8 j) = harnais J + 20 premiers parcours + pureté/lint + journal : c'est elle qui aurait attrapé R-01, R-02, R-03 et la moitié de R-04.
10. **Décisions** : 8 prises avec recommandation (§ 7), 3 pour le propriétaire (émulateur TV arm64 accepté comme substitut 32 bits ? ; qui exécute la liste H ; `-rc` sur le nom ou build séparé), **1 BLOQUÉ** (B-W14-1 : le téléphone réel sert-il à la fumée automatique, avec ses données, ou seulement l'émulateur ?).

## 1. Pourquoi les régressions passent (analyse)

### 1.1 Les cinq cas, et le test qui les aurait vus

| R | Symptôme terrain | Cause (code, vérifiée dans les rapports et commits du jour) | Couche qui l'aurait vu | Parcours |
|---|---|---|---|---|
| R-01 | copie figée, puce verte, PIN à ressaisir | la puce sonde avec le **jeton** (`C/trust/LinkDriver.kt:174-181`), le transfert part avec `PinStore.get(clé)` qui rate la TV de confiance (`S/TvLink.kt:191-198`) ⇒ ancien PIN ⇒ 401 avalé (`S/UploadService.kt:255-265`, `C/xfer/TransferClient.kt:38-43`) ; DESIGN-W13 § 1 | **J** : un parcours « TV réinstallée, PIN tourné, copie » exige **un message et une notification** à la fin ; il n'existait pas parce que `UploadService` (Android) tient la notification et le choix du secret | P-07, P-08, P-36 |
| R-02 | « Aucune TV ajoutée », Copier grisé, TV connectée par le code | `OpenWithActivity` ne lisait que la liaison de confiance (`LinkUi.NoTv`) ; l'accueil était vert par le chemin PIN (`S/TvHome.kt:125-182`) ; corrigé par la fonction pure `SendChoices.decide` (commit 9f6777f/6885704) | **J** + **lint** : deux écrans décidaient de l'état avec deux sources ; une seule fonction pure alimentée par **tous** les faits (registre + chemin PIN) le rend impossible ; le test de table existe maintenant (`CT/SendChoiceTest.kt`) mais les faits sont écrits à la main : le harnais doit les **fabriquer** depuis un vrai état | P-22, P-23, P-24 |
| R-03 | téléphone de confiance cassé ; « Réassocier » détruit la TV | TV réinstallée ⇒ `trusted_phones.txt` perdu + PIN changé ; `forget(address)` avant que l'appairage réussisse (corrigé : `LinkDriver.prepareReassociate`, commit cf47cc0) ; code du refus non journalisé | **J** : `FakeTv.reinstall()` existe déjà dans `CT/LinkFixtures.kt` ; il manquait le parcours « réinstallée → Réassocier → abandon → la TV reste » ; **F/H** : règle « mise à jour par-dessus, jamais désinstaller » non écrite dans la liste de livraison | P-02, P-03, P-04 |
| R-04 | TV sans progression, « Prêt à recevoir » figé | TV : statut `1-bt` = chaîne écrasée (`R/BtServer.kt:113-127`, W7 § 2 n° 7) ; LAN : la carte de réception dépend de `TvService` (Android), aucune assertion sur l'état TV pendant une copie | **J** : asserter `TransferHost.stateJson`/`/api/transfer/state` **pendant** la copie (côté TV) et `onProgress` (côté téléphone) ; **F** : `/api/screenshot` pendant la copie + `dumpsys notification` | P-11, P-13, P-36 |
| R-05 | `connect()` BT en rafale, activation non propagée, BT lent, permissions en boucle | `OwnerBtHost` seulement avec `TvService` ; `ActivateTvActivity` lit `/api/activation` LAN seulement écran ouvert ; `onResume` redemande (DESIGN-W7 § 2) | **J** : `ActivationScreenState.view` sur une `/api/activation` réelle ; **F** : compter les dialogues (`uiautomator dump` en boucle) ; **H** : Bluetooth réel | P-25, P-26, P-35 |

### 1.2 Les trois trous structurels

1. **Aucun test ne traverse le couple** : `CT/LinkDriverTest` teste la liaison avec `FakeEnv` (sans HTTP) ; `CT/TransferTest`/`TrustTest` testent `ReceiverServer` avec un client ad hoc ; personne ne branche `LinkEnv.probe/check` sur une vraie `ReceiverServer`, ni `TransferClient` sur une session issue de `LinkDriver`. Tout le nécessaire existe pourtant en JVM pur (`ReceiverServer` sur port libre : `CT/TrustTest.kt:351` ; `FakeTv` Bluetooth sur `PipedStreams` ; `TransferHost` sans socket ; `FakeClock`).
2. **Les décisions d'état sont dans les écrans** : `S/TvHome.kt:137-197` (`reachable = r.isSuccess`, « Connectée »), `S/TvScreen.kt:61,113-115` (`reachable`, `badPin`), `S/TvPairScreen.kt:65-66,294`, `S/ActivateTvActivity.kt:63` (« Aucune TV n'est ajoutée… »), textes de notification `"$text · $pct %"` en dur dans `S/UploadService.kt:272`, `S/TransferQueueService.kt:59`, `S/BtUploadService.kt:179`, `S/DownloadService.kt:154`. Seule `OpenWithActivity` délègue (`SendChoices.decide`). Tant que c'est dans les écrans, c'est **non testable** sans appareil, et chaque agent qui « compile » croit avoir fini.
3. **La remise au propriétaire n'a pas de porte** : `docs/RELEASES.md` § 7 demande « parcours de base » sans liste ; `tools/release/check_versions.py` vérifie les numéros, pas les parcours ; la clé USB reçoit l'APK avant tout essai ; la version précédente n'est pas désignée comme « retour arrière ».

## 2. Étage F — suite de fumée scriptée `tools/smoke/`

### 2.1 Cibles et modes

| Mode | TV | Téléphone | Ce que ça prouve | Ce que ça ne prouve pas |
|---|---|---|---|---|
| `--tv fake` (**par défaut**, CI possible) | **Fausse TV du Mac** : `FakeTvMain` (cahier w14-10) = `ReceiverServer` + `TrustRegistry` + `TransferHost` + extension `/api/activation` simulée, sur `127.0.0.1:<port>`, **scénarios** `fresh`, `reinstalled` (nouveau `installId`, nouveau PIN), `trial` (`TrialPolicy`), `full` (507), `slow` (écriture 300 Ko/s), `pin-rotated`, `busy` (429) ; HELLO Bluetooth rejoué **par TCP** (`FakeTv.serve` sur socket au lieu de RFCOMM, port `+1`) | émulateur `cbconnect_phone` (API 35 arm64) avec `adb reverse tcp:8765 tcp:<port>` ⇒ l'app voit la TV en `127.0.0.1:8765` ; ou téléphone réel (B-W14-1) | toute la logique téléphone (services, notifications, écrans, « Ouvrir avec », PIN, file) contre une TV **au comportement choisi**, y compris les cas destructeurs | la vraie app TV, le Bluetooth réel |
| `--tv emu` | émulateur `cbconnect_tv` / `ETV_TV_API34` (Android TV 34, **arm64-v8a** : l'APK TV contient la tranche arm64 ; `splits.abi` dans `receiver/build.gradle.kts`) ; APK **verrouillée** de test (`-PrequireActivation=true -PtrustedKeysFile=<clés de TEST>`) ; activation de TEST comme `tools/rental-test` l'a prouvé | idem | la vraie app TV : routes, `TvService`, rangement, écrans TV (`/api/screenshot`, `uiautomator dump` côté TV), redémarrage, migration en place | 32 bits, Bluetooth (émulateur sans RFCOMM ⇒ chemin PIN + HELLO TCP seulement), GaiaOS |
| `--tv real-ro` | **vraie TV, lecture seule** par le relais (`adb forward tcp:18766 tcp:9876` + `nc` sur le téléphone, mémoire « Relais ») : `GET /api/hello`, `/api/info`, `/api/activation`, `/api/library`, `/api/transfer/state`, `/api/apk`, `/api/connections`, `/api/screenshot`, et `ssh -p 2223 cbdev status` | téléphone réel | état réel de la TV **avant** et **après** une livraison (versions installées, badge, bibliothèque intacte, aucune session de transfert orpheline) | rien d'actif : **aucun** POST, aucune installation, aucun PIN faux (verrou), aucune réinstallation |

**Décision D-W14-3** : la TV du propriétaire n'est **jamais** une cible active d'un script. Ce qui doit casser casse sur `fake` ou `emu`.

### 2.2 Vérifications (ce que chaque pas affirme)

| Famille | Commande / source | Assertion | Parcours |
|---|---|---|---|
| Installation | `adb -s <phone> install -r <apk>`, `aapt2 dump badging` | `versionCode` attendu ; signature identique à l'installée (`apksigner verify --print-certs` vs `pm dump`) ; **jamais** `pm uninstall`/`pm clear` sur le téléphone réel | P-29 |
| HTTP TV | `curl` (fausse/émulée : direct ; réelle : relais) | statut et champs (`/api/hello` `{"app":"castbridge-tv","v":…}` ; `/api/activation` `locked`,`label` ; `/api/transfer/state` ; `/api/library` contient le fichier) | P-05, P-11, P-25, P-31 |
| Logcat | `adb logcat -d -s CB_JOURNEY:I UploadService:I TvLink:I TransferQueue:I CastBridgeTV:I` | **marqueurs structurés** `CB_JOURNEY <étape> <clé=valeur…>` (cahier w14-11 : `link.step`, `pin.prompt`, `xfer.begin/progress/finish/blocked`, `openwith.decide`, `activation.view`) ; aucun `FATAL EXCEPTION`, aucun `ANR` dans `castbridge.*` | tous |
| Notifications | `adb shell dumpsys notification --noredact` | une notification `pkg=castbridge.sender` avec `title=<fichier>`, `text=… NN %`, `progress` croissant entre deux lectures à 5 s ; à la fin : « Terminé » ou « Envoi bloqué : … », **jamais** disparition sans texte final (marqueur `xfer.finish`/`xfer.blocked`) | P-36 |
| Écran téléphone | `adb shell uiautomator dump /sdcard/ui.xml` + XPath | textes attendus présents/absents (« Connectée », « Aucune TV ajoutée » **absent** si une TV est connue, « Copier » `enabled="true"`), compte de dialogues `GrantPermissionsActivity` ≤ 1 par type | P-05, P-22, P-24, P-35 |
| Écran TV | émulée : `adb -s <tv> shell uiautomator dump` ; réelle : `GET /api/screenshot` (PNG des fenêtres CastBridge-TV seulement) | pendant une copie : texte de la carte de réception (émulée) ou **différence d'image** > seuil entre « Prêt à recevoir » et « en cours » (réelle, lecture seule) ; après : retour à « Prêt à recevoir » | P-11, P-36 |
| Fichiers | `GET /api/library`, `adb -s <tv> shell ls` (émulée) | fichier présent, taille exacte, rangé dans sa catégorie, `.cbx` absent après `finish` | P-11, P-12, P-15 |
| Migration | `tools/smoke/migrate_test.py` (w14-13) | après N-1 → N : mêmes téléphones de confiance, même PIN accepté, même activation, même bibliothèque, mêmes locations | P-28, P-29 |

### 2.3 Budget, sorties, prérequis

- **Budget < 10 min** : démarrage émulateurs (déjà lancés : 0 ; à froid : 90 s), installation 2 APK (30 s), 14 pas de fumée (≈ 5 min dont copie de 256 Mio ≈ 1 min sur émulateur), migration (2 min). Chaque pas a un `timeout` ; dépassement = FAIL avec le pas.
- **Sortie** : `tools/smoke/out/<horodatage>/REPORT.md` (une page) : versions (APK, commit `git rev-parse --short HEAD`, arbre propre ou `-dirty`), mode, **PASS/FAIL** par parcours, et pour le **premier** FAIL : le pas, la commande, la **première preuve** (extrait logcat de 20 lignes, `ui.xml` filtré, capture PNG, JSON de la route) ; dossier `evidence/`. Code de sortie 0/1. Le rapport est **joint** à la livraison et cité dans `docs/HANDOFF.md` § 0.
- **Prérequis** : SDK Android local (`ANDROID_HOME`), AVD `cbconnect_tv` + `cbconnect_phone` (existent), APK de la candidate (TV verrouillée + clés de TEST pour `emu`), `~/CastBridge-release/` pour l'APK N-1 (migration), `tools/activation-desktop` (clé d'activation de TEST), Python 3.12 (stdlib seulement : `subprocess`, `xml.etree`, `json`, `urllib`). **Rien** du dépôt ne contient de secret : PIN de la fausse TV généré, affiché dans le rapport comme `******`.
- **Partage des émulateurs entre sessions** (`docs/HANDOFF.md` § 10) : le script prend un port de console à lui (`-port 5580` pour la TV, `5582` pour le téléphone, `emulator-5580` cité dans la mémoire) et refuse de démarrer si un autre `smoke.py` tient le verrou `tools/smoke/.lock` (même principe que `tools/agents/gradle-lock.sh`).

## 3. Étage J — couche de parcours JVM dans le cœur

### 3.1 Harnais (`CT/journey/`, cahier w14-01, audit Opus)

```
JourneyKit                          (DSL : given/when/then, horloge, journal des pas)
├─ JourneyClock : FakeClock (existant CT/LinkFixtures.kt) partagé par TvSim, PhoneSim, TransferHost(now=)
├─ TvSim        : une "TV" complète en processus
│    dir temporaire (volumes), TrustRegistry(FileTrustPersistence(dir), now=clock), PinGuard(pin),
│    TransferHost(now=clock), ReceiverServer(volumes, FakePlayer, port=0, pin, guard, tokenAuth=reg::verifyToken,
│      routeGuard = scenario.trial ? TrialPolicy::guard : null, extension = ActivationApiSim(state), …)
│    FakeTv (Bluetooth : HelloHandler + PairingSession + BtProtocol.serve sur PipedStreams, existant)
│    actions : start(), stop(), restart() (même dir ⇒ reprise), reinstall() (dir neuf : registre et PIN neufs),
│              rotatePin(), setTrial(), fill(freeBytes), slow(bytesPerSec), lanes vivantes, stateOf(name)
│    observations : receivedFiles(), transferState(id) (= /api/transfer/state), notices (onNotice), pinFailures
├─ PhoneSim     : le "téléphone" sans Android
│    Phone (existant : LinkDriver + SavedTvs + MemoryLinkStore + FakeEnv) dont env.probe/check appellent
│      la VRAIE ReceiverServer de TvSim en HTTP (TvClient.info avec jeton) — c'est le branchement manquant
│    PinStore pur (Map clé→code ; même sémantique de clés que S/PinStore.kt, extraite en C/trust/PinKeys par w14-05)
│    SendFacts.from(phone, pinStore, tvSim) : fabrique les faits RÉELS pour SendChoices.decide (plus de faits à la main)
│    UploadSim : ResumableUpload/TransferClient(HttpTransferApi(base, credential)) + XferTexts (w14-05) ⇒ notification simulée
│      (titre, texte, progress) et carte ; journal des transitions (Uploading/Waiting/Failed/Done + blocker)
│    actions : addTvViaPairing(), reassociate(), abandonReassociate(), enterPin(code), share(file) (= Ouvrir avec), send(file), cancel()
│    observations : chip() (LinkView), lastNotification(), card(), progressSamples()
└─ Scenario     : fichiers (small 5 Mio, big 64 Mio aléatoire déterministe), horloge, aléa, ordre des pas
```

Règles : aucun `Thread.sleep` réel (délais injectés : `sleep` de `TransferClient`, `LinkDriver.minGapMs`, `FakeClock.advance`) ; un test ≤ 3 s ; ports 0 ; dossiers temporaires ; `ReceiverServer` utilise `System.currentTimeMillis` en interne (pas d'horloge injectée : `TrustRegistry`, `TransferHost`, `PhoneLink`, `LinkDriver` en acceptent une) : les tests d'expiration passent par `TrustRegistry(now=clock)` et n'exigent rien de `ReceiverServer` sur le temps (**limite connue**, § 8).

### 3.2 Les 20 premiers tests (cahiers w14-02, w14-03, w14-04)

| # | Test (`CT/journey/`) | Parcours | Pas | Échoue si |
|---|---|---|---|---|
| J-01 | `LinkJourneyTest.firstPairingGivesGreenChipAndToken` | P-01 | pairing → HELLO → `check` HTTP avec jeton | puce ≠ `GOOD` en ≤ 3 pas, ou `/api/info` ≠ 200 avec le jeton |
| J-02 | `…tvReinstalledShowsReinstalledNeverNoTv` | P-02 | `tv.reinstall()` → pas de boucle | la vue dit « Aucune TV » ou `NoTv` alors que `saved.list()` = 1 ; code de refus absent du journal |
| J-03 | `…reassociateThenAuthorizeKeepsSingleEntry` | P-02 | `reassociate()` → TV « Autoriser » | 0 ou 2 entrées ; ancien jeton réutilisé |
| J-04 | `…reassociateAbandonedKeepsTv` | P-04 | `reassociate()` → `abandon()` → relance | TV absente de `saved`, ou `SendChoices.decide` ≠ `QUEUE` |
| J-05 | `…phoneReinstalledKnownAddressNeedsNoAuthorize` | P-03 | `phone.wipe()` même adresse → HELLO | fenêtre TV ouverte ou vue « retiré » alors que `isTrusted` |
| J-06 | `PinJourneyTest.rightPinGreenAndStoredUnderAllKeys` | P-05 | `enterPin(ok)` | une des clés (nom, mDNS, `ip:port`, `ip`) sans code |
| J-07 | `…wrongPinOneAttemptVisibleMessage` | P-06 | `enterPin(bad)` | `pinFailures` > 1, ou puce `GOOD`, ou message vide |
| J-08 | `…rotatedPinBlocksCopyWithMessageAndNotification` | P-07 | `tv.rotatePin()` → `send(small)` | fin sans texte final (notification disparue) ; état `Failed` sans raison ; **ou** boucle > 3 essais |
| J-09 | `…missingPinAsksBeforeFirstByte` | P-08 | clé sans code → `send` | une requête part sans secret et compte dans `pinFailures` |
| J-10 | `…lockThenRightPinResumesAlone` | P-09 | 5 faux → bon code | 6ᵉ essai pendant le verrou ; pas de reprise à `retryAfter` |
| J-11 | `…tokenExpiryRenewsWithoutScreen` | P-10 | `clock.advance(13 h)` | vue d'erreur ; copie en cours abandonnée |
| J-12 | `TransferJourneyTest.lanSmallProgressBothSides` | P-11, P-36 | `send(small)` | `progressSamples` non monotone ; `tv.transferState` sans `received` croissant **pendant** ; fichier absent/taille fausse ; notification finale absente |
| J-13 | `…lanBigMultiLaneVerifiedAndFiled` | P-12 | `send(big)`, K=4 | SHA-256 faux ; `.cbx` restant ; pas rangé |
| J-14 | `…bluetoothLaneProgressAndMoveDisabled` | P-13 | `btOnly` → `send(small)` | `moveEnabled`, ou aucun % côté TV |
| J-15 | `…cancelCleansBothSides` | P-15 | `send(big)` à 30 % → `cancel()` | `.cbx` présent ; notification restante ; état TV ≠ aborted |
| J-16 | `…tvRestartResumesFromBlockMap` | P-16 | 30 % → `tv.restart()` → `clock.advance` | repart de 0 ; ou pause > 60 s sans texte ; ou doublon |
| J-17 | `…trialRefusedOnceNoLoop` | P-19 | `tv.setTrial()` → `send` | > 1 requête d'envoi après le 403 ; texte sans « essai » |
| J-18 | `…storageFullRefusedBeforeFirstByte` | P-20 | `tv.fill()` → `send(big)` | un octet envoyé ; texte sans « place » |
| J-19 | `OpenWithJourneyTest.decideFromRealStates` (table) | P-22, P-23, P-24 | pour chaque état **fabriqué** (8 lignes) → `share(file)` | `NO_TV` avec une TV connue ; Copier grisé avec TV connue ; > 1 sonde PIN par ouverture ; `PIN_UPLOAD` qui n'aboutit pas à un fichier reçu |
| J-20 | `ActivationJourneyTest.stateSeenByPhoneWithinOnePoll` | P-25 | `tv.activate(trialKeyOfTest)` → `ActivationScreenState.view(GET /api/activation)` | vue ≠ ESSAI ; après production ≠ PRODUCTION ; refus sans code de raison |

Compléments de la même tranche (w14-04) : fixtures d'anciens formats (`trusted_phones.txt`, `LinkStore`, `.filing`, coffre de location) lues par les classes actuelles (P-28/P-29 en JVM) ; `UpdateRules.mayInstall(installedCode, candidateCode, debug)` (P-30).

### 3.3 Ce que J ne peut pas voir et qui doit remonter dans le cœur (cahier w14-05)

| Décision aujourd'hui dans un écran | Fonction pure à créer (cœur) | Consommateur après w14-06 |
|---|---|---|
| `S/TvHome.kt:137-197` `reachable`, « Connectée » / « Recherche… », bascule `wizard` | `C/trust/HomeLinkView.decide(facts: HomeFacts): LinkView` (faits : dernier `info()` daté, 401 reçu, registre, chemin PIN) | `TvHome` |
| `S/TvScreen.kt:61,113-115,236` `reachable`, `badPin`, « TV injoignable » | `C/trust/ManualTvView.decide(...)` | `TvScreen` |
| `S/TvPairScreen.kt:65-66,93,294` `when(link){NoTv→…}` « Aucune TV ajoutée. » | `LinkStart.view` (existant) + `PairScreenView.decide` | `TvPairScreen` |
| `S/ActivateTvActivity.kt:63,95` « Aucune TV n'est ajoutée… » | `SendChoices`-like : `ActivateTargetView.decide(savedCount, pinTvName, link)` | `ActivateTvActivity` |
| `S/UploadService.kt:272`, `TransferQueueService.kt:59`, `BtUploadService.kt:179`, `DownloadService.kt:154` `"$text · $pct %"` | `C/xfer/XferTexts.notification(state): Notice(title, text, progress, final: Boolean)` (**une** source : jamais de disparition sans `final`) | les 4 services |
| `R/UpdateInstaller.kt` `a.code < installedCode() && !canForce` | `C/update/UpdateRules.mayInstall(installed, candidate, debug): Verdict` | `UpdateInstaller` |
| `R/BtServer.kt:113-127` statut `1-bt` écrasé | `C/xfer/ReceiveCard.of(transfers): Card` (nom, reçu/total, voie) alimentée par `TransferHost.stateJson` et les envois Bluetooth | `TvService`/`HomeScreen` (zone nommée ; W7 `TransferLedger` l'absorbera) |
| `S/PinStore.kt:22` clés multiples | `C/trust/PinKeys.keysOf(tv): List<String>` + `putAll` | `PinStore` (W13 w13-08 le réutilise) |

### 3.4 Règle de pureté et test lint (`CT/lint/UiStatePurityTest`, cahier w14-06)

Règle : **un écran ou un service Android ne décide pas d'un état de liaison, de transfert, d'activation ni d'un texte de notification ; il transmet des faits à une fonction du cœur et dessine son résultat.** Le test lit `android/sender/src/main/kotlin/**` et `android/receiver/src/main/kotlin/**` (source, comme `RouteStatusLintTest` de W13) et échoue si, **hors liste blanche** (`S/TvLink.kt`, `S/LinkAndroid.kt` : façade de boucle ; `S/link/*` W13), un fichier contient : `"Aucune TV` ; `LinkUi\.NoTv\s*->` suivi d'un texte littéral ; `reachable\s*=\s*` ; `isSuccess\s*\)\s*\{?\s*"` (texte décidé sur un `Result`) ; `· \$pct %` ou `"\$\{?pct\}? ?%"` ; `\.code\s*<\s*installedCode` ; `pin\.isEmpty\(\)\s*\)\s*\{?\s*[A-Za-z]+\s*=\s*"` (texte décidé sur un PIN vide). Chaque motif porte le nom de la fonction pure à appeler à la place. La liste blanche est **datée** et chaque entrée a une échéance (cahier qui la retire). Ce test tourne dans `:core:test` (lecture de fichiers relatifs au dépôt, comme `tools/routes` pour les routes).

## 4. Discipline de version et de remise

### 4.1 Voies et étage « candidate »

| Voie | Nom | Qui l'installe | Porte |
|---|---|---|---|
| **beta** (aujourd'hui) | `X.Y.Z-beta` ; TV verrouillée `-verrouillee` | propriétaire + testeurs | J + F + H |
| **stable** (après 1.0.0, décision propriétaire) | `X.Y.Z` | clients | J + F + H + campagne `docs/TEST-CAMPAIGN.md` |
| **candidate** (nouvel étage) | **même** `versionCode` que la future beta, nom `X.Y.Z-beta-rc.N` (`-Pcastbridge.versionName` seulement ; `version.properties` inchangé jusqu'à la promotion) | **émulateur** puis **téléphone du propriétaire** (`adb install -r`) ; **jamais** la TV de référence avant H | J + F ; H décide la promotion |
| **test** (existant) | `-test`, clés de TEST | émulateur | aucune remise |

Promotion : `rc.N` 12/12 à H ⇒ reconstruire **le même commit** sans suffixe (commit de numéro `chore(release)`), `check_versions.py --apk`, `SHA256SUMS`, copie dans `Download/` de la clé, tag. Si le propriétaire préfère ne pas rebuilder (D-W14-6) : la candidate elle-même est promue et le nom `-rc.N` reste visible dans « Version » (recommandé **non** : un seul nom par `versionCode`).

### 4.2 Test de mise à jour en place (migration)

`tools/smoke/migrate_test.py` (w14-13), sur `cbconnect_tv` + `cbconnect_phone` : (1) installer **N-1** (dernier APK de `~/CastBridge-release/`, même signature) ; (2) peupler : association de confiance (HELLO TCP), code PIN, activation de TEST, 2 fichiers reçus (rangés), 1 lot, 1 réglage ; (3) `empreinte_avant` = `/api/library` + `/api/activation` + liste des téléphones (`/api/connections`) + `ls` des dossiers de données (émulateur : `run-as`) ; (4) installer **N** par `adb install -r` **et** par `/api/update/install` (deux chemins) ; (5) `empreinte_après` ⇒ égalité champ à champ ; (6) rétrogradation N-1 ⇒ 409 attendu. En JVM (w14-04) : fixtures d'anciens formats de fichiers lues par les classes actuelles (une fixture par format, **jamais modifiée** : un changement de format ajoute une fixture et un chemin de migration).

### 4.3 Retour arrière

- **Règle** : l'APK **précédente** reste dans `Download/` de la clé (`CastBridge-TV-<N-1>…apk`) et dans `~/CastBridge-release/` ; on ne supprime une version de la clé qu'après deux livraisons réussies.
- **Rétrogradation refusée** (`R/UpdateInstaller.kt` : `code < installé` ⇒ 409 en release ; Android refuse aussi) ⇒ **revenir = republier l'ancienne source sous un nouveau `versionCode`** : `git worktree add ../cb-old tv-<N-1>` (ou `git archive`), build verrouillé avec `-Pcastbridge.versionCode=<N+2>` et nom `<N-1>-beta.1` (reconstruction documentée dans `docs/RELEASES.md` § 10/13 ; w14-12 écrit la procédure et le script `tools/release/rollback-plan.sh` en simulation par défaut).
- **Données** : une mise à jour n'efface rien (P-28) ; un retour arrière non plus (les formats de fichiers sont rétro-lisibles : fixtures w14-04). Si une version N a changé un format sans chemin de retour, la ligne REGRESSIONS le dit et le retour passe par un export (`/api/library`, téléphones de confiance) avant réinstallation : à éviter par construction.

### 4.4 Journal des régressions et règle « test rouge d'abord »

`docs/REGRESSIONS.md` (w14-14) : table `R-NN | date | symptôme (mots du propriétaire) | appareils/versions | cause racine (fichier:ligne, commit) | parcours (P-NN) | test ajouté (classe, rouge avant / vert après) | version corrigée | statut`. Amorcé avec R-01…R-05. **Règle** (dans `docs/COORDINATION.md` et les gabarits) : un cahier de correctif commence par le test J (ou, à défaut, F) **qui échoue** sur `integration/agents` ; le rapport de l'exécutant cite la sortie rouge puis verte ; le coordinateur refuse la fusion sans ces deux sorties.

## 5. Processus des agents

| Type de cahier | Exécutant doit faire tourner | Coordinateur avant fusion | Opus audite |
|---|---|---|---|
| cœur pur hors liaison/transfert/confiance (`C/learn`, `C/quiz`, `C/lots`…) | porte étroite du cahier + `:core:test --tests 'castbridge.core.journey.*'` | `:core:test` complet + lint | si C = 2 (routage existant) |
| **liaison / confiance / transfert / TV serveur** (`C/trust`, `C/xfer`, `C/tv/ReceiverServer`, `C/link`) | porte + **J complet** + le parcours J **nommé** dans le cahier (nouveau ou étendu) | J complet + **F `--tv fake`** (< 10 min) | **oui** (diff + rapport F) |
| **écran ou service Android** (`S/**`, `R/**`) | `compileDebugKotlin` + **lint de pureté** + J de la zone (via la fonction pure créée/modifiée) | **F `--tv fake`** obligatoire ; `--tv emu` si l'écran est TV ; **aucune fusion sans `REPORT.md` PASS** | échantillon (1 sur 3) |
| docs, CI, scripts | porte du cahier | lint YAML/Python | non |
| **correctif terrain** | test rouge d'abord (§ 4.4), puis vert ; ligne REGRESSIONS | J + F ; H si l'écran TV change | oui si liaison/confiance/transfert |

Règles : **au plus 2 cahiers « risqués »** (liaison/confiance/transfert) en parallèle, fichiers disjoints, et **un seul** à la fois sur `C/tv/ReceiverServer.kt`, `C/trust/LinkDriver.kt`, `S/UploadService.kt`, `S/TvLink.kt` ; une fusion risquée est suivie d'un **F complet** avant la suivante. **W14 d'abord** : W13 (12 cahiers) et W7 (25) touchent exactement ces fichiers ; sans J ni F, chaque cahier W13/W7 est une régression possible. Après W14-S1 : chaque cahier W13/W7 encore à lancer reçoit, dans l'index W13/W7 (sans éditer les cahiers), **un parcours J obligatoire** (ligne « Parcours J » dans « Changements aux cahiers antérieurs ») ; W13 w13-07/w13-08/w13-09 se **rebasent** sur w14-05/w14-06 (fonctions pures `HomeLinkView`, `XferTexts`, `PinKeys`). Ce que W14 **ne fait pas** : le mécanisme `Blocker` (W13), le `LinkManager`/`CBSX`/`CBSY` (W7), la fenêtre d'association v2 (W7).

## 6. Tranches, effort, coût

| Tranche | Cahiers | Livre | Effort | Coût estimé |
|---|---|---|---|---|
| **S1 — à livrer d'abord** | w14-01 harnais (sonnet, **audit Opus**), w14-02 J liaison/PIN, w14-03 J transfert/progression, w14-04 J Ouvrir-avec/activation/formats, w14-05 fonctions pures (sonnet, **audit Opus**), w14-06 câblage écrans + lint, w14-14 REGRESSIONS + règles (haiku) | les 20 tests J ; R-01…R-04 reproductibles en JVM ; plus aucun écran qui décide ; journal | ≈ 9 j (≈ 6,5 j en parallèle sur 3 groupes) | ≈ 11 $ + audit 2,5 $ |
| **S2 — fumée** | w14-10 fausse TV CLI, w14-07 squelette `smoke.py` + rapport, w14-08 vérifications téléphone (adb), w14-09 vérifications TV (API, relais, lecture seule), w14-11 marqueurs `CB_JOURNEY`, w14-13 migration en place | `tools/smoke/` complet < 10 min ; migration scriptée | ≈ 7 j | ≈ 7 $ |
| **S3 — discipline** | w14-12 candidate + retour arrière + `handover.sh` (haiku), w14-15 CI + `gate.sh` + gabarits (haiku) | porte locale et CI ; remise refusée sans rapport | ≈ 1,5 j | ≈ 0,6 $ |
| **Total** | 15 cahiers | | **≈ 18 j** | **≈ 19 $ + 4 $ d'audit** (jauges estimées, non vérifiées) |

Comparaison : W13 ≈ 15 $, W7 ≈ 57 agent·jours. Une seule régression de terrain coûte au propriétaire une demi-journée et une réinstallation de TV ; S1 se rembourse à la première attrapée.

## 7. Décisions

**Prises par l'architecte (renversables)** : **D-W14-1** trois étages J/F/H, J en CI, F local avant toute fusion d'écran, H avant toute remise ; **D-W14-2** harnais J construit sur `CT/LinkFixtures.kt` + vraie `ReceiverServer` (pas de faux HTTP en mémoire : NanoHTTPD sur port 0 suffit et teste les vraies routes) ; **D-W14-3** la TV du propriétaire n'est jamais une cible active d'un script (lecture seule par relais) ; **D-W14-4** fonctions pures dans `C/trust`, `C/xfer`, `C/update` avec lint de pureté en `:core:test` (liste blanche datée) ; **D-W14-5** émulateur TV = AVD Android TV 34 **arm64** existant (`cbconnect_tv`) avec l'APK TV verrouillée + clés de TEST ; le 32 bits reste humain ; **D-W14-6** candidate = même `versionCode`, nom `-rc.N` par `-Pcastbridge.versionName`, promotion par rebuild du même commit sans suffixe ; **D-W14-7** marqueurs logcat `CB_JOURNEY` (tag unique, `clé=valeur`, sans secret, en `Log.i`, aussi en release : volume ≤ 1 ligne/s) ; **D-W14-8** W14-S1 avant tout cahier W13/W7 ; W13-S1 après W14-S1 ; W7 après W13.

**Pour le propriétaire (recommandation en gras)** : **Q-W14-1** accepter l'émulateur arm64 comme substitut de la TV pour la fumée (**oui** ; le 32 bits = point 1-4 de la liste H) ; **Q-W14-2** qui exécute la liste H à chaque livraison (**le propriétaire**, 15 min, ou un testeur qu'il désigne ; le coordinateur ne peut pas) ; **Q-W14-3** nom de candidate `-rc.N` (**oui**) ou build séparée sans suffixe.

**BLOQUÉ** : **B-W14-1** le **téléphone réel** du propriétaire peut-il servir à la fumée automatique (installation `-r` d'une candidate, partage de fichiers de test, lecture de `dumpsys`/`logcat`, **sans** jamais effacer les données) ? Si non, F tourne sur `cbconnect_phone` seulement et la liste H garde les points téléphone. Recommandation : **oui en lecture + installation `-r`**, jamais `pm clear`/`uninstall`.

## 8. Ce que cette conception n'a pas pu vérifier

- Que `cbconnect_tv` (Android TV 34 arm64) **installe et lance** l'APK TV actuelle avec libVLC/aria2 arm64 (`tools/rental-test` l'a fait avec `0.14.12-test` : probable, non revu ici) ; que l'émulateur accepte l'APK **verrouillée** avec les clés de TEST.
- Que `ReceiverServer` tolère deux instances successives sur le **même dossier** (`restart()` du harnais) sans verrou de fichier résiduel ; que `TransferHost` relit l'état `.cbx` d'un hôte précédent (la reprise par carte de blocs est documentée, non rejouée ici).
- La lisibilité réelle de `dumpsys notification --noredact` sur le S21+ (Samsung) et le coût de `uiautomator dump` (≈ 1-2 s) dans le budget de 10 min.
- Que `/api/screenshot` fonctionne sur la vraie TV par le relais (route présente dans `tools/routes/routes.txt` ; jamais exercée par un script).
- `cbdev` (SSH 2223) ne lit pas le logcat de CastBridge-TV (uid différent, `docs/agent-reports/diag-trusted-phone.md`) : les marqueurs `CB_JOURNEY` côté TV ne sont lisibles que sur l'émulateur ; sur la vraie TV, seule l'API répond (lecture seule). Une route `GET /api/journal` (ring de marqueurs, sans secret) est **proposée** mais laissée à W7 `LinkJournal`/W13 `BlockerLogTv` pour ne pas dupliquer.
- Les jauges de jetons et coûts sont estimées ; aucun cahier n'a été lancé.
