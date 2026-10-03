# w18-13 — Secours sans matériel neuf : Bluetooth optimisé (`BtPlan`, voie Bluetooth branchée, flux prioritaire du mux, qualité réduite sur accord, copie nocturne, cast audio/photo par le tunnel, limites dites)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (transcodage, FGS) · statut : **CONDITIONNEL** — verdict terrain ROUGE (ni GO ni SoftAP), ou pour les TV du parc sans Wi-Fi Direct (`wd.cap=0`) ; après la sortie du gel W15
> **Groupe : W18c** · prérequis : w18-02 fusionné ; `docs/agent-reports/w18-field-test.md` rempli · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.BtPlanTest' --tests 'castbridge.core.xfer.*' --tests 'castbridge.core.tunnel.*' :sender:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 3 j) · audit Opus : échantillon

**Vague 18c · Effort L · Modèle : sonnet · Statut CONDITIONNEL.** Conception : `docs/coordination/DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 9, § 5.1 (CAST par Bluetooth), D-W18-10. Branche `claude/sonnet-w18-13`. Rapport : `docs/agent-reports/sonnet-w18-13.md`. Règle W15 R2/R9 : seul cahier sur `S/UploadService.kt`.

## Objectif
Quand la seule voie est le Bluetooth (100-300 ko/s, rien ne le dépasse), faire le mieux possible **et le dire** : (1) `C/link/BtPlan.kt` (pur) : pour un envoi par Bluetooth : ETA honnête (débit mesuré glissant, 200 ko/s par défaut), ordre (petits lots et « Copier et lire » avant les vidéos, R-09 `QueueRules`), **proposition** « qualité réduite » (vidéo > 50 Mo, pas un lot signé, pas déjà ≤ 480p : « Envoyer en qualité réduite (480p, ≈ 60 Mo pour 10 min) : 5 min au lieu de 25 ? ») et « Envoyer cette nuit » (> 300 Mo : chargeur + inactif + TV allumée), compression seulement pour les extensions compressibles (`Blocks.compressible`), déduplication R-12 avant tout ; (2) brancher `BluetoothLane` (`C/xfer/Lanes.kt`, jamais branchée) dans `UploadService` par le tunnel mux `…0004` (reprise au milieu d'un bloc : `writeSlice`) ; (3) `C/tunnel/Mux.kt` : drapeau `urgent` sur les trames de télécommande/HELLO : l'envoi en cours marque une pause ≤ 300 ms quand une trame urgente attend (télécommande réactive pendant une copie) ; (4) `S/transcode/` : `MediaCodec` 480p ≈ 1 Mbit/s, FGS `dataSync`, fichier temporaire supprimé après envoi, **jamais** sans accord ; (5) `S/NightlyCopyJob.kt` : `JobScheduler` (chargeur, inactif, Bluetooth) qui relance la file persistée (R-09) ; (6) `C/player/CastPlan.kt` : cast par Bluetooth = audio et photo par le tunnel (`127.0.0.1:18765` ⇒ `:8089`), vidéo refusée avec la phrase et « Copier sur la TV et lire ».

## Pourquoi (preuves)
- `docs/TRANSFER.md` § 1 (RFCOMM 0,1-0,25 Mo/s), § 4 (`BluetoothLane` « pas encore branchée »), § 7 ; `C/xfer/Lanes.kt:149-188` ; `C/xfer/Blocks.kt` (compression par extension) ; `C/tunnel/Mux.kt:15-25` (trames) ; `C/tunnel/LinkPool.kt` ; `C/tv/QueueRules` (R-09) ; `C/tv/ContentIndex` (R-12) ; `S/TransferQueue.kt` (file persistée R-09) ; `C/player/CastPlan.kt` ; `DESIGN-W7` § 5.2 (« 80 Mo par Bluetooth ≈ 8 min : envoyer quand même ? »).
- Décision propriétaire : aucun matériel neuf ; W8 (agrégation) abandonné.

## Fichiers possédés
Nouveaux `C/link/BtPlan.kt`, `CT/link/BtPlanTest.kt`, `S/transcode/Transcoder.kt`, `S/transcode/TranscodeService.kt`, `S/NightlyCopyJob.kt`, `CT/xfer/BluetoothLaneWiringTest.kt` ; zones : `C/tunnel/Mux.kt` (`urgent`, ≤ 30 lignes + test dans `CT/tunnel/BtMuxTunnelTest.kt` zone additive), `C/xfer/Lanes.kt` (`BluetoothLane` : ≤ 20 lignes), `S/UploadService.kt` (branchement, ≤ 30 lignes), `C/player/CastPlan.kt` (refus vidéo par BT, ≤ 15 lignes), manifeste téléphone (service de transcodage `dataSync`, job). **Hors zone** : `S/TransferQueue.kt` (w18-08), `C/tv/ReceiverServer.kt`, `C/link/WdPolicy.kt` (lecture), `R/**`.

## Étapes
1. **Rouge** : `BtPlanTest` : ETA (200 Mo à 200 ko/s ⇒ « ≈ 17 min ») ; proposition qualité réduite seulement pour vidéo > 50 Mo non signée ; « cette nuit » > 300 Mo ; lot signé ⇒ jamais transcodé ; `.mp4` ⇒ jamais compressé, `.zip` non compressé ⇒ compressé ; doublon R-12 ⇒ « déjà sur la TV » ; ordre de file ; textes (chaque issue une phrase ; « Le Bluetooth ne permet pas la lecture en direct d'une vidéo » exacte).
2. **Rouge** : `BluetoothLaneWiringTest` (vraie `ReceiverServer` + faux mux : 3 Mo par tranches de 256 Kio, coupure au milieu d'un bloc ⇒ reprise à la tranche) ; `BtMuxTunnelTest.urgentFramePausesBulk` (une trame urgente passe en ≤ 300 ms pendant un envoi).
3. `BtPlan`, `Mux.urgent`, `BluetoothLane` branchée dans `UploadService` (route `UseBt` de `WdPolicy` ⇒ `TransferClient` sur le tunnel, repli CBT1 si TV sans `/api/transfer`).
4. `Transcoder` : `MediaCodec`/`MediaMuxer`, 854×480, 1 Mbit/s, audio copié ; annulable ; estimation de durée affichée ; refus honnête si le codec manque (phrase).
5. `NightlyCopyJob` : relance `TransferQueue` ; notification silencieuse au matin (« 2 fichiers copiés cette nuit ») ; si la TV était éteinte : « La TV était éteinte : reprise au prochain contact ».
6. `CastPlan` : refus vidéo par BT + `Suggest(COPY_AND_PLAY)` ; audio/photo par le tunnel.
7. **Sur appareil (propriétaire)** : H-29 copie 100 Mo par BT avec télécommande utilisée pendant (latence) ; H-30 qualité réduite d'une vidéo 10 min (durée du transcodage, taille, durée d'envoi) ; H-31 copie nocturne ; H-32 audio en direct par BT.
8. **Vert** : porte ; `:core:test` complet.

## Critères d'acceptation
Porte verte ; ≥ 16 tests rouges puis verts ; aucun transcodage sans `userAccepted = true` (test) ; `grep -n "mp4\|mkv" android/core/src/main/kotlin/castbridge/core/link/BtPlan.kt` ne montre que la liste « jamais compressé » ; aucune décision dans `S/transcode/**` ; aucun secret.

## Cas limites
Téléphone sans codec H.264 encodeur matériel ⇒ proposition masquée ; vidéo HEVC 10 bits ⇒ transcodage refusé avec phrase ; file nocturne et TV éteinte ⇒ aucune boucle (job suivant) ; mux occupé par une passerelle Internet (`…0002`) ⇒ l'envoi ralentit, l'ETA le dit ; coupure Bluetooth en plein transcodage ⇒ le fichier réduit est gardé 24 h pour l'envoi suivant.

## À ne pas faire
Aucune agrégation de radios ; aucun hotspot du téléphone ; ne pas promettre une vitesse ; ne pas toucher `TransferQueue.kt` ; pas de transcodage silencieux ; ne pas changer CBT1.

## Rapport
`STATUT`, sorties rouge/vert, mesures H-29…H-32, la phrase exacte des limites, question : seuil « qualité réduite » 50 Mo (recommandé) ou 100 Mo.
