# w13-06 — CastBridge-TV (écran) : bandeau « un téléphone attend le code », puce d'accueil, page D-pad « Derniers blocages », réglage du bandeau
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune (écran ; la logique est dans w13-05) · statut : PRÊT
> **Groupe : W13-c** (vague W13, tranche S2) · prérequis : w13-05 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 300 k jetons entrée / 14 k sortie** (effort S-M) · audit Opus : non

**Vague 13c (TV, écran) · Effort S-M (≈ 1 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W13` § 3.5 (TV), § 3.6 (D-pad), § 3.7, D-W13-3, D-W13-5. Branche `claude/sonnet-w13-06`. Rapport : `docs/agent-reports/sonnet-w13-06.md`.

## Objectif
(1) `TvService` branche `onAuthRefused` ⇒ `PinNeededPolicy` ⇒ `notice(...)` (bandeau existant) avec les trois textes § 3.5 ; en mode enfant, texte « déverrouillez le mode enfant pour l'afficher » (jamais le code) ; (2) la puce d'accueil (`● Prêt à recevoir · code 77••••`) ajoute « · un téléphone attend le code » pendant 60 s ; (3) page « Connexion & réglages » › « Derniers blocages » (10 lignes de `BlockerLogTv.lines()`, code support par ligne, focus initial sur « Fermer », ≥ 24 sp) ; (4) réglage « Prévenir quand un téléphone n'a pas le code » (oui par défaut, `TvPrefs` `pin_needed_banner`).

## Pourquoi (preuves)
- `R/TvService.kt:242-263` (construction de `ReceiverServer` : ajouter `onAuthRefused`), `:608` (`notice`), `:611` (`setStatus`) ; `R/PlayerActivity.kt:211,834` (`flash` = bandeau) ; `R/HomeScreen.kt:79,124-126` (puce « ready · code ») ; `R/HomeScreen.kt:274-289` (« Connexion & réglages ») ; `R/ParentalHub.kt:283` (`shownPin` masqué en mode enfant) ; `R/TvPrefs.kt:39-44` (getters/setters).
- Accessibilité D-pad : `DESIGN-W7` § 8.3 (focus initial sur le bouton sûr, ≥ 48 dp, ≥ 24 sp) ; `R/PairActivity.kt:88-99` (anneau de focus).

## Fichiers possédés
`R/TvService.kt` (zone : construction du serveur + une fonction `onAuthRefused`), `R/HomeScreen.kt` (puce + entrée « Derniers blocages »), `R/TvPrefs.kt` (une propriété), `R/PlayerActivity.kt` (statut de la puce : une ligne) ; nouveaux : `R/BlockersPanel.kt`, `R/PinNeededNotifier.kt`. **Hors zone** : `C/**`, `S/**`, `R/PairActivity.kt`, `R/ActivationActivity.kt`.

## Étapes
1. `PinNeededNotifier(service, policy, prefs)` : `onRefusal(peerKey, kind)` ⇒ `policy.onRefusal` ⇒ `Show.PIN_NEEDED` ⇒ `notice(if (ParentalHub.engineOrNull?.active() == true) "Un téléphone attend le code de la TV : déverrouillez le mode enfant pour l'afficher." else "Un téléphone essaie d'envoyer un fichier sans le bon code. Le code est : ${pin.take(2)}•••• (OK sur la puce pour l'afficher).")` ; `TOKEN_RENEWING` ⇒ « Un téléphone de confiance renouvelle son autorisation : rien à faire. » ; `TRIAL` ⇒ « Version d'essai : un téléphone a tenté un envoi, refusé. » ; `prefs.pin_needed_banner == false` ⇒ rien ; toujours sur le fil principal.
2. `HomeScreen` : `api.status()` renvoie aussi `pinNeededUntil` ; la puce ajoute « · un téléphone attend le code » si `now < pinNeededUntil`.
3. `BlockersPanel` : liste `BlockerLogTv.lines()` + `SupportCode` (recalculé côté TV avec `tvHash4` du nom de la TV) ; bouton « Fermer » (focus initial) ; « Effacer la liste » ; ouverture depuis « Connexion & réglages ».
4. Réglage : ligne « Prévenir quand un téléphone n'a pas le code · Oui/Non » dans le panneau de réglages existant.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -n 'pin_needed_banner' android/receiver/src/main/kotlin/castbridge/receiver/TvPrefs.kt` ⇒ 1 ligne ; aucune chaîne contenant le PIN entier dans `PinNeededNotifier.kt` (`grep -n '\$pin\b' …` vide) ; rapport : capture d'écran impossible hors ligne ⇒ décrire le parcours D-pad (DOWN jusqu'à « Derniers blocages », OK, BACK).

## Cas limites
`TvService` sans écran (`screen == null`) ⇒ bandeau différé au prochain écran ; TV verrouillée (serveur absent) ⇒ rien ; `pin` masqué en mode enfant ⇒ jamais révélé par le bandeau ni par la liste.

## À ne pas faire
Pas de nom ni d'adresse du téléphone (HTTP ne les connaît pas) ; pas de `Toast` système (bandeau existant seulement) ; ne pas modifier `PairActivity`.

## Rapport
`STATUT`, lignes modifiées de `TvService`, textes exacts, ce que w7-12/w7-14 devront conserver (crochet `onAuthRefused`, entrée du menu).
