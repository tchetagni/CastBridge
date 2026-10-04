# w20-06 — Client téléphone et page web : onglet Quiz de CastBridge (rejoindre par code, lien d'application `castbridge://play`, QR), WebView `/play`, `deviceHash`, « Ouvrir la salle de ma TV sur Internet » ; page `/play` partagée (sans app) avec le même signe
<!-- routage Fable 2026-10-03 -->
> **Amendement 2026-10-04 — PARTIE (A) ANNULÉE, PARTIE (B) SANS OBJET EN PHASES 1-2, conception `docs/coordination/DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` § 3, § 6.** Décisions du propriétaire : « aucune page web de jeu, jamais » ; « un téléphone synchronisé, par internet = TV à laquelle il est connecté » ; « Pas de délégation ». **(A) page `/play` jouable : ANNULÉE** ; `/play` ne sert plus qu'une page d'information statique, écrite par `sonnet-w20-04b` (`info.html`), qui possède désormais ce chemin ; `play.html/js/css` du service restent dans le dépôt, non servis. **(B) téléphone : aucune connexion au service, jamais** ; pas de saisie de code Internet, pas de lien d'application `castbridge://play`, pas d'App Links ni `assetlinks.json`, pas de WebView `/play`, pas de `deviceHash` côté service. Le téléphone reste le joueur **local** de sa TV (page `/quiz` de la TV, `S/QuizScreen.kt`, inchangée) et y voit le bandeau reçu par la TV. Seul reste possible en phase 2 : le bouton « Ouvrir la salle de ma TV sur Internet » (`POST /api/quiz/scope` **local**, PIN, confirmation sur la TV), à reprendre dans un futur cahier. **Statut : RETIRÉ (A) ; EN ATTENTE phase 2 (bouton local seulement).** Ne pas exécuter le corps ci-dessous.
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (lien d'application, jeton hors URL) · statut : page web **PRÊT (après w20-03)** ; partie `S/` **ATTEND sortie du gel**
> **Groupe : W20-S4** (câblage) · prérequis : w20-03 (page), w20-05 (ouverture depuis le téléphone) · porte : `gradle :play-server:test --tests 'castbridge.play.web.*'` + `cd android && tools/agents/gradle-lock.sh gradle :sender:compileDebugKotlin`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 2 j) · audit Opus : échantillon

**Vague 20 · Effort M · Modèle : sonnet.** Conception : `DESIGN-W20` § 1.6, § 5.2. Branche `claude/sonnet-w20-06`. Rapport : `docs/agent-reports/sonnet-w20-06.md`. Deux moitiés **séparables** : (A) page web dans `server-play/src/main/resources/static/play/` (pendant le gel) ; (B) `S/QuizScreen.kt` et le manifeste du téléphone (après le gel). Aucun `R/`, aucun `C/tv/`.

## Objectif
(A) Page `/play` et `/play/j/{code}` : code `XXXX-XXXX` tolérant (casse, I/L/O), pseudonyme (règles `Pseudonym` du cœur **rejouées côté serveur** : la page n'affiche que le motif du refus), case « 13 ans ou plus, ou un parent m'accompagne », bandeau « Partie sûre » **reçu** (`safety`), mêmes rôles qu'aujourd'hui (`player`, `audience`, `friend`, `candidate`), « Signaler ce joueur », « Quitter », jeton en `sessionStorage` (jamais dans l'URL ; `/play/j/<code>` ne porte **que** le code), identifiant d'appareil navigateur aléatoire en `localStorage` (`deviceHash` côté serveur), transports WS → SSE → long-poll, texte « réseau lent » si RTT > 1 500 ms, ≤ 60 Ko, aucune dépendance externe, Android 8 / Safari 14 ; `SafetySignAgreementWebTest` : la page rend, pour chaque ligne de `safety-table.json` (w20-01), le même mot, la même forme et la même couleur (test JVM qui charge `play.js` dans un évaluateur minimal ? Non : test de **contrat de données** : la page lit `level/word/shape/text` tels quels et la table de couleurs `play.css` est générée depuis `SignalColors` par un test qui compare les hexadécimaux).
(B) App : onglet Quiz ⇒ carte « Partie Internet » (visible **seulement** si le téléphone a Internet, sinon ligne noire « Internet : non connecté ») : saisir un code, scanner un QR (caméra existante si présente, sinon saisie), lien d'application `castbridge://play?code=` (intent-filter additif) et lien web `https://bridge.sti-cm.com/play/j/*` (App Links, `assetlinks.json` **à publier par le propriétaire** : w20-08 le documente) ⇒ WebView sur `/play/j/<code>?app=1&dev=<installId haché>` ; avec une TV de confiance en salle d'attente : bouton « Ouvrir la salle de ma TV sur Internet » ⇒ `POST /api/quiz/scope` (PIN) ⇒ texte « Confirmez sur la TV avec la télécommande ».

## Pourquoi (preuves)
- `S/QuizScreen.kt:46-49, 105-108, 168-176` : le téléphone joue déjà dans une WebView sur la page `/quiz` de la TV : **même** approche pour `/play` (un seul client web).
- `S/QuizScreen.kt:138, 183-195` : `POST /api/quiz/open` et `GET /api/quiz` avec le PIN : modèle de « Ouvrir la salle de ma TV ».
- `android/core/src/main/resources/castbridge/quiz/play.html` (24 308 o) : base de la page `/play` (copiée par w20-03).
- `docs/QUIZ.md` § 3 : « Sans app : scanner le QR … Même Wi-Fi que la TV » : l'Internet lève cette contrainte, le texte de la page le dit.

## Fichiers possédés
- (A) `server-play/src/main/resources/static/play/{play.html, play.js, play.css}` (zones : transport, bandeau, âge, signalement ; w20-03 a créé les fichiers), `server-play/src/test/kotlin/castbridge/play/web/{SafetySignAgreementWebTest, PageBudgetTest}.kt`.
- (B) `S/QuizScreen.kt` (zone additive : carte Internet, WebView `/play`), `S/quiz/PlayDeepLink.kt` (pur : analyse de `castbridge://play?code=` et du lien web, tests `android/sender/src/test/.../PlayDeepLinkTest.kt`), `android/sender/src/main/AndroidManifest.xml` (intent-filters additifs), `docs/PHONE-PLAYER.md` ou `docs/QUIZ.md` § 3 (zone additive : « Depuis Internet »).
- Interdit : `R/`, `C/`, `backend/`.

## Étapes
1. **Rouge** : `PageBudgetTest` (≤ 60 Ko, aucun `<script src=` externe, aucun `fetch` hors `/play/`), `SafetySignAgreementWebTest`, `PlayDeepLinkTest` (code normalisé, hôte strict, refus de `castbridge://play?code=<script>`).
2. (A) page : transport, bandeau, âge, pseudonyme, signalement, `sessionStorage`, « réseau lent ».
3. (B) app : carte, lien d'application, WebView (`?app=1&dev=`), bouton d'ouverture.
4. **Vert** (A) ; (B) compile ; relevé humain : Android 8 WebView, Safari 14, Chrome Android derrière données mobiles (Orange/MTN CM si possible) : 3 transports.

## Critères d'acceptation
- Le jeton n'apparaît dans aucune URL, aucun `console.log`, aucun `href`. `/play/j/<code>` sans JavaScript affiche au moins le code et « Activez JavaScript ».
- Sans Internet sur le téléphone : l'onglet Quiz est **identique** à aujourd'hui (aucune roue, aucun bouton Internet actif).
- Bandeau : couleur, forme et mot identiques à la TV pour chaque ligne de la table.

## Cas limites
- Code d'une salle fermée ⇒ « Cette partie est terminée » (pas « code faux »). Deux onglets même navigateur ⇒ second = spectateur. Lien d'application reçu pendant une partie locale ⇒ demande « Quitter la partie en cours ? ».

## À ne pas faire
- Aucun suivi tiers, aucune police externe, aucun cookie. Pas de chat. Pas de calcul de couleur côté client. Pas de modification de la page `/quiz` locale.

## Rapport
Format RAPPORT + `SYMBIOSE: cap=play1 (web, app) · proto=play-v1 · reason=PLAY_* · deux écrans=SafetySignAgreementWebTest` + poids de la page + navigateurs essayés.
