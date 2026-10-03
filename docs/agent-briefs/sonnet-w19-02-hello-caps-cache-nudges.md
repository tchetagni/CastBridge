# w19-02 — Cœur : poignée de main de version et de capacités, additive : `/api/hello` enrichi, `Caps` (+ capacités impliquées des TV anciennes), en-tête `X-CB-App` du téléphone, cache `cap.<tvId>` dans `PinBook`, `UpdateNudge` (encouragements qui ne bloquent jamais), `FeatureGate` (repli par capacité absente)

<!-- routage Fable 2026-10-03 -->
> **Amendement (W20, Fable, 2026-10-03)** : réserver dans `C/sync/Caps.kt` deux capacités **additives** pour le Quiz en ligne, sans les émettre encore : `quizscope` (la TV accepte `POST /api/quiz/scope`, confirmation à la télécommande, w20-05) et `play1` (la TV sait rejoindre la passerelle `play-v1` en connexion sortante). Repli téléphone si absentes : le bouton « Ouvrir la salle de ma TV sur Internet » est **masqué** avec la ligne « Cette TV ne sait pas encore jouer sur Internet : mettez-la à jour ». Rien d'autre ne change : voir `docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md` § 5 et `SONNET-WAVE20-INDEX.md`.
> **Modèle : sonnet** · escalade : audit Opus en échantillon (route publique, identité `id`) · statut : PRÊT (après w19-13)
> **Groupe : W19-S1** (versions) · prérequis : w19-01, w19-13 fusionnés ; `tvctx-01` **non exécuté** : ce cahier fournit `id` ; si `tvctx-01` est fusionné entre-temps, se rebaser et ne pas le refaire · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.sync.*' --tests 'castbridge.core.trust.PinBookTest' --tests 'castbridge.core.trust.Credential*' --tests 'castbridge.core.*ReceiverServer*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 19 · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W19 § 1.1 (S-6, S-10), § 2 (tout), D-W19-1, D-W19-2, D-W19-5, D-W19-10. Branche `claude/sonnet-w19-02`. Rapport : `docs/agent-reports/sonnet-w19-02.md`. Règle W15 R5 : aucun `S/`, `R/`.

## Objectif
(1) `C/sync/Caps.kt` : constantes de capacités **stables** (`envelope, sync, resume2, dedup, filing2, queue, cbtn, wd, wdpersist, slowed, copybadge, lots1, quizlots, transfer2, resume, diagwd, pinhello`), chacune avec KDoc « depuis quelle version TV / téléphone, repli » ; `Caps.PROTOCOL = 8` ; `Caps.impliedBy(appVersion: String?): Set<String>` pour une TV qui ne déclare rien (table **établie par lecture** de `git show tv-0.14.22-beta:…`, `tv-0.14.24`, `tv-0.14.25`, `tv-0.14.26` : quelles routes existent ; aucune valeur de mémoire ; chaque ligne cite la preuve) ; `Caps.current()` de la TV = ensemble réel (branchement par lambda injectée). (2) `/api/hello` **additif** : `protocol`, `appVersion`, `versionCode`, `id` (8 hex, `InstallKey` W7 ; jamais `installId` de confiance), `caps`, `edition`, `storage`, `routes`, `lots{schema}`, `filing{version}` ; `v:"0.7"`, `app`, `pinRequired` **inchangés** ; `appVersion`/`versionCode` fournis par le constructeur de `ReceiverServer` (paramètres optionnels, défaut `null` ⇒ clés omises). (3) `HelloV2` (`C/sync/HelloV2.kt`) : `parse(body): TvCaps` tolérant (une TV ancienne ⇒ `protocol = 7`, `caps = impliedBy(null)` = vide) ; `TvCaps.has(cap)`. (4) `X-CB-App` : `TvCredential` (seule voie, `C/trust/Credentials.kt`) ajoute l'en-tête `X-CB-App: castbridge/<versionName> (<versionCode>); proto=8; caps=<csv>` sur **toute** requête, valeurs injectées (`AppIdentity`) ; HELLO Bluetooth : ligne `app=` additive dans la requête (drapeau `HELLO_HAS_APP`, comme `HELLO_HAS_INSTALL_ID`), ignorée par une TV ancienne. (5) `PinBook` : clé `cap.<tvId>` = `proto \t versionCode \t appVersion \t caps(csv) \t edition \t seenMono` ; `TvCaps.of(tvId)` ; périmé > 24 h ou `versionCode` différent ; effacé par « Oublier » ; jamais par adresse. (6) `C/sync/UpdateNudge.kt` : `decide(me: AppIdentity, peer: TvCaps, wanted: Set<String>, lastShownMono, now): Nudge?` (texte français **avec la fonction qui manque**, « marche quand même », une fois par 24 h et par TV, jamais pendant une copie : paramètre `busy`) ; `UpdatePhoneNudge` (côté TV, à partir de `X-CB-App`). (7) `C/sync/FeatureGate.kt` : `allow(cap, caps): Gate(Allowed | Fallback(text) | Hidden(text))` selon la table DESIGN § 2.4.

## Pourquoi (preuves)
- `C/tv/ReceiverServer.kt:554-555` : `/api/hello` = `{"app","v":"0.7","pinRequired"}` seulement ; `:1746` `VERSION = "0.7"` ; `:919-920` `/api/transfer/caps` ne couvre que le transfert.
- `C/trust/HelloHandler.kt:53` : la version TV voyage en Bluetooth, pour un pair de confiance ; rien n'est rangé.
- `C/trust/PinBook.kt:47-197` (R-10) : clés par identité de TV, hors sauvegarde ; W18 y ajoute `wd.<tvId>` : même toit pour `cap.<tvId>`.
- Terrain : TV 0.14.23 avec un sélecteur Quiz défectueux, versions mixtes sur la clé USB ; le téléphone ne peut ni savoir ni dire.

## Fichiers possédés
Nouveaux : `C/sync/Caps.kt`, `C/sync/HelloV2.kt`, `C/sync/UpdateNudge.kt`, `C/sync/FeatureGate.kt`, `C/sync/AppIdentity.kt`, `CT/sync/CapsTest.kt`, `CT/sync/HelloV2Test.kt`, `CT/sync/UpdateNudgeTest.kt`, `CT/sync/FeatureGateTest.kt`. Zones additives : `C/tv/ReceiverServer.kt` (la ligne `/api/hello` ⇒ `HelloV2.body(...)` ; constructeur : `appVersion`, `versionCode`, `caps` optionnels ; ≤ 20 lignes), `C/trust/Credentials.kt` (`X-CB-App`, ≤ 15 lignes), `C/trust/PinBook.kt` (`cap.<tvId>`, ≤ 40 lignes), `C/tv/BtProtocol.kt` + `C/trust/HelloHandler.kt` (ligne `app=` additive, ≤ 20 lignes ; **aucune trame changée**). **Hors zone** : `C/sync/Reason.kt`, `RejectionLedger`, `RetryPolicy` (w19-01), `C/sync/SyncState.kt` (w19-06), `S/`, `R/`.

## Étapes
1. **Rouge** : (a) `HelloV2Test` : l'ancienne forme est un **sous-ensemble octet pour octet** du nouveau corps (`app`, `v:"0.7"`, `pinRequired` en premier, mêmes valeurs) ; `parse` d'un corps ancien ⇒ `protocol 7`, caps impliquées ; corps nouveau ⇒ tout lu ; clé inconnue ignorée ; (b) `CapsTest` : toute capacité a une KDoc « depuis » et un repli ; `impliedBy("0.14.24-beta")` = ensemble cité avec preuve ; (c) `PinBookTest` (ajouts) : écriture/lecture `cap.<tvId>` par clé d'écran via `tvId`, périmé à 24 h, invalidé par `versionCode` différent, effacé par oubli, jamais via `127.0.0.1`/`192.168.49.1` ; (d) `UpdateNudgeTest` : TV sans `dedup` ⇒ texte nommant les doublons + « partira quand même » ; une fois par 24 h ; jamais si `busy` ; protocole < P-3 ⇒ texte générique avec la version ; P+2 ⇒ « trop ancienne pour la file… copie simple » ; jamais de blocage (aucune variante `Block`) ; (e) `FeatureGateTest` : table § 2.4 ligne par ligne (`wd` absent ⇒ `Hidden`, `dedup` absent ⇒ `Fallback`, `slowed` absent ⇒ seuil 90 s) ; (f) `HelloCompatTest` (existant) : TV ancienne ⇔ téléphone nouveau et l'inverse restent verts avec la ligne `app=`.
2. Implémenter ; `AppIdentity(versionName, versionCode, proto, caps)` injectée (les apps la rempliront en w19-09/10 ; défaut tests `castbridge/0.0.0 (0)`).
3. **Vert** : porte ; `:core:test` complet.

## Critères d'acceptation
- Un téléphone **ancien** (analyseur `TvClient.str/num`) lit `v` et `pinRequired` inchangés : test qui n'utilise que ces deux lectures sur le nouveau corps.
- Aucune capacité en dur hors `Caps.kt` (test de source).
- Aucune information d'appareil (adresse, `installId`) dans `/api/hello` ni dans `X-CB-App`.

## Cas limites
`appVersion` null (tests) ⇒ clés omises ; `versionCode` plus petit que celui en cache (rétrogradation) ⇒ cache remplacé et encouragement « TV rétrogradée » ; `caps` vide mais `protocol` 8 ⇒ cru tel quel ; cache corrompu ⇒ ignoré ; deux TV même modèle (R-10) ⇒ caches distincts.

## À ne pas faire
Modifier `v` ; rendre un champ obligatoire ; bloquer un parcours sur une version ; lire `BuildConfig` dans `core` ; toucher `S/`, `R/`.

## Rapport
RAPPORT + `SYMBIOSE: cap=envelope,sync,resume2,… (déclaration) · proto=8 (additif, note PROTOCOL-CHANGES à écrire par w19-05) · reason=— · deux écrans=UpdateNudgeTest (téléphone) / UpdatePhoneNudge (TV)`.
