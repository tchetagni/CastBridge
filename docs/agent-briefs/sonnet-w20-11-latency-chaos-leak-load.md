# w20-11 — Tests de garantie : `LatencySim` (0 / 150 / 600 ms, pertes), chaos de reprise (joueur, TV hôte, serveur en drain), `NoAnswerLeakTest` sur socket (fuzz 1 000 parties), charge 500 salles, compatibilité `play-v1` (persona)
<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune (tests) · statut : PRÊT (après w20-03 et w20-07)
> **Groupe : W20-S1** (service, tests) · prérequis : w20-03, w20-07 fusionnés · porte : `gradle :play-server:test --tests 'castbridge.play.chaos.*'` (≤ 10 min sur un portable) + `:core:test --tests 'castbridge.core.quiz.online.*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 2 j) · audit Opus : non

**Vague 20 · Effort M · Modèle : sonnet.** Conception : `DESIGN-W20` § 1.5, § 2.3, § 2.6, § 2.7, § 4 (T-5, T-6), § 8 (risques 1, 2, 4, 12). Branche `claude/sonnet-w20-11`. Rapport : `docs/agent-reports/sonnet-w20-11.md`. Tests seulement : aucun fichier `main/` modifié (si un crochet d'injection manque : `QUESTION:`).

## Objectif
(1) `LatencySim` (pur, `CT/quiz/online/`) : transport en mémoire avec latence par connexion (constante ou tirée : 0, 150, 300, 600, 1 500 ms), gigue ±30 %, perte 0-5 %, coupure programmée ; branché sur `PlayTransport` (w20-02) et sur un `FaultProxy` TCP local pour les tests socket (réutiliser `CT/compat/FaultProxy` de w19-04 **s'il est fusionné**, sinon un proxy minimal de 80 lignes dans `server-play/src/test`). (2) `ChaosPlayTest` à graine : 5 tables × 8 joueurs, 40 évènements aléatoires (coupure joueur 5-90 s, coupure TV 10-120 s, serveur en drain à mi-partie, messages dupliqués, `resume` tardif, code renouvelé) ; **invariants** : un joueur a au plus une réponse comptée par question ; aucun score ne recule ; chaque table finit en `FINISHED` ou `ABANDONED(raison)` ; chaque client finit avec un état **affichable** et une raison (jamais une attente muette > 60 s) ; la TV repasse en local après 60 s **exactement** (± 1 tick). (3) `FairnessTest` : 8 joueurs à 0/150/300/600 ms qui répondent **au même instant local** ⇒ écart de points ≤ 8 sur 1 000 ; joueur relayé par la TV (RTT TV 300) ⇒ idem. (4) `NoAnswerLeakSocketTest` : 1 000 parties sur socket, capture de **toutes** les trames : aucune ne contient la bonne réponse avant `reveal` (comparaison sur l'index **et** le texte de la bonne réponse). (5) `PlayLoadTest` (marqué lent, exécuté à la demande `-Dplay.load=true`) : 500 salles × 8 clients WS simulés sur une JVM, 10 min simulées ; mesure : mémoire, messages/s, p99 de latence de diffusion ; seuils **indicatifs** (mémoire < 250 Mo, p99 < 200 ms) ; résultat dans le rapport, pas une porte. (6) Persona `play-v1` pour la matrice W19 (`CT/compat/personas/play-v1.json`) **si** w19-03 est fusionné ; sinon noté.

## Pourquoi (preuves)
- `DESIGN-W19` § 1.4 et § 4.2 : `ChaosResumeTest` à graine et `FaultProxy` sont le modèle du projet pour les garanties de reprise : W20 l'applique au jeu.
- `C/quiz/QuizDuel.kt:134` `points(elapsedMs, windowMs)` : permet de calculer l'écart attendu par ms de RTT.
- `docs/QUIZ.md` § 4 : « La bonne réponse n'est jamais envoyée avant la clôture (testé) » : à **re-prouver** sur le transport Internet.

## Fichiers possédés
- Cœur (tests) : `CT/quiz/online/{LatencySim, LatencySimTest, FairnessTest}.kt`.
- Service (tests) : `server-play/src/test/kotlin/castbridge/play/chaos/{ChaosPlayTest, NoAnswerLeakSocketTest, PlayLoadTest, DrainTest, MiniFaultProxy}.kt`, `server-play/src/test/resources/chaos/seeds.txt` (graines qui ont déjà trouvé un défaut, rejouées à chaque fois).
- Zone additive : `CT/compat/personas/` (persona `play-v1`) si disponible.
- Interdit : tout `src/main/`.

## Étapes
1. `LatencySim` + `FairnessTest` (rouge si la compensation de w20-07 n'est pas branchée : sortie dans le rapport).
2. `ChaosPlayTest` (100 graines en CI, 1 000 en local) ; chaque défaut trouvé ⇒ graine ajoutée à `seeds.txt` et **`QUESTION:`** au coordinateur avec la trace (ce cahier ne corrige pas `main/`).
3. `NoAnswerLeakSocketTest`, `DrainTest` (SIGTERM simulé par `context.close()` ⇒ `roomGone` reçu par tous, nouvelles salles refusées pendant le drain).
4. `PlayLoadTest` à la demande ; rapport chiffré.

## Critères d'acceptation
- 100 graines vertes ; invariants écrits comme assertions nommées (le message d'échec dit l'invariant et la graine).
- `FairnessTest` : écart ≤ 8 points ; sans compensation (test témoin avec `RttBook` neutralisé) l'écart dépasse 10 : la compensation est **prouvée utile**.
- `NoAnswerLeakSocketTest` : 0 fuite sur 1 000 parties, jokers compris.

## Cas limites
- Latence 1 500 ms (au-delà du plafond) : le joueur voit « réseau lent », perd ≤ 25 points par question, jamais exclu. Drain pendant la grâce d'une question : la question est **clôturée** avant `roomGone` (les scores finaux sont cohérents).

## À ne pas faire
- Ne pas modifier le code de production pour faire passer un test ; ne pas désactiver un invariant ; ne pas réduire le nombre de graines sous 100 en CI sans le dire.

## Rapport
Format RAPPORT + `SYMBIOSE: cap=— · proto=play-v1 (persona) · reason=— · deux écrans=ChaosPlayTest` + tableau de mesures (`PlayLoadTest`) + graines trouvées et `QUESTION:` associées.
