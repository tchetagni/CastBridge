# w20-01 — Cœur : `PlayScope` (trois périmètres), `SafetySign` (signe « Partie sûre »), `GameAuthority` + `LocalAuthority`, codes `Reason` `PLAY_*`
<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (sémantique du signe) · statut : FAIT (branche `claude/w20-01-scope-signe-autorite`, non fusionnée) — **premier cahier de la vague**
> **Groupe : W20-S0** (cœur pur) · prérequis : aucun · porte : `tools/core-harness/run.sh :core:test --tests 'castbridge.core.quiz.online.*' --tests 'castbridge.core.quiz.*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 20 · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md` § 1, § 5.3. Branche `claude/sonnet-w20-01`. Rapport : `docs/agent-reports/sonnet-w20-01.md`. Dire « CastBridge » (téléphone) / « CastBridge-TV ». Règle W15 R5 : aucun fichier `S/`, `R/`, aucun fichier serveur. Cœur pur : `C/quiz/online/` (nouveau dossier) seulement, plus une zone additive dans `C/sync/Reason.kt` **si** w19-01 est fusionné (sinon : constantes locales `PlayReason` et une note dans le rapport).

## Objectif
Donner au Quiz, dans le cœur partagé, (1) les **trois périmètres** `PlayScope { TV_ONLY, LAN, INTERNET }` avec icône, étiquette française et promesse ; (2) `SafetySign.of(facts): SafetyView` : la table figée du § 1.3 (périmètre × niveau `SignalLevel` × texte × action), identique pour les deux applications ; (3) l'interface `GameAuthority` et `LocalAuthority(room: QuizRoom)` qui enveloppe la salle d'aujourd'hui **sans la modifier** ; (4) les codes de refus `PLAY_BAD_CODE`, `PLAY_ROOM_FULL`, `PLAY_ROOM_GONE`, `PLAY_TICKET_REFUSED`, `PLAY_BANNED`, `PLAY_TLS_INVALID`, `PLAY_SCOPE_FORBIDDEN` (réessayable ou non, français). Rien ne touche au réseau.

## Pourquoi (preuves)
- `C/ux/TvSignal.kt:9-14` : `SignalLevel` (GREEN/ORANGE/RED/BLACK, forme + mot) et la règle « Internet absent = NOIR » (`:7`) : le signe doit la respecter.
- `C/quiz/QuizRoom.kt:102-135, 306, 489-531` : `join/act/view/awaitChange` : la surface exacte que `GameAuthority` doit exposer ; `view` est déjà le contrat des clients.
- `C/quiz/QuizHttp.kt:40-53` : les routes `/quiz/*` consomment `QuizRoom` directement : `LocalAuthority` doit être un **adaptateur** (aucune ligne de `QuizHttp` changée dans ce cahier).
- Exigence du propriétaire (2026-10-03) : « un signe que la partie est sûre : TV seule, en réseau local, depuis internet ».

## Fichiers possédés
- Nouveaux : `C/quiz/online/PlayScope.kt`, `C/quiz/online/SafetySign.kt` (`SafetyFacts`, `SafetyView`, `TlsState`, `Link3`), `C/quiz/online/Authority.kt` (`GameAuthority`, `LocalAuthority`), `C/quiz/online/PlayReason.kt`, `CT/quiz/online/PlayScopeTest.kt`, `CT/quiz/online/SafetySignTest.kt`, `CT/quiz/online/LocalAuthorityTest.kt`, `CT/quiz/online/SafetySignAgreementTest.kt`.
- Zone additive : `C/sync/Reason.kt` (ajout de codes à l'`enum`, à la fin) **seulement si** le fichier existe sur `integration/agents`.
- Interdit : tout autre fichier de `C/quiz/`, `C/ux/TvSignal.kt` (lu, jamais modifié).

## Étapes
1. **Rouge** : `SafetySignTest` (une ligne de test par ligne de la table § 1.3, 15 cas), `PlayScopeTest` (étiquettes, icônes, `allowsRemote`, `requiresInternet`, `noOutboundNetwork`), `LocalAuthorityTest` (scénario Duel 3 joueurs : même séquence de `view` qu'en appelant `QuizRoom` directement, réponse en double = `SAME`, rejeu inoffensif). Coller la sortie rouge dans le rapport.
2. `PlayScope` : `enum class PlayScope(val icon: String, val label: String, val promise: String)` + `fun allowsRemotePlayers()`, `fun mayUseNetwork()` (`TV_ONLY`/`LAN` ⇒ false).
3. `SafetySign.of(facts)` : première cause par gravité (`SignalLevel.severity`), textes du § 1.3 **mot pour mot**, `detail` = toutes les causes ; `TV_ONLY` jamais autre que vert ; TV sans Internet ⇒ `BLACK` jamais `RED`.
4. `GameAuthority` : `view(token): Map<String, Any?>`, `act(token, action, questionId, choice, arg): QuizRoom.Act`, `join(code, name, token, device): QuizRoom.JoinResult`, `awaitChange(since, timeoutMs): Long`, `safety(): SafetyView`, `scope: PlayScope`. `LocalAuthority(room, facts: () -> SafetyFacts)` délègue ; `view` ajoute la clé additive `"safety": {scope, level, word, text, action}` (les anciens clients ignorent les clés inconnues : `play.html` lit par nom).
5. `PlayReason` : codes, HTTP, réessayable, français (§ 1.3 et § 2.8) ; si `Reason` existe : ajout à l'`enum` + test que les nouveaux codes ont un message.
6. `SafetySignAgreementTest` : charge `castbridge/quiz/online/safety-table.json` (ressource de test écrite par ce cahier : la table § 1.3) et vérifie `SafetySign.of` contre chaque ligne ; cette ressource sera relue par w20-06 (page web) pour prouver la **même** sémantique.
7. **Vert** : porte ; `:core:test` complet dans le rapport (échecs préexistants `LearnLotsTest` ×10 et `BaseContentTest` listés à part).

## Ajout du propriétaire (2026-10-03) : délai entre deux questions
« Entre 2 questions laisse 1 ou 2 s de latence pour pouvoir synchroniser les parties en ligne. » Cœur pur `C/quiz/online/PlayTiming.kt` : `INTER_QUESTION_GAP_MS = 1500`, plage 1000..2000 (`gap(demandé)` borne), `gapFor(scope)` = 0 pour `TV_ONLY` et `LAN` (comportement inchangé), délai borné pour `INTERNET`. Après la révélation, la question suivante est annoncée aussitôt avec `opensAtServerMs = revealAtServerMs + gap` (horloge serveur) ; personne ne répond avant ; les points se comptent depuis `opensAtServerMs` ; une annonce tardive démarre aussitôt, fenêtre raccourcie seulement du temps mesuré par le serveur. Test : `CT/quiz/online/PlayTimingTest.kt`. Protocole : `docs/PLAY-PROTOCOL.md` § Timing (w20-02 le complète).

## Critères d'acceptation
- 100 % des lignes de la table § 1.3 testées ; `TV_ONLY` ⇒ vert quelles que soient les `facts` ; `INTERNET` + `kidProfile` sans autorisation ⇒ `BLACK` ; `INTERNET` + `tls = INVALID` ⇒ `RED` avec action « Fermer Internet ».
- `LocalAuthorityTest` : la séquence `view().v/stage/duel.phase` est **identique** à celle de `QuizRoom` sur le même scénario à graine (`random = Random(42)`, `clock` injectée).
- Aucun import `java.net`, `java.io` dans `C/quiz/online/` (test de source `OnlinePurityTest`).
- Tests existants `QuizRoomTest`, `QuizSoloTest`, `QuizRoomHistoryTest` inchangés et verts.

## Cas limites
- `facts.scope = INTERNET` mais `serverLink = LOST` depuis 59 s ⇒ orange « liaison en reprise (59 s) » ; 60 s ⇒ rouge « Internet perdu : la partie continue en local ».
- `LAN` avec `guestsViaQr = 2` ⇒ orange, texte « 2 invités dans le Wi-Fi de la TV ».
- `clockDoubt = true` en `INTERNET` ⇒ noir « Internet : vérifiez l'heure de la TV » (le ticket serait refusé).

## À ne pas faire
- Ne pas modifier `QuizRoom`, `QuizHttp`, `play.html`, `TvSignal`. Ne pas écrire de client réseau. Ne pas inventer de couleur hors `SignalColors`. Ne pas toucher à `S/`, `R/`, `backend/`.

## Rapport
Format RAPPORT du gabarit + ligne `SYMBIOSE: cap=play-scope · proto=inchangé · reason=PLAY_* · deux écrans=SafetySignAgreementTest` + sortie rouge puis verte + liste des textes français ajoutés.
