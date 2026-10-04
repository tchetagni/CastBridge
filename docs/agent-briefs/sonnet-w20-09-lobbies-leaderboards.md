# w20-09 — Produit : salons publics (file d'attente par niveau/filière/langue, tables), classements (mondial, par pays ; schéma `castbridge_play`, Flyway), rétention et `DELETE /play/me`
<!-- routage Fable 2026-10-03 -->
> **Amendement 2026-10-04 — conception `docs/coordination/DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` § 1, § 4.** Décisions du propriétaire : seule une TV activée connectée à Internet joue ; téléphones = joueurs locaux relayés ; aucune page web ; aucune délégation. **Changements de périmètre** : (1) **aucun participant anonyme ni web** : une file de salon public n'admet que des **TV de production** authentifiées (ticket + activation, chemin de `sonnet-w20-04b`), jamais une TV d'essai ; chaque TV y amène ses joueurs relayés (tables remplies **par TV**, ≤ 8 sièges par TV) ; (2) classements **par identité de TV + siège** (code d'appareil signé de l'activation, haché avec le sel du service, + étiquette du siège), jamais par navigateur ni par téléphone ; (3) `DELETE /play/me` devient « effacer les données de jeu de **cette TV** », appelé **par la TV** (ticket) ; (4) aucune route publique lisible par un navigateur hors la page d'information ; (5) `BotScore` et « ≥ 4 humains distincts » s'entendent par siège ; les sièges d'une même TV comptent comme un seul foyer pour le seuil « humains distincts ». **Statut : phase 2, après le POC** (prérequis ajoutés : `sonnet-w20-04b` fusionné).
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (schéma, purge, classement anti-triche) · statut : PRÊT (après w20-04 et w20-07)
> **Groupe : W20-S2** (produit) · prérequis : w20-04, w20-07 fusionnés ; D-W20-9, D-W20-12 · porte : `gradle :play-server:test --tests 'castbridge.play.lobby.*' --tests 'castbridge.play.board.*'` (base H2 en mode MySQL **ou** Testcontainers MySQL si Docker local : préciser dans le rapport)
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 2,5 j) · audit Opus : échantillon

**Vague 20 · Effort L · Modèle : sonnet.** Conception : `DESIGN-W20` § 2.10 (b)(c)(e), § 3.1, § 7 (D-W20-9, 12). Branche `claude/sonnet-w20-09`. Rapport : `docs/agent-reports/sonnet-w20-09.md`. Aucun `S/`, `R/`, `backend/src/`.

## Objectif
(1) **Salon public « Duel rapide »** : `Lobby` pur (`C/quiz/online/Lobby.kt` : file par clé `(track, level, field, lang)`, table dès 2 joueurs, départ à 4 ou 20 s, mélange par pays/IP quand possible, questions **libres** seulement via `PlayRules`, 10 questions, pas de mise, « revanche » = nouvelle file). (2) **Classements** : schéma `castbridge_play` (Flyway `V1__play.sql` : `play_device(deviceHash, kind app|tv, country, createdAt, lastSeenAt)`, `play_result(id, roomId, deviceHash, points, elapsedAvgMs, correct, humans, ranked, botScore, country, playedAt)`, `play_board(period, scope, deviceHash, points, games, updatedAt)`), `Board` pur (30 jours glissants, mondial et par pays, ≥ 4 humains distincts, ≤ 20 parties classées/appareil/jour, `ranked` seulement, écart-type des temps > 0), routes `GET /play/board?scope=&country=` (JSON, 100 lignes, pseudonyme **du dernier jeu** ou « Joueur »), `GET /play/me` (mes résultats), `DELETE /play/me` (purge par `deviceHash`, idempotent). (3) **Rétention** : `PlayRetentionJob` nocturne (salles > 24 h, résultats > 12 mois, appareils inactifs > 12 mois), testé avec horloge injectée. (4) **Télémétrie agrégée** `play_room` (§ 2.10 e) : écrite dans `play_result`/journal ; **aucun** envoi vers l'API principale dans ce cahier.

## Pourquoi (preuves)
- `backend/src/main/resources/application.yml:57-59` : Flyway est la règle du projet ; `docker-compose.yml:13-27` : MySQL 8.4 `max-connections=40` : le pool de `play` doit rester petit (`maximum-pool-size 4`).
- `application.yml:96-100` : durées de rétention en variables (`retention-days`, `ip-days`) : même forme pour `CASTBRIDGE_PLAY_*_DAYS`.
- `C/quiz/HighScores.kt` : « 10 par tableau » local : le classement en ligne est **séparé** (jamais fusionné avec `QuizMerge`).
- `docs/TELEMETRY.md` : évènements à liste blanche, jamais `name/user/ip` : `play_room` suit la même règle.

## Fichiers possédés
- Cœur : `C/quiz/online/{Lobby, Board}.kt`, `CT/quiz/online/{LobbyTest, BoardTest}.kt`.
- Service : `server-play/src/main/resources/db/migration/V1__play.sql`, `server-play/src/main/kotlin/castbridge/play/{lobby/LobbyService, board/BoardController, board/BoardRepository, board/MeController, retention/PlayRetentionJob}.kt`, `server-play/build.gradle.kts` (zone : `spring-boot-starter-jdbc`, `flyway-mysql`, `mysql-connector-j`, H2 test), `application.yml` du service (datasource `CASTBRIDGE_PLAY_DB_*`, pool 4, Flyway), tests `…/lobby/LobbyServiceTest.kt`, `…/board/{BoardControllerTest, RetentionJobTest, DeleteMeTest}.kt`.
- Interdit : schéma `castbridge` (aucune table de l'API lue ou écrite), `backend/**`.

## Étapes
1. **Rouge** : `LobbyTest` (2 joueurs ⇒ table à 20 s ; 4 ⇒ immédiat ; même IP ⇒ tables différentes si possible ; départ d'un joueur en file), `BoardTest` (règles de classement, 20/j, humains ≥ 4, `ranked`), `RetentionJobTest`, `DeleteMeTest` (plus aucune ligne pour le `deviceHash`, 2e appel = 204).
2. Flyway + repository (JDBC simple, pas de JPA) ; `BoardController` avec cache 60 s.
3. `LobbyService` branché sur `RoomRegistry` (salles **sans hôte**, auto-hôte).
4. **Vert** ; rapport avec la taille estimée par ligne et par an (ordre de grandeur).

## Critères d'acceptation
- Service démarre **sans** base si `CASTBRIDGE_PLAY_DB_URL` est vide : salons et salles privées marchent, classements répondent 503 « classement indisponible » (le jeu ne dépend jamais de la base).
- Un résultat `ranked = false` n'apparaît jamais dans `play_board`. Une partie avec 3 humains et 1 robot ⇒ non classée.
- `DELETE /play/me` supprime `play_device`, `play_result`, `play_board` de l'appareil ; les agrégats de salle restent **sans** identifiant.

## Cas limites
- Pays inconnu (GeoIP absent) ⇒ `ZZ`, classement mondial seulement. Changement de pseudonyme ⇒ le classement montre le dernier. Deux appareils d'une même personne ⇒ deux lignes (dit dans la doc : « un classement par appareil »).

## À ne pas faire
- Pas de JPA, pas de table partagée avec l'API, pas de jointure sur `deviceId` de l'API. Pas de récompense, pas de badge « argent », pas de mise dans les salons.

## Rapport
Format RAPPORT + `SYMBIOSE: cap=lobby,board · proto=play-v1 (messages additifs) · reason=PLAY_BUSY · deux écrans=—` + schéma + variables.
