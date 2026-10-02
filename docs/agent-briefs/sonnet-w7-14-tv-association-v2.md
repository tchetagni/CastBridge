# w7-14 — CastBridge-TV : association v2 (`/api/hello` v2, `/api/knock`, fenêtre auto-ouverte, toc, QR, dialogue SAS de ré-adoption)

**Vague 7b · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après 7a ; en parallèle de w7-12/w7-13 : contrats `TvBeacon.knocks`, `SyncHost`).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 4.1 (rangs 3, 4, 6), § 4.2, § 4.4, § 8.1, § 8.3 (D-pad). Branche `claude/sonnet-w7-14`. Rapport : `docs/agent-reports/sonnet-w7-14.md`.

## Objectif
(1) `GET /api/hello` v2 : ajoute `id` (8 hex), `proto`, `caps`, `name`, `v` (le téléphone reconnaît une TV épinglée sans mDNS) ; `POST /api/knock` (corps `name`, `pub` b64url) ⇒ `TvBeacon.onKnock` ; (2) `PairActivity` : fenêtre selon `PairingWindowPolicy` (auto 10 min sans téléphone de confiance **depuis l'accueil**, bandeau « Prêt à être associé… » ; 2 min sur toc avec dialogue « <nom> demande à s'associer » ; manuel inchangé), **plus de fermeture/réouverture à 8 s** (la fenêtre vit par sa durée, pas par l'activité), popup « visible » via `TvPermissions.askVisibleOnce` ; (3) **QR** sur `PairActivity` (« Ma TV n'apparaît pas » → image) : `castbridge://tv?id=&bt=&ip=&port=&pub=&sas=` (générateur QR **pur** en Kotlin, matrice dessinée sur `Canvas`, pas de dépendance ; version 6-8, niveau M) ; (4) dialogue **SAS de ré-adoption** : « Est-ce bien votre téléphone ? Code **47 12** [Refuser] [Autoriser] » déclenché par `HelloHandler.onReadoptable` (w7-06) ou par `Readoption.decide` ; (5) « À propos » / `PairActivity` montrent l'**empreinte** de la TV (`Identity.fingerprint`).

## Pourquoi (preuves)
- `R/PairActivity.kt:134-141` (boutons), `:153-162` (`onStop` ferme à 8 s), `:187-196` (`openWindow` + discoverable), `:239` (`refresh` rouvre), `:270-272` (dialogue Autoriser/Refuser, Refuser focus) ; `R/PlayerActivity.kt:546,579,711` (ouvertures manuelles) ; `C/tv/ReceiverServer.kt:275-276` (`/api/hello` public `{"app","v","pinRequired"}`).
- `C/link/{PairingWindowPolicy,Readoption,Identity}.kt` (w7-06), `C/trust/PairingSession.kt` (`openFor`), `C/link/SecureSession.kt` (`sas`), `C/tv/WifiDirect.kt` (il existe déjà un URI `WIFI:` pour QR : s'en inspirer pour le format, pas de QR dessiné aujourd'hui).
- Problème terrain 9 (boucles) et 6 (PIN après réinstallation).

## Fichiers possédés
Modifiés : `R/PairActivity.kt`, `C/tv/ReceiverServer.kt` (**seulement** `/api/hello` v2 + route `/api/knock` ; `hello` reçoit un `helloExtra: () -> Map<String,String>` injecté par `TvService` : si cela exige `TvService.kt` (w7-12), rapport `À BRANCHER`), `R/HomeScreen.kt` (bandeau « Prêt à être associé », empreinte dans le panneau). Nouveaux : `R/PairQr.kt` (générateur QR pur + vue), `R/ReadoptDialog.kt`, `CT/tv/QrTest.kt` (générateur : vecteurs d'un QR connu, ex. « HELLO WORLD » version 1-M matrice attendue). **Hors zone** : `R/TvBeacon.kt`, `R/TvPermissions.kt`, `R/TvService.kt`, `R/PlayerActivity.kt` (w7-12), `R/BtServer.kt` (w7-15).

## Étapes
1. `/api/hello` v2 (additif, public, aucune donnée sensible : `id` = empreinte tronquée, pas le code d'appareil) ; `/api/knock` : limite 1/10 s par IP, réponse `{"asked":true}` ; **jamais** d'ouverture de fenêtre sans action du propriétaire.
2. `PairActivity` : lire `PairingWindowPolicy.onHome(trust.list().size, TvPermissions.visibleAsked)` à l'arrivée sur l'accueil (crochet dans `HomeScreen` → `PairActivity.autoWindow(ctx)` statique) ; fenêtre = `pairing.openFor(ms)` ; retirer la fermeture à 8 s ; `TvBeacon.knocks` observé ⇒ dialogue toc ⇒ `openFor(KNOCK_MS)` ; compte à rebours existant conservé.
3. QR : bouton « Ma TV n'apparaît pas » → page QR plein écran (≥ 320 px de côté, fond blanc, marge) + texte « Scannez ce code avec l'appareil photo du téléphone » ; contenu : `id`, `bt` (adresse BT **de la TV** : acceptable, c'est l'écran de la TV elle-même), `ip` (première IPv4 de site), `port`, `pub` (b64url), `sas` (4 chiffres **aléatoires par affichage**, valides 10 min, remis à `SyncHost` pour accepter une poignée de main LAN dont le SAS correspond : contrat `SyncHost.expectSas(code, untilMs)` — si w7-13 n'est pas fusionné, rapport `À BRANCHER`).
4. `ReadoptDialog` : plein écran, code en 40 sp, [Refuser] focus initial, [Autoriser] ⇒ `trust.trust(address, name, pub)` + `pairing` reste fermée ; 3 refus ⇒ blocage existant.
5. Empreinte : « À propos » (le fichier « À propos » est `PlayerActivity` : **hors zone** ⇒ l'afficher dans `PairActivity` et dans le panneau `HomeScreen` « Téléphones de confiance : empreinte de cette TV 7f3a-91c2 ») .
6. Tests : `QrTest` (matrice), `PairActivity` logique extraite testée en JVM si possible (`PairWindowController` pur dans `R/` ? non : dans `C/link/PairingWindowPolicy` déjà) ; émulateur : premier démarrage ⇒ bandeau + fenêtre 10 min ; refuser « visible » ⇒ pas de redemande ; toc depuis le téléphone (w7-19) ⇒ dialogue.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin && gradle --offline :core:test --tests 'castbridge.core.tv.QrTest' --tests '*ReceiverServer*'
grep -n 'postDelayed' android/receiver/src/main/kotlin/castbridge/receiver/PairActivity.kt | grep -i 'close\|8_000\|8000' | wc -l   # 0
curl -s http://<tv>:8765/api/hello | python3 -c "import sys,json;d=json.load(sys.stdin);assert d['id'] and d['proto']==1;print('ok')"
```
Observable : TV réinstallée + téléphone déjà lié ⇒ sur le téléphone « La TV a changé d'identité » → « Confirmer » ⇒ fenêtre auto-ouverte sur la TV ⇒ un « Autoriser » ⇒ lié, **aucun code saisi**.

## Cas limites
TV sans Bluetooth ⇒ QR sans `bt`, SAS obligatoire ; deux tocs simultanés ⇒ le second attend (« La TV traite déjà une demande ») ; SAS expiré ⇒ nouveau QR ; mode enfant actif ⇒ fenêtre auto **non** ouverte (règle parentale : seul un adulte avec la télécommande + code parental ouvre) — documenter.

## À ne pas faire
Aucune bibliothèque QR ; pas de code d'appareil ni de PIN dans le QR ; pas de popup « visible » hors `TvPermissions` ; ne pas modifier `PlayerActivity.kt`.

## Rapport
`STATUT`, `À BRANCHER`, captures (bandeau, QR, dialogue SAS), comportement du bouton Retour sur chaque écran.
