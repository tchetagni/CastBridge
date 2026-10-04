# w22-15 — Défi des 10 000 : cœur pur (échelle et sa validation, état de partie, retrait aux paliers 5/8/10/13, 50:50, journal signé `cbm1`, pack signé, simulateur de rendement)
<!-- routage architecte 2026-10-04 (W22, niveau 1 en parallèle, hors chemin critique) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (aucune création de valeur sur la TV, journal complet et vérifiable) · statut : **ATTEND w22-03** (cœur pur : permis pendant le gel)
> **Groupe : W22-DEFI** · porte : `:core:test --tests 'castbridge.core.wallet.millions.*'` (par `tools/agents/gradle-lock.sh`)
> **Jauge : ≈ 350 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` § 13 (13.1 règles, 13.2 échelle, 13.3 M-2, 13.4 journal et pack). Branche `claude/w22-15-defi-coeur`. Rapport : `docs/agent-reports/sonnet-w22-15.md`.

## Objectif (autonome)
Nouveau mode **solo, hors ligne, production seulement** : mise 500 NDEM, 15 questions de difficulté croissante, une erreur (ou 30 s dépassées) = fin, gain 0 ; un seul joker 50:50 ; retrait possible **seulement** juste après une bonne réponse aux questions 5, 8, 10, 13 ; 10 000 NDEM au maximum. **Distinct** du Millionnaire existant (`android/core/src/main/kotlin/castbridge/core/quiz/QuizRoom.kt`, `QuizGame.kt` : ne pas les modifier ni les réutiliser, sauf le type de question). Livrer les règles pures, le format du pack (reçu du serveur) et du journal (produit par la TV), sans aucune opération qui crédite un solde.

## Fichiers possédés
- **Nouveaux** : `android/core/src/main/kotlin/castbridge/core/wallet/millions/{MillionsLadder,MillionsGame,MillionsPack,MillionsJournal,MillionsOfflineBudget,MillionsRtpSimulator}.kt` ; tests `android/core/src/test/kotlin/castbridge/core/wallet/millions/**` ; `tools/wallet/millions-vectors.json` (`castbridge-millions-vectors-v1` : packs et journaux dorés signés par des clés de test déterministes, journaux impossibles avec leur motif).
- **Interdit** : `C/quiz/**`, `C/wallet/*.kt` de w22-03 (consommés), `R/`, `S/`, `backend/**`.

## Spécification
1. `MillionsLadder(values[15], stake, stops={5,8,10,13})` : `validate()` refuse (texte français) une échelle non strictement croissante, aux incréments non croissants, avec Q15 ≠ 10 000, ou dont le palier 5 dépasse la mise ; défaut = échelle **B** du § 13.2 (50 … 10 000), mise 500.
2. `MillionsGame` (pur, horloge monotone injectée) : `start()` (la mise est « en jeu »), `show(q)`, `fifty()` (une fois ; retire 2 mauvaises réponses désignées **par le pack** pour que le serveur puisse vérifier), `answer(choice, msSinceShown)`, `withdraw()` permis seulement à l'état `AT_STOP(k ∈ stops)` ; états `ASKING(k) | AT_STOP(k) | LOST(k) | WITHDRAWN(k, gain) | WON | FORFEIT(k)` ; temps > 30 s (valeur du pack) = perdu ; `snapshot()/restore()` pour reprendre après une coupure à la même question.
3. `MillionsPack` (`cbk1`, signé par la clé « portefeuille » de l'API, domaine `castbridge-millions-pack-v1`, même enveloppe que `cbw1` de w22-03) : `packId, id (identité), from, until (≤ 14 j), ladder, stake, timeSec, maxGamesPerDay, levels[15][≤ 20] = {qid, texte, 4 choix, bonne, deux mauvaises du 50:50}` ; `verify(token, ring, identity, TvClock)` ; tirage de la question suivante : première question non jouée du niveau, dans l'ordre du pack (déterministe, vérifiable par le serveur).
4. `MillionsJournal` (`cbm1`, signé par la clé d'installation via l'interface d'`android/core/src/main/kotlin/castbridge/core/owner/InstallSigner.kt`, domaine `castbridge-millions-journal-v1`) : `gameId` (128 bits), `packId`, `ladderVersion`, `[(qid, choix, fifty, ms)]`, fin, temps de début/fin, chaîne d'empreintes ; `impossible(journal, pack)` ⇒ motif (question hors pack, niveaux dans le désordre, retrait hors palier, gain ≠ échelle, 50:50 deux fois, réponse après la fin, ms < 300, partie au-delà de `maxGamesPerDay`) — **même liste** que le vérificateur Java de w22-16 (vecteurs).
5. `MillionsOfflineBudget` : disponible pour miser = NDEM confirmés du dernier `cbw1` − mises des journaux non confirmés ; refuse une mise si le disponible < mise ou si `maxGamesPerDay` est atteint (jour de `TvClock`) ; gains des journaux non confirmés exposés **séparément** (« en attente »), jamais ajoutés au disponible.
6. `MillionsRtpSimulator` : programmation dynamique du joueur optimal (rendement attendu pour une échelle et 15 probabilités de réussite, avec et sans 50:50) ; sert aux tests et à l'administration.

## Critères d'acceptation (mutations au rapport)
- Règles : retrait refusé aux questions 1-4, 6, 7, 9, 11, 12, 14 ; permis à 5, 8, 10, 13 juste après une bonne réponse ; erreur ⇒ gain 0 (mutation : repli sur le dernier palier ⇒ échec) ; 50:50 deux fois refusé ; 30 s dépassées ⇒ perdu ; reprise après `restore()` à la même question.
- Simulateur : échelle B avec les probabilités supposées du § 13.2 ⇒ ≈ 460 NDEM (± 5) ; A ⇒ ≈ 920 ; C ⇒ ≈ 400.
- `MillionsOfflineBudget` : jamais de mise au-delà du disponible (propriété sur 2 000 suites) ; les gains en attente n'entrent jamais dans le disponible.
- Vecteurs : packs et journaux dorés vérifiés ; chaque journal impossible refusé avec son motif.
- Test de source : aucune méthode publique du paquet n'augmente un solde.
