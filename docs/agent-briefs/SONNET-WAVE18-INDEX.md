# Vague 18 pour agents Sonnet/Haiku — index (2026-10-03) : Wi-Fi Direct primaire, la TV est le point d'accès, le téléphone s'y branche seul dès qu'il a le PIN

Source : `docs/coordination/DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` (lire § 0, § 1, § 5, § 7, § 8 et le § cité par chaque cahier). Protocole et règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport vivant `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »), gabarits `EXECUTOR-PROMPT-TEMPLATE.md`. Routage : en-tête de trois lignes sur chaque cahier ; `routing.json` : lignes proposées au § Routage (le coordinateur les ajoute).

**Décisions du propriétaire (2026-10-03)** : « dans 85% des cas, il n'aura pas de point d'accès. le cast et la copie par Wifi s'avèrera donc fondamental et ceci doit être transparent depuis l'app phone une fois que celle-ci aura le PIN. Cela doit être totalement plug and play » ; « Je ne veux pas me reposer sur le hotspot du téléphone. Uniquement sur le hotspot de la TV par Wi-Fi Direct sans achat de nouveau matériel ». Lecture appliquée : **la TV est le point d'accès** (groupe P2P dont elle est propriétaire, 192.168.49.1) ; **secret de groupe persistant par TV**, remis une fois par un canal authentifié (Bluetooth appairé + PIN, ou QR) ; reconnexion **sans geste** ; `LAN > WD ≥ BT` pour **tous** les usages ; secours = Bluetooth optimisé, sans matériel neuf.

**Brique existante (ne pas refaire)** : le bouton **« Wi-Fi Direct »** de la fiche de la TV (branche `claude/wd-manual-button`, exécutant lancé le 2026-10-03, hors W18) : visible si lien Bluetooth établi **et** PIN/jeton valides ; CBTN ⇒ jonction ⇒ état vert/orange/rouge + débit sur 20 Mo + faits ; c'est **l'outil du test terrain** et le contournement manuel (D-W18-11). W18 lui donne ensuite la jonction directe sans Bluetooth (w18-01/08) et une source d'état unique (`WdLine`, w18-09). Tout cahier qui touche la fiche de la TV (w18-09) **se rebase** sur cette branche.

**Prérequis absolu : le test terrain § 7 (10 min, bouton « Wi-Fi Direct » dès qu'il est installé, sinon application existante)**. Tant que le verdict (table § 7.2) n'est pas écrit dans `docs/agent-reports/w18-field-test.md` par le propriétaire, **seule la tranche 18a** (cœur pur, harnais, kit, docs) peut être lancée. 18b (câblage TV/téléphone) exige un verdict VERT ou ORANGE ; 18c (Bluetooth optimisé) un verdict ROUGE ou une TV du parc sans Wi-Fi Direct.

**Règles W18** : **(R1)** test rouge d'abord (JVM, horloges et radios factices) ; aucun appareil réel sauf le test terrain du propriétaire ; **(R2)** pendant le gel W15, **aucun** cahier ne touche `S/`, `R/` : w18-07…10 et 13 attendent la sortie du gel (ou l'exception « correctif terrain » accordée à R-14) ; **(R3)** audit Opus **obligatoire** sur w18-01 (identifiants), w18-07 (radios TV), w18-08 (radios téléphone) ; échantillon sur w18-02, 03, 10 ; **(R4)** aucune décision d'état dans un écran ni dans un service : tout dans `C/link/` ; **(R5)** aucun secret dans un journal, un `toString`, une URL, un TXT mDNS/DNS-SD, `/api/connections` ; **(R6)** la TV du propriétaire n'est jamais la cible d'un script ; **(R7)** la branche `claude/auto-wifi-direct` (R-14) est la base : ses 27 tests restent verts ; **(R8)** sémantique du PIN de `docs/ADMIN.md` inchangée (6 chiffres, `X-CB-Pin`, 5 échecs / 60 s) ; **(R9)** un seul cahier à la fois sur `S/TvLink.kt`, `R/TvService.kt`, `C/tv/ReceiverServer.kt` (gel R2).

**Modèle d'exécution** : `haiku` = kit terrain, docs (w18-11, 12) ; `sonnet` = le reste ; audit Opus : 3 obligatoires, 3 échantillons.

## Les 13 cahiers

| id | Cahier | Objet | Tranche | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w18-01 | `sonnet-w18-01-wd-credentials-core.md` | `WdCredentials` : secret persistant par TV (format, codage CBTN/HELLO/QR, fiche `PinBook` `wd.<tvId>`, rotation, masquage), `BootstrapPlan` (chemin du premier contact) | 18a | M | sonnet | **oui** | 400 / 20 | PRÊT | branche R-14 |
| w18-02 | `sonnet-w18-02-wd-policy-session-core.md` | `WdPolicy` (une table `LAN > WD ≥ BT` par usage, enveloppe `BulkRoute` + `LinkPlanner`), `WdSession` (jonction directe, déclencheurs, cycle de vie, perte en plein cast, bascule entre deux TV), `WdLine` (signalétique) | 18a | L | sonnet | échantillon | 700 / 35 | PRÊT | w18-01 |
| w18-03 | `sonnet-w18-03-trust-by-pin-core.md` | `TrustByPin` : HELLO sur RFCOMM appairé portant le PIN ⇒ confiance sans fenêtre « Autoriser » ; `PinGuard` par adresse Bluetooth ; textes | 18a | M | sonnet | échantillon | 350 / 18 | PRÊT | — |
| w18-04 | `sonnet-w18-04-wd-diag-core-route.md` | `WdDiag` (faits ⇒ phrases FR ⇒ verdict de la table § 7.2) ; route `GET/POST /api/diag/wifi-direct` (classe additive, une ligne dans `ReceiverServer`) | 18a | S | sonnet | non | 250 / 12 | PRÊT | — |
| w18-05 | `sonnet-w18-05-cast-route-local-address.md` | `LocalAddress.toward(tvIp)`, `CastRoute` (cast, Quiz `joinUrls`, télécommande : quelle base, quelle adresse source), textes de refus honnêtes | 18a | S | sonnet | non | 250 / 12 | PRÊT | w18-02 (interface `WdPolicy.Route`) |
| w18-06 | `sonnet-w18-06-journey-wifi-direct.md` | harnais W14 : `RadioSim` (P2P, BT, LAN factices), 9 parcours J-WD (première fois BT+PIN, QR, zéro geste, perte en cast, deux TV, essai, Android 10-12, rotation, verdict rouge ⇒ BT) | 18a | M | sonnet | non | 450 / 22 | PRÊT | w18-01…05 |
| w18-07 | `sonnet-w18-07-tv-persistent-group.md` | TV : `WifiDirectGroup` persistant (identifiants `TvPrefs` chiffrés, allumé à l'écran, bail v2, variante SoftAP si verdict b), DNS-SD P2P, QR étendu, rotation, `TvService` (une zone) | 18b | L | sonnet | **oui** | 800 / 40 | **ATTEND** verdict terrain + sortie du gel | w18-01, 02, 04 |
| w18-08 | `sonnet-w18-08-phone-wd-runtime.md` | téléphone : `WdRuntime` (jonction directe par identifiants, découverte DNS-SD, sockets par réseau sur 10-12, bascule entre TV), `TvLink` route de contrôle `Direct` si `Up`, `PinBook` `wd.*` | 18b | L | sonnet | **oui** | 800 / 40 | **ATTEND** verdict terrain + sortie du gel | w18-01, 02, 07 |
| w18-09 | `sonnet-w18-09-phone-first-contact-screens.md` | téléphone : « Ajouter ma TV » avec l'étape « Tapez le code de la TV », chemin QR, ligne d'état WD, réglage « Wi-Fi Direct » (toujours / jamais) | 18b | M | sonnet | non | 450 / 22 | **ATTEND** sortie du gel | w18-03, 08 |
| w18-10 | `sonnet-w18-10-phone-cast-quiz-remote-over-wd.md` | téléphone : `CastSession.serve` par `LocalAddress`, `ServerService`, `RemoteController`, `LotPush`/`ParentalSync` sur la route active ; TV : `QuizHub.joinUrls` + QR `WIFI:` invité | 18b | M | sonnet | échantillon | 450 / 22 | **ATTEND** sortie du gel | w18-05, 08 |
| w18-11 | `sonnet-w18-11-field-test-kit.md` | `docs/test-plans/WIFI-DIRECT-FIELD-TEST.md` (protocole 10 min, table F1-F11, verdict), `tools/wd/tv_wd_facts.sh` (relevés SSH/adb, sans secret), gabarit `docs/agent-reports/w18-field-test.md` | 18a | S | haiku | non | 120 / 10 | **PRÊT — À LANCER EN PREMIER** | — |
| w18-12 | `sonnet-w18-12-docs-wifi-direct.md` | `docs/WIFI-DIRECT.md`, renvois `ADMIN.md` § Wi-Fi Direct, `TRANSFER.md`, `BT-PLUG-AND-PLAY.md`, `HANDOFF.md` § 0, `REGRESSIONS.md` R-14 (suite) | 18a | S | haiku | non | 120 / 12 | PRÊT (après w18-02) | w18-01, 02 |
| w18-13 | `sonnet-w18-13-bluetooth-optimised-fallback.md` | secours sans matériel neuf : `BtPlan` (quand proposer la qualité réduite, la copie nocturne), `BluetoothLane` branchée, flux prioritaire du mux, transcodage opt-in (`S/transcode/`), cast audio/photo par le tunnel, phrases honnêtes | 18c | L | sonnet | échantillon | 800 / 40 | **CONDITIONNEL** (verdict ROUGE ou TV sans WD) | w18-02 |

Coûts (prix 2026-09 : haiku 1/5, sonnet 2/10, opus 4/20 $/M ; jauges **estimées, non vérifiées**) : sonnet S 3 × ≈ 0,4 $ = 1,2 $ ; sonnet M 4 × ≈ 1,0 $ = 4 $ ; sonnet L 4 × ≈ 2,0 $ = 8 $ ; haiku S 2 × ≈ 0,1 $ ; audits Opus obligatoires 3 × ≈ 0,8 $ = 2,4 $ ; échantillons ≈ 1,5 $. **Total ≈ 17-18 $**, ≈ **23 agent·jours** (18a ≈ 9, 18b ≈ 10, 18c ≈ 3 ; marge ≈ 1). **Pendant le gel : 18a ≈ 9 j, ≈ 7 $.** Test terrain : 10 min du propriétaire, 0 $.

## Tranches

| Tranche | Cahiers | Livre | Parallélisme |
|---|---|---|---|
| **Terrain** (propriétaire, d'abord) | w18-11 (kit) puis le test lui-même | le fait bloquant B-W18-1…4 : GO ? AP ? débits ? LAN gardé ? clé USB ? | — |
| **18a** (cœur, pendant le gel) | w18-01 → w18-02 → w18-06 ; w18-03 ∥ w18-04 ∥ w18-05 (après 02) ; w18-12 après 02 | tout ce qui **décide** : identifiants, routes, session, confiance par PIN, diagnostic, adresse de cast, 9 parcours J | 3 après w18-01 |
| **18b** (câblage, après le gel, verdict VERT/ORANGE) | w18-07 → w18-08 → (w18-09 ∥ w18-10) | la TV allume son groupe, le téléphone s'y branche seul, tout passe dessus | 2 en fin |
| **18c** (secours, verdict ROUGE) | w18-13 | Bluetooth optimisé et limites dites | 1 |

**Plus petite tranche visible** = 18a + w18-07 + w18-08 (≈ 15 j, ≈ 12 $) : première fois par Bluetooth + PIN, reconnexion sans geste, copie et contrôle par Wi-Fi Direct ; w18-09/10 ajoutent les écrans soignés et le cast/Quiz.

## Matrice de propriété (preuve de disjonction)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = tests cœur, `R/` = receiver, `S/` = sender.

| id | Fichiers possédés |
|---|---|
| w18-01 | nouveaux `C/link/WdCredentials.kt`, `C/link/BootstrapPlan.kt`, `CT/link/{WdCredentialsTest,BootstrapPlanTest}.kt` ; zones additives : `C/tv/BtProtocol.kt` (`LinkInfo` : `wd.id`, `wd.name`, `wd.pass`, `wd.persist` ; ≤ 25 lignes), `C/trust/PinBook.kt` (`wdOf`, `writeWd`, `forgetWd` ; ≤ 40 lignes), `C/tv/WifiDirect.kt` (`Err.ROTATED`, `Err.TRIAL` retiré de `ALL`) |
| w18-02 | nouveaux `C/link/{WdPolicy,WdSession,WdLine,WdTriggers}.kt`, `CT/link/{WdPolicyTest,WdSessionTest,WdLineTest}.kt` ; `C/link/BulkRoute.kt` (zone : `MIN_WD_BYTES` conditionnel, `Why.TRIAL` retiré ; ≤ 15 lignes) ; `C/tv/BtProtocol.kt` **lecture seule** (`LinkPlanner.plan` reçoit `wdUp` par un **nouveau** `LinkPlanner2` dans `WdPolicy.kt`) |
| w18-03 | nouveaux `C/trust/TrustByPin.kt`, `CT/trust/TrustByPinTest.kt` ; zones additives : `C/trust/HelloHandler.kt` (branche `pin != null` ; ≤ 20 lignes), `C/tv/BtProtocol.kt` (drapeau `HELLO_HAS_PIN`, ≤ 10 lignes, **après** la fusion de w18-01), `C/trust/LinkText.kt` (3 phrases) |
| w18-04 | nouveaux `C/link/WdDiag.kt`, `C/tv/WdDiagRoutes.kt`, `CT/link/WdDiagTest.kt`, `CT/tv/WdDiagRoutesTest.kt` ; `C/tv/ReceiverServer.kt` (**une** ligne de délégation), `tools/routes/routes.txt` (2 lignes) |
| w18-05 | nouveaux `C/link/{LocalAddress,CastRoute}.kt`, `CT/link/{LocalAddressTest,CastRouteTest}.kt` ; `C/ux/UiTexts.kt` (phrase `LIVE` révisée) |
| w18-06 | nouveaux `CT/journey/{RadioSim,WdJourneyTest,WdPhoneSim}.kt` ; `CT/journey/TvSim.kt` (zone additive ≤ 8 lignes) |
| w18-07 | `R/WifiDirectGroup.kt`, `R/TvPrefs.kt` (`wdCredentials`), nouveaux `R/{WdDnsSd,WdQr}.kt`, `R/TvService.kt` (zone `linkInfo`/`wdLeaseCheck`/`onResume`), `R/PairActivity.kt` (QR : contenu), `R/ConnectionActivity` (ligne diagnostic) |
| w18-08 | `S/AutoWifiDirect.kt` → `S/link/WdRuntime.kt` (renommage + extension), nouveaux `S/link/{WdDnsSdClient,WdNetworkSockets}.kt`, `S/TvLink.kt` (zone `canJoinWifiDirect`/route), `S/PinStore.kt` (`wd.*` par `PinBook`), `S/TransferQueue.kt` (zone `bulkBase`) |
| w18-09 | `S/TvPairScreen.kt`, `S/ConnectScreens.kt` (section Connexion), nouveaux `S/link/{PinEntryStep,QrAddTvStep}.kt`, `S/TvHome.kt` (ligne d'état) |
| w18-10 | `S/player/CastSession.kt` (`serve`), `S/ServerService.kt`, `S/Upnp.kt` (`localIp` délégué), `S/RemoteController.kt` (base), `S/LotsRuntime.kt` (base), `R/QuizHub.kt` (`joinUrls`), `R/QuizActivity.kt` (QR invité) |
| w18-11 | nouveaux `docs/test-plans/WIFI-DIRECT-FIELD-TEST.md`, `tools/wd/tv_wd_facts.sh`, `tools/wd/README.md`, gabarit `docs/agent-reports/w18-field-test.md` ; `docs/test-plans/README.md` (une ligne) |
| w18-12 | nouveau `docs/WIFI-DIRECT.md` ; une section/ligne dans `docs/ADMIN.md`, `docs/TRANSFER.md`, `docs/BT-PLUG-AND-PLAY.md`, `docs/HANDOFF.md`, `docs/REGRESSIONS.md` |
| w18-13 | nouveaux `C/link/BtPlan.kt`, `CT/link/BtPlanTest.kt`, `S/transcode/{Transcoder,TranscodeService}.kt`, `S/NightlyCopyJob.kt` ; `C/tunnel/Mux.kt` (zone `urgent` ≤ 30 lignes), `C/xfer/Lanes.kt` (`BluetoothLane` : zone ≤ 20 lignes), `S/UploadService.kt` (branchement `BluetoothLane`, zone ≤ 30 lignes), `C/player/CastPlan.kt` (refus vidéo par BT) |

Branche `claude/wd-manual-button` (hors W18) : ses fichiers (écran du bouton dans `S/`, mesure 20 Mo) sont **lecture seule** pour w18-01…07 ; w18-08 (source d'état) et w18-09 (fiche) se rebasent dessus et le disent au rapport.

Disjonction : `C/tv/BtProtocol.kt` = w18-01 (zone `LinkInfo`) puis w18-03 (zone HELLO) **en série** ; `C/link/BulkRoute.kt` = w18-02 seul ; `R/TvService.kt` = w18-07 seul ; `S/TvLink.kt` = w18-08 seul ; `C/tv/ReceiverServer.kt` = w18-04 (une ligne) ; `S/UploadService.kt` = w18-13 seul ; `C/trust/PinBook.kt` = w18-01 seul ; `S/TvHome.kt` = w18-09 seul. Fichiers de la branche R-14 non cités (`C/link/WdClient.kt`, `AutoWifiDirectTest.kt`, `S/BtUploadService.kt`, `R/BtServer.kt`) : **personne** (lecture seule ; lignes proposées au rapport).

## Graphe de dépendances

```
terrain : w18-11 ─► test (propriétaire) ─► verdict ──────────────────────────────────────┐
18a     : w18-01 ─► w18-02 ─► w18-05 ─► w18-06                                           │
                 └► w18-03 ──────────────┘   w18-04 ─► w18-06 ; w18-12 après w18-02      │
18b     : [verdict VERT/ORANGE + fin du gel] w18-07 ─► w18-08 ─► w18-09 ∥ w18-10 ◄───────┘
18c     : [verdict ROUGE] w18-13 (après w18-02)
```

## Amendements aux cahiers W7 (en-tête « Amendement » seulement)
w7-09 (`RoutePolicy` : WD n'est plus « isolation seulement »), w7-12 (`TvBeacon` publie aussi le service DNS-SD P2P), w7-16 (déclencheurs de jonction), w7-17 (QR étendu `wd`/`wp`), w7-21 (`WifiDirectAuto` remplacé par `WdSession`/`WdRuntime` ; spécificateur seulement sur 10-12). W7 reste le cadre ; W18 en est la part « sans point d'accès ».

## Routage (lignes proposées pour `routing.json`)
`w18-01..06,12` : `sonnet`/`haiku` pendant le gel, zone `C/`, `CT/`, `docs/`, `tools/wd/` ; `w18-07..10,13` : `sonnet`, après la sortie du gel, zones `R/` et `S/` disjointes ; audits : `opus` sur 01, 07, 08.
