# w21-04 — Câblage TV des métriques techniques derrière `pocMetrics` : points de mesure du jeu en ligne, passerelle, performances, copie ; porte d'envoi ; aucun écran neuf
<!-- routage architecte 2026-10-04 (W21 données techniques du POC) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (consentement et cohorte respectés, aucune gêne du jeu, aucune donnée personnelle, gel des écrans respecté) · statut : **ATTEND w21-01 et la fusion de w20-05a**
> **Groupe : W21-B** (ordre 3) · porte : `:core:test --tests 'castbridge.core.telemetry.*' --tests 'castbridge.core.quiz.online.*'` + `:receiver:testDebugUnitTest` (par `tools/agents/gradle-lock.sh`) ; build verrouillé `-PrequireActivation=true` compilé (sans installation)
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 2 (M-01…M-32, M-46…M-50), § 3.4 (porte d'envoi), § 3.5 (cohorte). Branche `claude/w21-04-tv-tech-wiring`. Rapport : `docs/agent-reports/sonnet-w21-04.md`.

## Objectif (autonome)
Le cœur `castbridge.core.telemetry.tech` (w21-01) fournit `TechMetrics`, les enregistreurs et `UploadGate`. Le client de jeu Internet de la TV (w20-05a : `C/quiz/online/{PlayHttpTransport,PlayTvSession,RelayAuthority,LinkCause}.kt`) et les éléments existants (`R/TvConnect.kt` `track`, `R/BtGatewayHost.kt` `gateway_session`, `R/TvNetDiag.kt`, `R/Thumbnailer.kt`, `R/QuizHub.kt`, `C/connect/Routes.kt`, file de réception) émettent aujourd'hui peu ou pas de mesures techniques. Poser les **points de mesure passifs**, n'activer `TechMetrics` que si `consentement == USAGE && directives.pocMetrics && pas de profil enfant`, et faire passer **toute** la télémétrie par `UploadGate`. **Aucun écran neuf, aucun texte affiché neuf** (gel des écrans).

## Fichiers possédés
- **Nouveaux** : `android/receiver/src/main/kotlin/castbridge/receiver/TvTech.kt` (fabrique de `TechMetrics` pour la TV : lecture de `pocMetrics` dans l'état du lien serveur, consentement, profil enfant ; échantillonneur horaire du tas via `Runtime`/`ActivityManager.MemoryInfo`, `onTrimMemory`, `Choreographer` pour les images > 100 ms **seulement si** activé) ; `android/core/src/main/kotlin/castbridge/core/quiz/online/PlayProbe.kt` (interface pure des points de mesure du client en ligne, implémentation nulle par défaut) ; tests `android/core/src/test/kotlin/castbridge/core/quiz/online/PlayProbeTest.kt`, `android/receiver/src/test/kotlin/castbridge/receiver/TvTechTest.kt`.
- **Zone additive** : `PlayHttpTransport.kt` (octets et durées des rafales, POST → `ack`, coupures, reprises, décomposition de l'ouverture : ticket, TCP, TLS, `hello`→`welcome`) ; `PlayTvSession.kt` (marge à la réception `opensAt − (serverNow + rtt/2)`, révélation, refus `PLAY_*`, `hello.link` envoyé = `via` + classe de la passerelle) ; `RelayAuthority.kt` (relais local, téléphones, 9e refusé) ; `LinkCause.kt` (temps orange/rouge et cause) ; `R/BtGatewayHost.kt` (`link.gw` : reconnexions, échecs SOCKS, classe déclarée si le téléphone l'envoie — champ additif lu, absent ⇒ `unk`) ; `R/TvNetDiag.kt` (sonde `generate_204` à l'attache de la passerelle et une fois par heure **hors partie**) ; `R/TvConnect.kt` / l'endroit où `TelemetryUploader.flush` est appelé (passage par `UploadGate`) ; `R/QuizHub.kt` (`perf.quiz`) ; `R/Thumbnailer.kt` (`perf.thumb`) ; point d'état final de la file de réception (`sync.xfer` côté TV : ralenti, plancher) ; le point Wi-Fi Direct côté TV s'il existe (`link.wd`).
- **Interdit** : tout fichier d'écran (`*Activity.kt` hors lecture d'état, mises en page, chaînes affichées), `C/owner/**`, `C/lots/**`, `C/tv/ReceiverServer.kt`, `R/TvService.kt` (sauf si l'appel de `flush` y est : alors **une** ligne, justifiée dans le rapport), `backend/`, `server-play/`, `S/`.

## Étapes
1. **Rouge** (sortie collée) : `PlayProbeTest.lateAnnouncementIsCounted` (sonde absente).
2. `PlayProbe` + implémentation qui alimente un `SessionRecorder` ; appels posés dans les fichiers du client en ligne (une ligne par point ; aucune logique de jeu modifiée ; sonde nulle par défaut ⇒ comportement identique, tests de w20-05a verts **sans** modification).
3. `TvTech` : objet nul tant que la porte est fausse ; bascule à chaud (consentement retiré ⇒ file technique vidée par `removeIf`).
4. `UploadGate` branchée : partie Internet en cours ou fin < 30 s ⇒ **aucun** `flush` ; passerelle ⇒ `maxBytes` 8 192 ; copie active ⇒ techniques retenus.
5. Points `perf.*`, `link.*`, `sync.xfer` (TV), `net.route.flips` (`Routes`).
6. **Vert** ; compilation du build verrouillé.

## Critères d'acceptation (JVM ; mutations appliquées puis retirées)
- `PlayProbeTest` : partie simulée (horloge et transport factices de w20-05a, `SlowSocksProxy` si disponible) : 2 annonces en retard ⇒ `late = 2` ; coupure de 12 s ⇒ reprise comptée et durée dans la bonne case ; refus `PLAY_BUSY` ⇒ `play.fail`.
- `TvTechTest.zeroBytesDuringOnlineGame` : pendant une partie simulée de 10 questions, `flush` n'est **jamais** appelé, même avec 400 évènements en file et un `crash` (mutation : retirer la porte ⇒ échec) ; après 30 s ⇒ un lot ≤ 8 192 octets compressés par la passerelle.
- `TvTechTest.offWithoutCohortOrConsent` : `pocMetrics=false` ⇒ 0 évènement technique, 0 sonde lancée, 0 abonnement `Choreographer` (mutation) ; consentement essentiel seul ⇒ idem ; profil enfant ⇒ idem.
- `TvTechTest.noForbiddenProps` : tous les évènements émis passent la validation serveur simulée (copie de `TechValidator` de w21-02 si fusionné, sinon liste blanche du JSON) ; aucune propriété textuelle hors liste fermée.
- Mesure : surcoût de tas de `TechMetrics` actif pendant une partie simulée ≤ 64 Ko (mesure consignée) ; aucun fil neuf permanent hors l'échantillonneur horaire.
- Les tests existants de `:receiver` et du client en ligne restent verts sans modification ; build verrouillé compilé.

## Interdits
Aucune sonde active pendant une partie ; aucun texte affiché ; aucune installation sur appareil ; aucune connexion au serveur ; ne pas modifier `ConsentText`.

## Rapport
Rouge, vert, mutations, liste des points de mesure (fichier:ligne), surcoût mesuré, ce qui n'a pas pu être câblé (Wi-Fi Direct, classe de la passerelle si le téléphone ne l'envoie pas encore).
