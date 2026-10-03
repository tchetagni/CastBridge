# castbridge-play : service de jeu en ligne

Service JVM (Kotlin, JDK seul, aucune dépendance hors `:core`) qui héberge les salles `ServerRoom` du cœur (`castbridge.core.quiz.online`) pour le Quiz sur Internet.
Protocole : `docs/PLAY-PROTOCOL.md` (`play-v1`). Conception : `docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md` § 2. Rapport : `docs/agent-reports/sonnet-w20-03.md`.

> **Ne jamais publier le port du service hors de la boucle locale** (`127.0.0.1:7091->8080` en production, jamais `0.0.0.0`). Seul nginx (TLS, port 443) le joint.

## Routes (toutes sous `/play/`, rien sous `/api/`)

| Route | Rôle |
|---|---|
| `GET /play`, `GET /play/j/{code}` | page de jeu (statique, sans dépendance externe, ≤ 60 Ko) |
| `WS /play/ws` | WebSocket : un message JSON `play-v1` par trame texte |
| `POST /play/act` + `GET /play/events?token=` | repli SSE : un message client par POST (en-tête `X-Play-Conn` après le premier), flux `event: <type>` |
| `POST /play/act` + `GET /play/state?token=&since=` | repli long-poll 25 s : `{"msgs":[…],"next":n}`, les messages sont retirés à l'accusé `since` |
| `GET /play/health` | JSON sans secret : salles, connexions, mémoire, version (ni IP, ni code, ni jeton) |
| `GET /play/.well-known/caps` | capacités (`play1`, `sse`, `longpoll`…) et limites |

Ici `token` est le secret de la **connexion de repli** (128 bits, vie courte), jamais le jeton de siège. Les trois transports passent par le même `PlayHub` et le même `PlayCodec`.

## Variables d'environnement (les seules lues ; aucune clé privée, aucun secret)

| Variable | Défaut | Rôle |
|---|---|---|
| `CASTBRIDGE_PLAY_PORT` (ou `--server.port=`) | 8080 | port d'écoute dans le conteneur |
| `CASTBRIDGE_PLAY_BIND` | 0.0.0.0 | adresse d'écoute (en production : réseau Docker, port publié en 127.0.0.1) |
| `CASTBRIDGE_PLAY_MAX_ROOMS` | 400 | salles ; au-delà : `error PLAY_BUSY` |
| `CASTBRIDGE_PLAY_MAX_CONNECTIONS` | 3000 | connexions ; au-delà : HTTP 503 |
| `CASTBRIDGE_PLAY_MAX_PER_IP` | 8 | connexions par adresse cliente ; au-delà : HTTP 429 avant l'upgrade |
| `CASTBRIDGE_PLAY_ORIGINS` | `https://bridge.sti-cm.com` | origines autorisées (liste séparée par des virgules) |
| `CASTBRIDGE_PLAY_TRUSTED_PROXIES` | `127.0.0.0/8,::1/128,172.16.0.0/12` | réseaux dont `X-Forwarded-For` est cru (dernier saut seulement) |
| `CASTBRIDGE_PLAY_TICKET_PUBKEY`, `_2`, `_3` | (vide) | clés PUBLIQUES Ed25519 des tickets (Base64 : 32 octets bruts ou SPKI) ; vide = aucune salle ne peut s'ouvrir |
| `CASTBRIDGE_PLAY_LOTS_DIR` | (vide) | dossier en lecture seule de lots `.quiz.zip` ; vide = questions libres intégrées seulement |

Ticket d'ouverture de salle : `v1.<charge>.<signature>` (Base64 URL), signature Ed25519 de `v1.<charge>`, charge `{"iat":<ms>,"exp":<ms>}`, vie ≤ 15 min ; envoyé dans `hello.ticket` ou l'en-tête `X-Play-Ticket`. Rejouer ou fabriquer un ticket : `PLAY_TICKET_REFUSED`, aucune salle.

## Limites et délais

- Messages : 10/s, rafale 30 (au-delà : fermeture 1008) ; ≤ 2 Ko par message (au-delà : `error BAD_REQUEST`) ; trame > 8 Ko : fermeture 1009 ; texte seulement (binaire : 1003).
- File de sortie par connexion : 64 Ko (au-delà : fermeture 1008, le siège est gardé pour `resume`).
- WebSocket : ping toutes les 25 s (nginx coupe un flux muet à 75 s), fermeture après 40 s sans aucune trame ; repli : session fermée après 40 s sans flux ni requête.
- Salles : tick 200 ms ; purge après 10 min sans connexion, 2 h de vie au plus ; 50 codes faux sur une salle changent le code, 10 par adresse et par 5 min bloquent l'adresse.
- Délai entre deux questions (exigence du propriétaire) : 1,5 s par défaut (1 à 2 s), `opensAtServerMs` sur l'horloge du serveur (`PlayTiming`) ; appliqué par `ServerRoom`, honoré par la boucle du service.

## Derrière nginx (extrait indicatif ; le vrai fichier est écrit par w20-08)

```
location /play/ {
    proxy_pass http://castbridge-play:8080;     # nom Docker (réseau infra-net)
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;     # WebSocket
    proxy_set_header Connection $connection_upgrade;
    proxy_set_header X-Forwarded-For $remote_addr;   # ÉCRASE l'en-tête : le service lit le dernier saut
    proxy_buffering off;                        # SSE
    proxy_read_timeout 75s;                     # ping toutes les 25 s
}
```

## Construire, tester, lancer en local

```
cd android
ANDROID_HOME=$HOME/Library/Android/sdk bash ../tools/agents/gradle-lock.sh gradle --offline :server-play:test      # tests (vraies sockets sur 127.0.0.1)
ANDROID_HOME=$HOME/Library/Android/sdk bash ../tools/agents/gradle-lock.sh gradle --offline :server-play:fatJar    # server-play/build/libs/castbridge-play.jar (< 60 Mo)
java -jar ../server-play/build/libs/castbridge-play.jar --server.port=8090     # puis http://127.0.0.1:8090/play/health
```

`tools/core-harness/run.sh` n'inclut que `:core` : pour `:server-play` sans le plugin Android, créer une racine Gradle `.core-harness/` avec `include(":core", ":server-play")` (voir le `Dockerfile`).

Image : `docker build -f server-play/Dockerfile -t castbridge-play .` depuis la racine du dépôt (base `eclipse-temurin:21-jre`, uid 10002). Lancement attendu : `read_only`, `tmpfs /tmp`, `cap_drop: ALL`, `mem_limit: 384m`, port `127.0.0.1:7091:8080`, réseau `infra-net`, aucun accès aux secrets de licence ni d'activation, aucune base dans ce cahier (schéma `castbridge_play` : w20-09).
