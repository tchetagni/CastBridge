# w18-10 — Cast « Lire en direct », télécommande, lots, parental par la route active (téléphone) ; Quiz : URL par interface et QR invité (TV)

<!-- routage Fable 2026-10-03 -->
> **Amendement (W20, Fable, 2026-10-03)** : le QR et les URL par interface de la salle Quiz décrits ici restent ceux du périmètre **« Réseau local »** (⌂). W20 ajoute, à côté et seulement quand l'hôte l'a ouvert, un **second** code et QR « Internet » (◎, `https://bridge.sti-cm.com/play/j/<code>`) ; un invité entré par le QR `WIFI:` du groupe fait passer le signe « Partie sûre » en **orange** (« 1 invité dans le Wi-Fi de la TV », rotation du mot de passe proposée à la fin, D-W18-8 inchangé). Le dessin de la salle d'attente doit laisser la place au bandeau `SafetyView` (1 ligne en haut). Voir `docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md` § 1.3, § 1.6 ; les fichiers de W20 côté TV sont ceux de `sonnet-w20-05`, disjoints de ce cahier.
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : **ATTEND** sortie du gel W15 (après w18-08)
> **Groupe : W18b-3** (vague W18b, téléphone + TV, fichiers disjoints de w18-07/08/09) · prérequis : w18-05, 08 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin :receiver:compileDebugKotlin :core:test --tests 'castbridge.core.link.CastRouteTest' --tests 'castbridge.core.link.LocalAddressTest' --tests 'castbridge.core.quiz.*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 2 j) · audit Opus : échantillon

**Vague 18b · Effort M · Modèle : sonnet · Statut ATTEND.** Conception : `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 5.2, § 5.3 (cast et perte), § 6 (invités Quiz), D-W18-8. Branche `claude/sonnet-w18-10`. Rapport : `docs/agent-reports/sonnet-w18-10.md`.

## Objectif
Tout ce qui n'est pas « copie » passe aussi par le groupe, sans que l'usager le sache : (1) `S/player/CastSession.kt` `serve()` : adresse par `LocalAddress.toward(tvIp, ifaces, routeProbe)` (w18-05 ; `routeProbe` = `DatagramSocket.connect(tvIp, 9).localAddress`), choix par `CastRoute.decide` (refus honnête de la vidéo par Bluetooth, audio/photo par le tunnel) ; perte du groupe pendant un cast ⇒ attendre `WdRuntime` `Up` (≤ 20 s) puis `playUrl` à la position connue, sinon message et « Copier sur la TV et lire » ; (2) `S/ServerService.kt` : écoute déjà sur toutes les interfaces : vérifier, et tenir le service tant que `CastSession` est `PLAYING` même si le réseau par défaut change ; `S/Upnp.kt` `localIp()` **délégué** à `LocalAddress` (DLNA inchangé pour les autres TV) ; (3) `S/RemoteController.kt` : `host` = `RemoteBase.of(route)` (WD `Up` ⇒ 192.168.49.1), repli CBTR à la perte ; (4) `S/LotsRuntime.kt` : base de `LotPush` = route active ; (5) TV : `R/QuizHub.kt` `joinUrls()` (une par interface de la TV : `TvService.localIps()` à ajouter **ici**, en lecture de `NetworkInterface`, pas dans `TvService`), `R/QuizActivity.kt` : le QR de la salle affiche l'URL de la route du téléphone qui a ouvert (`QuizUrls`), et un bouton « Inviter un téléphone sans CastBridge » qui affiche le QR `WIFI:` du groupe (`WifiDirect.wifiUri`, identifiants lus de `TvPrefs` **par une méthode de w18-07**) puis l'URL ; à la fermeture de la salle, si un invité est entré, proposer « Changer le mot de passe Wi-Fi Direct » (une ligne, action de w18-07).

## Pourquoi (preuves)
- `S/player/CastSession.kt:125-165` (`live`, `playUrl`), `:297-308` (`serve`, `Upnp.localIp()`), `:310` (`poll`) ; `S/Upnp.kt:14-18` ; `S/ServerService.kt` (`MediaServer` `:8089`) ; `C/tv/ReceiverServer.kt:702-711` (`/api/playurl`).
- `R/QuizHub.kt:169` `joinUrl` (une seule IP) ; `C/quiz/QuizHttp.kt` (routes sans PIN) ; `C/tv/WifiDirect.kt` `wifiUri`.
- `S/RemoteController.kt:32,105` ; `S/LotsRuntime.kt` (`LotPush` base).
- `C/link/{CastRoute,LocalAddress,WdPolicy}.kt` (w18-02/05).

## Fichiers possédés
`S/player/CastSession.kt` (zones `serve`, `live` perte), `S/ServerService.kt`, `S/Upnp.kt` (`localIp`), `S/RemoteController.kt` (zone `connect`/base), `S/LotsRuntime.kt` (zone base), `R/QuizHub.kt`, `R/QuizActivity.kt`. **Hors zone** : `R/TvService.kt`, `R/WifiDirectGroup.kt` (w18-07 : on **appelle** `TvService.wd.credentialsForGuest()` si w18-07 l'a fourni, sinon bouton invité caché et dit au rapport), `S/TvLink.kt`, `S/link/**` (w18-08), `C/**`.

## Étapes
1. Rejouer `WdJourneyTest` J-WD-3 et J-WD-4 (cast) : contrat d'ordre.
2. `CastSession.serve` : `LocalAddress.toward` ; `CastFailure` texte de `CastRoute.Refuse` ; perte ⇒ `WdRuntime.awaitUp(tv, 20 s)`.
3. `RemoteController` : base dynamique ; `RemoteSession` existant garde ses numéros de séquence.
4. `QuizHub.joinUrls()` ; `QuizActivity` : QR de la bonne URL ; bouton invité.
5. **Sur appareil (propriétaire)** : H-24 « Lire en direct » d'une vidéo 720p par le groupe : lecture fluide ≥ 2 min, débit `:8089` lu dans `logcat` ; H-25 couper le Wi-Fi de la TV à 1 min ⇒ reprise ≤ 20 s après remise ; H-26 télécommande par le groupe (latence perçue) ; H-27 Quiz : un second téléphone (invité) scanne le QR `WIFI:` puis l'URL ⇒ joue ; H-28 audio par Bluetooth seul (Wi-Fi des deux éteint) ⇒ lecture ; vidéo ⇒ phrase + « Copier et lire ».
6. **Vert** : porte ; `compileDebugKotlin` des deux apps.

## Critères d'acceptation
Porte verte ; `grep -n "wlan" android/sender/src/main/kotlin/castbridge/sender/Upnp.kt android/sender/src/main/kotlin/castbridge/sender/player/CastSession.kt` vide ; `grep -n "localIp()" android/receiver/src/main/kotlin/castbridge/receiver/QuizHub.kt` vide (remplacé par `joinUrls`) ; aucune décision dans les écrans ; aucun mot de passe de groupe dans un `Log`.

## Cas limites
Téléphone sur LAN **et** groupe ⇒ l'adresse de la route active ; `/api/playurl` refuse une URL > 4096 (identifiant média long) ⇒ `Refuse` avant l'appel ; invité Quiz sur une TV R-14 (groupe frais) ⇒ QR `WIFI:` du groupe courant (valable le temps de la salle) ; `ServerService` tué par le système ⇒ `CastSession` le relance (existant `repeat(30)`), sinon phrase.

## À ne pas faire
Pas de SSDP/UPnP vers CastBridge-TV ; pas de décision dans `CastSession` (tout vient de `CastRoute`) ; ne pas toucher `TvService`, `WifiDirectGroup`, `TvLink`, `WdRuntime` ; ne pas changer `/api/playurl`.

## Rapport
`STATUT`, sorties, H-24…H-28, débit `:8089` observé, la méthode de w18-07 utilisée pour l'invité, question : proposer la rotation après **chaque** salle avec invité (recommandé) ou seulement à la demande.
