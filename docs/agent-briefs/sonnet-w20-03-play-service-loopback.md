# w20-03 — Service `castbridge-play` (`server-play/`, Kotlin + Spring Boot sur `:core`) : WebSocket + SSE + long-poll, page `/play`, `/play/health`, test en boucle locale et contrat d'autorité sur socket
<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (service exposé à Internet : transport, limites, isolation) · statut : PRÊT (après w20-02)
> **Groupe : W20-S1** (service) · prérequis : w20-02 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle :play-server:test` (puis `:core:test` inchangé)
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 3 j) · audit Opus : **obligatoire**

**Vague 20 · Effort L · Modèle : sonnet · Statut PRÊT (après w20-02).** Conception : `DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md` § 2.1-2.3, § 2.8, § 5.2-5.3. Branche `claude/sonnet-w20-03`. Rapport : `docs/agent-reports/sonnet-w20-03.md`. **Seul cahier** sur `server-play/` jusqu'à sa fusion. Aucun `S/`, `R/`, aucun fichier de `backend/src/` ; **aucun déploiement, aucune connexion au serveur de production** : tout tourne en JVM locale.

## Objectif
Un module Gradle **`:play-server`** (`server-play/`, inclus depuis `android/settings.gradle.kts` comme `:activation-desktop`) : Spring Boot 3.5 (`spring-boot-starter-websocket`, `-web`), **sans** JPA ni base dans ce cahier (w20-09 ajoute Flyway) ; dépend de `:core`. Il expose sur `/play/` : `GET /play` et `GET /play/j/{code}` (page de jeu, dérivée de `play.html`), `WS /play/ws`, repli `GET /play/events?token=` (SSE) + `POST /play/act` + `GET /play/state?since=` (long-poll 25 s), `GET /play/health` (JSON sans secret : salles, connexions, mémoire, version), `GET /play/.well-known/caps`. Il **héberge `ServerRoom`** (w20-02) avec un tick à 200 ms, un registre de salles plafonné (`.env` : `CASTBRIDGE_PLAY_MAX_ROOMS=400`, `…_MAX_CONNECTIONS=3000`, `…_MAX_PER_IP=8`), les limites de messages (10/s, rafale 30, ≤ 2 Ko), ping/pong 25 s, fermeture 40 s sans pong, vérification d'`Origin`. Il lit les lots Quiz depuis un dossier **en lecture seule** (`CASTBRIDGE_PLAY_LOTS_DIR`, format `.quiz.zip` existant via `QuizLotConsumer`/`QuizPacks` du cœur) : dans ce cahier, les **questions libres** seulement (les réservées : w20-04). Et la preuve : `PlayLoopbackTest` et `AuthorityContractTest` sur **socket réelle, port aléatoire**.

## Pourquoi (preuves)
- `backend/pom.xml` : Java/Maven, **sans** `spring-boot-starter-websocket`, zéro `SseEmitter`/`WebSocket` dans `backend/src/main/java` (vérifié) : le temps réel n'existe pas ; l'API Java ne connaît pas `:core` ⇒ un module Kotlin est le seul moyen de réutiliser `QuizRoom`.
- `android/settings.gradle.kts` : `include(":activation-desktop")` avec `projectDir = file("../tools/activation-desktop")` : précédent d'un module hors `android/` sur `:core`.
- `backend/docker-compose.yml:88-104` : conteneur `read_only`, `cap_drop ALL`, `mem_limit`, port publié sur `127.0.0.1` : **mêmes** règles pour `castbridge-play` (fichier compose écrit par w20-08, pas ici).
- `backend/src/main/resources/application.yml:2-8` : `forward-headers-strategy: framework` derrière nginx qui **écrase** `X-Forwarded-For` : à reprendre.
- `C/quiz/QuizHttp.kt:114-175` : le flux SSE existant (`event: state`, long-poll 25 s) : la page web attend ce format ⇒ le repli SSE du service le reproduit.

## Fichiers possédés
- Nouveaux : `server-play/build.gradle.kts`, `server-play/src/main/kotlin/castbridge/play/{PlayApplication, PlayConfig, RoomRegistry, PlayWebSocketHandler, PlayFallbackController, PlayPageController, HealthController, OriginCheck, ConnectionLimits, Ticker}.kt`, `server-play/src/main/resources/{application.yml, static/play/play.html, static/play/play.js, static/play/play.css}`, `server-play/src/test/kotlin/castbridge/play/{PlayLoopbackTest, AuthorityContractSocketTest, FallbackTransportTest, LimitsTest, OriginCheckTest}.kt`, `server-play/Dockerfile`, `server-play/README.md`.
- Zone additive : `android/settings.gradle.kts` (deux lignes `include` + `projectDir`), `android/build.gradle.kts` **seulement** si une version de plugin Spring doit y être déclarée (sinon dans le module).
- Interdit : `backend/**`, `S/`, `R/`, `C/**` (si le cœur manque quelque chose : `QUESTION:` et arrêt).

## Étapes
1. **Rouge** : `PlayLoopbackTest` (démarre le service `RANDOM_PORT`, crée une salle par WS avec un ticket **de test** (`CASTBRIDGE_PLAY_TICKET_PUBKEY` de test, vérification réelle de la signature : w20-04 remplace l'émetteur, pas le vérificateur), 3 clients WS rejoignent par code, Duel 10 questions à graine, classement attendu ; un client passe par SSE+POST, un par long-poll : **même** résultat), `LimitsTest` (9e connexion même IP ⇒ 429 ; 31 messages en 1 s ⇒ 1008 ; message 3 Ko ⇒ rejet ; `Origin: https://evil.example` ⇒ 403), `OriginCheckTest`.
2. Module Gradle : Kotlin JVM 21, Spring Boot BOM 3.5.x, `implementation(project(":core"))`, `bootJar` ; `tools/core-harness/run.sh` accepte `:play-server:test` (vérifier, sinon noter la commande exacte).
3. Service : `PlayWebSocketHandler` (une session = une connexion ; file de sortie bornée 64 Ko ⇒ 1008), `Ticker` (200 ms, `ServerRoom.tick`), `RoomRegistry` (plafonds, purge 10 min inactivité / 2 h vie), `ConnectionLimits` (par IP via `X-Forwarded-For` écrasé ; filtre **avant** l'upgrade), `OriginCheck` (liste blanche `CASTBRIDGE_PLAY_ORIGINS`, défaut `https://bridge.sti-cm.com` ; absent/`null` accepté **seulement** avec un jeton valide), repli SSE/long-poll qui parle **le même** `PlayCodec`.
4. Page `/play` : copier `android/core/src/main/resources/castbridge/quiz/play.html` vers `static/play/`, remplacer le transport (`/quiz/api/*`) par un module `play.js` : WS → SSE → long-poll, jeton en `sessionStorage`, bandeau `safety` **affiché depuis le message reçu** (jamais calculé), case « 13 ans ou plus ou un parent m'accompagne », saisie du code `XXXX-XXXX` tolérante ; ≤ 60 Ko au total, aucune dépendance externe.
5. `AuthorityContractSocketTest` : rejoue le scénario de w20-01/02 avec `ServerAuthority` sur un vrai `PlayTransport` WebSocket (client Java 11 `java.net.http.WebSocket`) contre le service ⇒ mêmes états que `LocalAuthority`.
6. `Dockerfile` (image `eclipse-temurin:21-jre`, uid 10001, `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k`, `EXPOSE 8090`, healthcheck `GET /play/health`), `README.md` (variables d'environnement, limites, **« ne jamais publier 8090 hors loopback »**).
7. **Vert** + mesure : mémoire du processus de test avec 100 salles × 8 clients simulés (`Runtime.totalMemory − freeMemory` avant/après) dans le rapport (ordre de grandeur, pas une garantie).

## Critères d'acceptation
- `PlayLoopbackTest` vert pour les **trois** transports ; `AuthorityContractSocketTest` vert.
- `/play/health` ne contient ni adresse IP, ni code de salle, ni jeton.
- Le service démarre sans base de données, sans `CASTBRIDGE_ADMIN_TOKEN`, sans clé privée : test `NoSecretsTest` (aucune variable `*_KEY`/`*_TOKEN` lue sauf `CASTBRIDGE_PLAY_TICKET_PUBKEY*`).
- `:core:test` inchangé et vert ; `gradle :play-server:bootJar` produit un jar < 60 Mo.
- Aucune route sous `/api/` (tout sous `/play/`) : pas de collision avec le nginx de l'API.

## Cas limites
- Client WS sans pong 40 s ⇒ fermé, siège gardé (reprise possible 10 min). Proxy qui coupe à 60 s ⇒ ping 25 s suffit ; test avec un `proxy_read_timeout` simulé (fermeture forcée à 30 s) ⇒ le client bascule en SSE et le dit.
- Ticket de test périmé ⇒ `PLAY_TICKET_REFUSED`, pas de salle. Plafond de salles atteint ⇒ `error{code:PLAY_BUSY, retryAfterMs}`.

## À ne pas faire
- Aucun déploiement, aucun `docker` contre la production, aucun `ssh`. Aucune dépendance sur le schéma MySQL `castbridge`. Aucun `X-CB-Pin`. Aucune route qui liste des questions. Ne pas réécrire la logique de jeu : `ServerRoom`/`QuizRoom` sont **la** logique.

## Rapport
Format RAPPORT + `SYMBIOSE: cap=play1,sse,longpoll · proto=play-v1 · reason=PLAY_* · deux écrans=AuthorityContractSocketTest` + mesure mémoire + commande exacte de démarrage local (`java -jar … --server.port=8090`) + points à auditer (Opus) listés : transport, limites, `Origin`, isolation.
