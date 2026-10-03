# w20-05 — Câblage CastBridge-TV (`R/`) : `PlayTransport` (WebSocket sortant, repli SSE/long-poll), bandeau « Partie sûre », « Ouvrir sur Internet » / « Fermer Internet », repli local à 60 s, relais des téléphones locaux, passerelle Internet du téléphone
<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (TV exposée à une connexion Internet, confiance, TLS) · statut : **ATTEND sortie du gel** (W15 R3/R5 : `R/`)
> **Groupe : W20-S4** (câblage) · prérequis : w20-01, 02, 03, 04, 07 fusionnés ; décision D-W20-7 · porte : `cd android && tools/agents/gradle-lock.sh gradle :receiver:compileDebugKotlin` + `:core:test --tests 'castbridge.core.quiz.online.*'` + fumée `tools/smoke/… --tv fake` (si elle existe ; sinon relevé humain H-PLAY)
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 3 j) · audit Opus : **obligatoire**

**Vague 20 · Effort L · Modèle : sonnet · Statut ATTEND sortie du gel.** Conception : `DESIGN-W20` § 1.3-1.5, § 5.1. Branche `claude/sonnet-w20-05`. Rapport : `docs/agent-reports/sonnet-w20-05.md`. Zone : `R/quiz/**` et `R/QuizHub.kt`, `R/QuizActivity.kt` (zones additives) ; **jamais** `C/tv/ReceiverServer.kt` ; la route `POST /api/quiz/scope` passe par l'extension Quiz existante (`QuizPackApi`/`QuizHttp` enregistrés par `QuizHub`).

## Objectif
Sur la TV : (1) `PlayTransport` mince (OkHttp WebSocket si déjà dans l'APK, sinon `java.net.http` indisponible sur API 26 ⇒ OkHttp ; vérifier `R/` dépendances) : connexion **sortante** `wss://bridge.sti-cm.com/play/ws`, TLS système (certificat invalide ⇒ **rouge**, jamais de confiance ajoutée), repli SSE/long-poll via `HttpURLConnection` ; (2) `QuizHub` : `ServerAuthority` quand le périmètre est Internet, `LocalAuthority` sinon ; (3) l'écran : bandeau `SafetyView` (haut, 1 ligne, formes + mots, couleurs `SignalColors`), tuile « Ouvrir sur Internet » en salle d'attente (visible selon `PlayRules`/`SafetySign` : sinon la **raison** s'affiche), écran de confirmation § 1.4 (Annuler présélectionné), code Internet `XXXX-XXXX` + QR (`QrCode` du cœur) du lien `https://bridge.sti-cm.com/play/j/<code>`, « Fermer Internet » toujours visible ; (4) ticket : `POST /api/v1/play/ticket` par la route réseau de la TV **ou par la passerelle Internet du téléphone** (`Routes`, comme les lots), renouvelé à 8 min ; (5) relais : chaque `act` d'un téléphone local (via `QuizHttp` local) est transmis avec `localElapsedMono` ; les téléphones locaux continuent de voir la page `/quiz` **de la TV** (aucun changement pour eux) ; (6) perte d'Internet : orange ⇒ 60 s ⇒ repli **nouvelle partie locale** avec texte, « Reprendre la partie Internet » une fois ; (7) `POST /api/quiz/scope {open:true}` (PIN) depuis le téléphone ⇒ ouvre l'écran de confirmation **sur la TV** (D-W20-7) ; (8) profil enfant : `ParentalEngine.check(INTERNET)` ⇒ noir avec texte.

## Pourquoi (preuves)
- `R/QuizHub.kt:128-135` `open(ctx)` crée `QuizRoom` ; `:169` `joinUrl` : point d'entrée unique du câblage.
- `S/QuizScreen.kt:138` : le téléphone appelle déjà `POST /api/quiz/open` avec le PIN : `scope` suit le même modèle (confirmation TV en plus).
- `C/ux/TvSignal.kt:7` : Internet absent = NOIR : le bandeau le respecte via `SafetySign`.
- `docs/QUIZ.md` § 6 quater « Sources » : la TV joint déjà le serveur **par la passerelle Internet du téléphone** (Bluetooth/Wi-Fi) pour les packs : même route pour le ticket et le WebSocket (débit ≈ 1 Ko/s par table : compatible Bluetooth, signe orange « par le téléphone »).
- `DESIGN-W18` § 5.3 : le groupe Wi-Fi Direct ne donne pas Internet à la TV : sans passerelle ⇒ noir.

## Fichiers possédés
- Nouveaux : `R/quiz/PlayTransportOkHttp.kt`, `R/quiz/PlayBanner.kt` (vue), `R/quiz/PlayScopeDialog.kt`, `R/quiz/PlayTicketClient.kt`, `R/quiz/PlayLocalRelay.kt`, `R/quiz/PlayFallback.kt`, tests `android/receiver/src/test/kotlin/castbridge/receiver/quiz/{PlayFallbackTest, PlayBannerModelTest}.kt` (JVM, modèles purs), `docs/test-plans/H-PLAY.md` (relevé humain 15 min : ouvrir sur Internet, 2 téléphones distants (données mobiles), couper la box à la question 4, vérifier orange → rouge → partie locale, reprise).
- Zones additives : `R/QuizHub.kt` (choix d'autorité, ticket), `R/QuizActivity.kt` (bandeau, tuile, dialogue), extension Quiz enregistrant `POST /api/quiz/scope`, `android/receiver/build.gradle.kts` (OkHttp **seulement** s'il n'y est pas déjà ; sinon rien).
- Interdit : `C/tv/ReceiverServer.kt`, `R/TvService.kt`, `C/**` (si manque : `QUESTION:`).

## Étapes
1. **Rouge** : `PlayFallbackTest` (horloge injectée : perte à t0 ⇒ orange ; 60 s ⇒ rouge + `LocalAuthority` neuve + texte ; retour à 5 min ⇒ proposition une fois ; retour à 11 min ⇒ rien), `PlayBannerModelTest` (le bandeau n'affiche **que** `SafetyView` reçue : aucune couleur calculée localement en Internet).
2. Transport + ticket + `ServerAuthority` ; `QuizHub.scope(ctx, INTERNET)` ; dialogue de confirmation (D-pad, Annuler préféré, textes § 1.4).
3. Relais local : `QuizHttp` local reste la source des téléphones locaux ; `PlayLocalRelay` écoute `onChange`/`act` et pousse `relayAct`.
4. Repli § 1.5 ; « Fermer Internet » ; `/api/quiz/scope`.
5. Compilation, tests JVM, **relevé humain H-PLAY** par le propriétaire (le cahier ne touche aucun appareil).

## Critères d'acceptation
- En `TV_ONLY` et `LAN`, **aucune** connexion sortante (le `ProxySelector` espion de `QuizLotsTest` est rejoué sur l'écran via le modèle : aucun appel `PlayTransport` tant que le périmètre n'est pas Internet).
- Certificat TLS invalide ⇒ rouge « Internet : impossible · certificat non valide », salle non créée, aucun bouton « continuer quand même ».
- Un téléphone local **ne voit aucune différence** de page ni de latence mesurable (SSE local) quand la salle s'ouvre sur Internet, hors le bandeau.
- Profil enfant -12 ⇒ tuile Internet noire avec texte, même avec le code parental.

## Cas limites
- Ticket refusé après ouverture (révocation à 15 min) ⇒ la salle **termine la question en cours** puis « Fermer Internet » automatique avec raison. Passerelle Bluetooth du téléphone qui tombe ⇒ même chemin que la perte d'Internet. Deux téléphones de confiance demandent `scope` ⇒ un seul dialogue.

## À ne pas faire
- Aucun port d'écoute nouveau sur la TV. Aucun `X-CB-Pin` vers Internet. Aucune confiance TLS personnalisée. Aucune modification de `play.html` local. Pas de déploiement.

## Rapport
Format RAPPORT + `SYMBIOSE: cap=play1 (TV) · proto=inchangé côté /api (route additive /api/quiz/scope) · reason=PLAY_* · deux écrans=H-PLAY` + liste des textes + points d'audit Opus (TLS, ticket, relais).
