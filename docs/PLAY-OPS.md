# PLAY-OPS : exploitation du service de jeu en ligne `castbridge-play` (runbook du propriétaire)

> **Ce document est écrit, jamais exécuté, par l'agent w20-08.** Aucune commande ci-dessous n'a été lancée sur `bridge.sti-cm.com` ni ailleurs : le propriétaire les exécute, dans l'ordre, après avoir lu la ligne « Lire d'abord » de chaque bloc. Chaque étape a sa vérification et son retour arrière.
> Sources : exigences des deux audits Opus (`docs/PLAY-OPS-REQUIREMENTS.md`), conception (`docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md` § 2.9, O-1 à O-10), service (`server-play/README.md`), cahier `docs/agent-briefs/sonnet-w20-08-ops-runbook-proxy-port-tls.md`.
> Le nginx de l'hôte (`infra-nginx`) et son certbot appartiennent à l'infrastructure, **pas à ce dépôt** (`docs/SERVER-STRATEGY.md`) : toute modification = sauvegarde datée, `nginx -t`, approbation explicite du propriétaire.
> Rien de « public » avant que les quatre conditions de `PLAY-OPS-REQUIREMENTS.md` (§ « Conditions avant la route nginx `/play/` publique ») soient remplies : le staging (a) est autorisé, la route publique (b) ne l'est qu'ensuite.

## 0. Plan en un coup d'œil

| Partie | Quoi | Public ? |
|---|---|---|
| [1](#1-faits-relevés-sur-lhôte-le-2026-10-03-lecture-seule) | Faits de l'hôte (relevés le 2026-10-03, lecture seule) et ce qui reste « à relever » | non |
| [2](#2-lire-lhôte-avant-toute-modification-lecture-seule) | Lectures préalables sur la machine | non |
| [3 (a)](#3-a-staging-non-public-127001-7091-tunnel-ssh) | **Staging** : image construite sur le serveur, `127.0.0.1:7091`, tunnel `ssh -L` | non |
| [4 (b)](#4-b-route-publique-wss-bridgesti-cmcomplay) | **Public** : clés de ticket, `.env.play`, déploiement, bloc nginx `/play/`, essais | oui |
| [5 (c)](#5-c-le-fichier-compose-séparé) | Fichier compose séparé `backend/docker-compose.play.yml` | — |
| [6 (d)](#6-d-deploy-serversh---service-play) | `deploy-server.sh --service play` | — |
| [7 (e)](#7-e-base-de-données-facultatif-après-w20-09) | Base `castbridge_play` (facultatif, après w20-09) | — |
| [8](#8-assetlinksjson-facultatif-pas-dans-le-périmètre-des-joueurs-web) | `assetlinks.json` (facultatif) | — |
| [9 (g)](#9-g-surveillance) | Surveillance : santé locale, commande de contrôle, journaux | — |
| [10](#10-retour-arrière-global-et-faits-à-confirmer) | Retour arrière global, faits à confirmer B-W20-1 / B-W20-2 | — |

Conventions : `$` = commande à lancer sur le serveur (`ssh ubuntu@bridge.sti-cm.com`), `Mac$` = sur le Mac. Docker se lance avec `sudo docker` (comme `CB_DOCKER` de `deploy-server.sh`). `ROOT=$HOME/castbridge` (disposition : `~/castbridge/{castbridge.git, releases/<tag>-<utc>, current, previous, backups, services/castbridge}`).

## 1. Faits relevés sur l'hôte le 2026-10-03 (lecture seule)

| Fait | Valeur relevée |
|---|---|
| Système, ressources | Ubuntu 24.04, 4 vCPU, 8 Go de RAM (≈ 5,8 Go libres), 80 Go de disque libres (B-W20-2 : vCPU = **4**, connu) |
| Conteneurs CastBridge | `castbridge-api` (image `castbridge-api:current`, `127.0.0.1:7090->8080`, 512 Mo, sain), `castbridge-db` (`mysql:8.4`) |
| **Autre projet : ne jamais toucher** | `sti-frontend`, `sti-backend`, `sti-db` |
| nginx | `infra-nginx` (`nginx:1.25.5-alpine`, `0.0.0.0:80/443`), configuration montée depuis `/opt/infra/nginx/nginx.conf` : `worker_processes auto; worker_connections 1024; keepalive_timeout 65;` ; `conf.d` = `/opt/infra/nginx/conf.d/` (propriétaire : utilisateur `git`) |
| Seul fichier CastBridge de nginx | `/opt/infra/nginx/conf.d/castbridge.conf` : site `bridge.sti-cm.com`, TLS Let's Encrypt (`infra-certbot`), `location /` → `set $cb_upstream http://castbridge-api:8080; resolver 127.0.0.11 valid=30s`, `proxy_pass $cb_upstream; proxy_http_version 1.1; X-Forwarded-For $remote_addr; proxy_buffering off; proxy_read_timeout 120s; client_max_body_size 200m` |
| Forme « upgrade » déjà utilisée sur l'hôte | `n8n.conf` (désactivé) dans le même dossier : `Upgrade` / `Connection "upgrade"` |
| Réseaux Docker | `infra-net` (nginx ↔ applications), `castbridge-edge`, `castbridge-internal` (base) |
| Pare-feu `ufw` | 22, 80, 443, 51821/udp |
| DNS | `bridge.sti-cm.com` : A `79.143.185.145`, **pas** d'AAAA |
| Images déjà présentes | `eclipse-temurin:25-jre` = `eclipse-temurin@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636` (Temurin 25.0.4.1 LTS, 120 Mo) ; `gradle:8.14.3-jdk21` = `gradle@sha256:21bd311ed01360c189b8870c6b6e988199ff10f72d445d02fb39d3cff9da91d7` |
| Déploiement | `~/castbridge/{castbridge.git (bare : main + tags server-*), releases/…, current, previous, backups, services/castbridge}` ; `tools/release/deploy-server.sh server-<v> --apply` : push du tag, extraction, sauvegarde de la base, construction, santé, retour arrière automatique |

**Ce que ce relevé ne dit pas (« à relever » : la partie 2 le lit) :** le contenu exact de `castbridge.conf` (le runbook insère avant `location /`), si `/opt/infra` est un dépôt git déployé par un crochet (utilisateur `git`), si `/var/log/nginx` est un volume, la limite de descripteurs de `infra-nginx`, l'adresse et le sous-réseau de `infra-net`, le contenu de `docker-compose.override.yml` de l'API, la présence d'un dossier `snippets/` inclus par `nginx.conf` (**non vérifié** : le runbook n'en dépend pas).

## 2. Lire l'hôte avant toute modification (lecture seule)

**Lire d'abord :** rien à lire avant ce bloc ; il ne modifie rien.

```bash
ssh ubuntu@bridge.sti-cm.com
$ ROOT=$HOME/castbridge
# 2.1 ressources et conteneurs
$ free -m; df -h / /var/lib/docker; nproc
$ sudo docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
# 2.2 nginx : fichier CastBridge, nginx.conf (worker_connections, TLS, HSTS), inclusions
$ sudo docker exec infra-nginx nginx -T 2>/dev/null | grep -nE 'worker_processes|worker_connections|worker_rlimit_nofile|ssl_protocols|Strict-Transport|include |server_name|limit_conn_zone|log_format'
$ ls -la /opt/infra/nginx /opt/infra/nginx/conf.d
$ cat /opt/infra/nginx/conf.d/castbridge.conf
$ sudo docker exec infra-nginx sh -c 'ulimit -n'
$ sudo docker inspect infra-nginx -f '{{json .Mounts}}'                    # /var/log/nginx est-il un volume ?
# 2.3 /opt/infra est-il déployé par git (utilisateur « git ») ? si oui, la modification doit passer par ce dépôt, sinon le prochain push l'écrase
$ ls -la /opt/infra/.git /opt/infra/nginx/.git 2>&1 | head; ls -la /home/git 2>&1 | head
# 2.4 réseau infra-net : sous-réseau et adresse de nginx
$ sudo docker network inspect infra-net -f '{{range .IPAM.Config}}{{.Subnet}}{{end}}'
$ sudo docker inspect infra-nginx -f '{{(index .NetworkSettings.Networks "infra-net").IPAddress}}'
# 2.5 ports et pare-feu : 7091 ne doit PAS exister encore ; 7090 est sur 127.0.0.1
$ ss -ltnp | grep -E ':7090|:7091'
$ sudo ufw status numbered
# 2.6 images et déploiement existants
$ sudo docker image inspect eclipse-temurin:25-jre -f '{{index .RepoDigests 0}}'
$ ls -la "$ROOT" "$ROOT/releases" | head -30; readlink -f "$ROOT/current"
$ cat "$ROOT/services/castbridge/backend/docker-compose.override.yml" 2>/dev/null || cat "$ROOT/current/docker-compose.override.yml"
```

**Vérifier :** (1) le digest de 2.6 est `eclipse-temurin@sha256:fcd7fd7b…ceae636` (sinon : noter le nouveau, ne **pas** re-tirer l'image, passer `CB_PLAY_RUNTIME_IMAGE=<digest lu>` au script et mettre à jour `docs/PLAY-OPS-REQUIREMENTS.md`) ; (2) 2.5 ne montre aucun écouteur 7091 et `7090` est en `127.0.0.1` ; (3) noter le sous-réseau et l'adresse de nginx de 2.4 (utilisés en 4.2) ; (4) **`worker_connections`** : avec `worker_processes auto` sur 4 vCPU, nginx tient environ 4 × 1 024 / 2 = **2 048 connexions proxifiées** en tout (chaque WebSocket en compte deux : client et service), API comprise ; le plafond du service (3 000 connexions) dépasse cela : voir 4.5 pour la décision.
**Revenir en arrière :** sans objet (lecture seule).

## 3. (a) STAGING non public : `127.0.0.1:7091`, tunnel SSH

**Lire d'abord :** `docs/PLAY-OPS-REQUIREMENTS.md` § « Staging NON public » ; `server-play/Dockerfile` (argument `RUNTIME_IMAGE`). Le staging ne publie **aucune route nginx**, n'ouvre **aucun** port du pare-feu, n'utilise **aucune** clé de production. Le `Dockerfile` n'a **jamais été construit** avant ce staging : le premier échec éventuel de construction est une information utile à renvoyer à l'équipe (réseau sortant pour Maven Central, mémoire).

### 3.1 Publier le tag dans le dépôt bare du serveur (depuis le Mac)

```bash
Mac$ git fetch origin && git tag -a server-play-0.1.0 -m "castbridge-play 0.1.0 (staging)" <commit-fusionné-de-w20-03-et-w20-08>
Mac$ git push origin refs/tags/server-play-0.1.0
Mac$ bash tools/release/deploy-server.sh server-play-0.1.0 --service play --push-only          # DRY-RUN : lire le plan
Mac$ bash tools/release/deploy-server.sh server-play-0.1.0 --service play --push-only --apply  # pousse SEULEMENT le tag vers « bridge » ; ne déploie rien
```

**Vérifier :** `git ls-remote bridge refs/tags/server-play-0.1.0` donne le même objet que `git rev-parse refs/tags/server-play-0.1.0`. **Revenir en arrière :** un tag poussé ne se déplace ni ne se supprime ; une erreur se corrige par `server-play-0.1.1`.

### 3.2 Extraire la release et construire l'image (sur le serveur)

**Lire d'abord :** 2.1 (au moins ≈ 2 Go de RAM disponibles : la construction lance Gradle dans un conteneur à côté de l'API, de MySQL et de l'autre projet ; choisir une heure creuse) et 2.6 (digest).

```bash
$ ROOT=$HOME/castbridge
$ DIGEST=eclipse-temurin@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636      # relevé le 2026-10-03
$ REL=$ROOT/releases/server-play-0.1.0-staging-$(date -u +%Y%m%dT%H%M%SZ)
$ mkdir -p "$REL" && git --git-dir="$ROOT/castbridge.git" archive --format=tar server-play-0.1.0 \
      backend/docker-compose.play.yml backend/.env.play.example backend/sql android/core android/gradle.properties \
      server-play content/learn content/langues | tar -x -C "$REL" -f -
$ git --git-dir="$ROOT/castbridge.git" rev-parse 'server-play-0.1.0^{commit}' | tee "$REL/REVISION"
$ free -m | awk '/^Mem:/ {print "disponible : " $7 " Mo"}'
$ cd "$REL" && sudo docker build -f server-play/Dockerfile --progress=plain \
      --build-arg RUNTIME_IMAGE="$DIGEST" --build-arg VCS_REF="$(cat REVISION)" \
      --label org.opencontainers.image.revision="$(cat REVISION)" --label org.opencontainers.image.version=0.1.0 \
      -t castbridge-play:current .
```

**Vérifier :** `sudo docker image ls castbridge-play` (une image `current`, taille de l'ordre de 150 à 250 Mo : **à relever**) ; `sudo docker image inspect castbridge-play:current -f '{{index .Config.Labels "org.opencontainers.image.revision"}}'` = le contenu de `$REL/REVISION` ; `sudo docker run --rm --entrypoint java castbridge-play:current -version` annonce Temurin 25 ; `sudo docker ps` : `castbridge-api` toujours « healthy ». **Revenir en arrière :** `sudo docker rmi castbridge-play:current` ; `rm -rf "$REL"`. Si la construction échoue : noter les 20 dernières lignes, ne rien contourner (ne pas toucher `server-play/`).

### 3.3 Fichier d'environnement de staging et clé publique de TEST

**Lire d'abord :** `backend/.env.play.example` (bloc STAGING). La clé de test n'est utilisée par aucun émetteur : elle ne sert qu'à vérifier que le service démarre ; aucune salle ne s'ouvrira sans ticket valide (voulu).

```bash
$ mkdir -p "$ROOT/services/play/staging-keys" && chmod 700 "$ROOT/services/play" "$ROOT/services/play/staging-keys"
$ openssl genpkey -algorithm ed25519 -out "$ROOT/services/play/staging-keys/test.key"      # clé PRIVÉE de TEST : reste ici, ne sert à rien d'autre
$ chmod 400 "$ROOT/services/play/staging-keys/test.key"
$ PUB=$(openssl pkey -in "$ROOT/services/play/staging-keys/test.key" -pubout -outform DER | base64 -w0)
$ ( umask 077; cat > "$ROOT/services/play/.env.play" <<EOF
CASTBRIDGE_PLAY_DIRECT=1
CASTBRIDGE_PLAY_ORIGINS=http://localhost:7091
CASTBRIDGE_PLAY_MAX_PER_IP=64
CASTBRIDGE_PLAY_TICKET_PUBKEY=$PUB
EOF
  )
$ install -m 600 "$ROOT/services/play/.env.play" "$REL/backend/.env.play"
```

**Vérifier :** `stat -c '%a' "$ROOT/services/play/.env.play"` = `600` ; `grep -c PRIVATE "$ROOT/services/play/.env.play"` = `0` ; le fichier ne contient aucune clé privée, aucune variable `*_KEY` ni `*_TOKEN`. **Revenir en arrière :** `rm -rf "$ROOT/services/play"`.

### 3.4 Lancer `castbridge-play` sur `127.0.0.1:7091`, sur `infra-net`

**Lire d'abord :** `backend/docker-compose.play.yml` (projet compose **distinct** `castbridge-play` : `castbridge-api`, `castbridge-db` et `sti-*` ne sont pas dans ce projet) ; 2.4 (`infra-net` existe : le réseau est déclaré `external`).

```bash
$ cd "$REL/backend" && sudo docker compose -f docker-compose.play.yml config -q && echo "compose valide"
$ sudo docker compose -f docker-compose.play.yml up -d --no-build
```

**Vérifier :**

```bash
$ sudo docker ps --filter name=castbridge-play --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'      # (healthy) sous 40 s ; ports : 127.0.0.1:7091->8080/tcp
$ sudo docker port castbridge-play                      # attendu : 8080/tcp -> 127.0.0.1:7091   (JAMAIS 0.0.0.0)
$ ss -ltnp | grep 7091                                   # attendu : 127.0.0.1:7091 seulement
$ sudo docker logs castbridge-play 2>&1 | head -n 20     # avertissement « DIRECT » attendu (staging), aucune erreur
$ sudo docker exec castbridge-play curl -fsS http://127.0.0.1:8080/play/health
$ sudo docker network inspect infra-net -f '{{range .Containers}}{{.Name}} {{end}}'     # castbridge-play y figure
```

Santé **depuis le Mac par tunnel** (le pare-feu reste fermé) :

```bash
Mac$ ssh -N -L 7091:127.0.0.1:7091 ubuntu@bridge.sti-cm.com &          # tunnel ; l'arrêter ensuite : kill %1
Mac$ curl -fsS http://localhost:7091/play/health                        # {"status":"ok","version":…,"rooms":0,…,"connections":…,"memoryUsedMb":…}
Mac$ curl -fsS http://localhost:7091/play/.well-known/caps
Mac$ open http://localhost:7091/play                                    # la page de jeu ; Chrome/Firefox : cookies Secure acceptés sur localhost, Safari non (repli SSE/long-poll indisponible en staging sous Safari)
```

**Contrôle de mémoire :**

```bash
$ sudo docker stats --no-stream castbridge-play castbridge-api castbridge-db       # castbridge-play : limite 384 MiB ; au repos, attendu nettement en dessous (ordre de grandeur mesuré hors conteneur : ≈ 66 Mo, à relever ici)
$ free -m; sudo docker inspect castbridge-play -f '{{.State.OOMKilled}} {{.RestartCount}}'     # attendu : false 0
```

**Revenir en arrière (staging) :**

```bash
$ sudo docker stop castbridge-play && sudo docker rm castbridge-play      # le réseau infra-net (externe) n'est pas touché
$ sudo docker rmi castbridge-play:current                                 # facultatif
$ sudo docker ps --filter name=castbridge-api --format '{{.Names}} {{.Status}}'      # l'API n'a pas bougé
```

## 4. (b) Route publique `wss://bridge.sti-cm.com/play/`

**Lire d'abord :** les 15 exigences et les « Conditions avant la route nginx `/play/` publique » de `docs/PLAY-OPS-REQUIREMENTS.md`. Les sections 4.1 à 4.3 préparent le service ; **la 4.4 est le seul acte qui expose le service à Internet.** Ne la faire qu'à une heure creuse, après la 4.3.

### 4.0 PRÉREQUIS de production : le module des licences (décision du propriétaire, rien n'est fait ici)

**À lire avant tout le reste de la partie 4.** Le service refuse d'ouvrir une salle tant qu'il n'a pas accepté une liste de révocations signée (échec fermé, voulu), et `CASTBRIDGE_PLAY_REVOCATIONS_URL` n'est pas facultative en production. Or :

- `GET /api/v1/revocations` répond **503** tant que le **module des licences** n'est pas actif avec sa clé de signature `license-signing.key` ; ce module est **ÉTEINT par défaut en production** (`CASTBRIDGE_LICENSES_ENABLED=false`, et la route publique `CASTBRIDGE_LICENSES_PUBLIC_ROUTES=false` répond 404). Sans décision, `/play/health` dira `"revocations":"none"` et **aucune salle ne s'ouvrira**.
- Le propriétaire doit donc **décider** : soit **activer le module des licences** (`docs/LICENSE-ADMIN.md` : `CASTBRIDGE_LICENSES_ENABLED=true`, `CASTBRIDGE_LICENSES_PUBLIC_ROUTES=true`, fichier `license-signing.key` dans le dossier des secrets), soit **fournir une liste de révocations signée statique** (une liste `cbx1` émise hors ligne avec une clé de portée `REVOKE`, servie en https à l'adresse de `REVOCATIONS_URL`) ; la liste n'est crue que si elle a moins de 24 h, il faut donc la ré-émettre au moins chaque jour (le service garde la dernière valide dans `play-state`). **Aucun cahier ne le déclenche** : rien n'a été activé, rien n'a été émis.

La clé publique du SERVEUR, **exactement où la lire** (code : `backend/src/main/java/castbridge/server/licenses/`) :

- La clé privée est le fichier `license-signing.key` du dossier des secrets (`CASTBRIDGE_LICENSES_SECRETS_DIR`, `/run/secrets` par défaut ; `LicenseKeyring`). Au démarrage de l'API, le journal dit `licence module: server signing key <kid> loaded`.
- La clé **publique** (Base64 de 32 octets bruts) est le champ `publicKey` de `GET /api/v1/admin/licenses/signing` (`LicenseApiController.signing()`, route d'administration : jeton d'administrateur, droit `LICENSE_READ`) ; le même appel donne `scopes` (portées fixes du serveur : `ISSUE_TRIAL`, `ISSUE_PRODUCTION`, `REACTIVATE`, `REVOKE`, `REGISTRY`, `POLICY`). Ce n'est **pas** `GET /api/v1/updates/public-key` (clé des mises à jour, autre clé) ni la clé des tickets.
- Valeur à poser : `CASTBRIDGE_PLAY_TRUSTED_KEYS=server:<publicKey>:REVOKE+ISSUE_TRIAL+ISSUE_PRODUCTION` (même format `nom:clé:PORTÉES` que la liste de confiance de la TV ; `REVOKE` pour la liste de révocations, `ISSUE_*` pour les activations émises par le serveur). Une entrée mal formée est **ignorée** et dite au démarrage (journal `play.config.warning`).
- `CASTBRIDGE_LICENSES_TRUSTED_KEYS` de l'API n'est **pas** cette valeur : elle liste les clés des outils hors ligne (bureau, téléphone) que l'API accepte à l'import du registre.

### 4.1 Paire de clés du ticket (privée pour l'API, publique pour `castbridge-play`)

**Lire d'abord :** DESIGN-W20 O-6. L'émetteur de tickets est livré par **w20-04** : tant que ce cahier n'est pas fusionné et déployé dans `castbridge-api`, aucune salle ne peut s'ouvrir en production (voulu : service sûr mais inerte). `tools/play/gen-ticket-keypair.sh` (w20-04) **n'existe pas encore** à la date de ce document ; s'il existe au moment de l'exécution, le préférer (`bash tools/play/gen-ticket-keypair.sh`, lire sa sortie) ; sinon, les commandes `openssl` ci-dessous sont équivalentes.

```bash
$ ROOT=$HOME/castbridge
$ SRC=$(readlink -f "$ROOT/current" 2>/dev/null || echo "$ROOT/services/castbridge/backend")      # release en service de l'API : deploy-server.sh copie secrets/ vers chaque nouvelle release
$ sudo test ! -e "$SRC/secrets/play-ticket.key" && echo "libre" || echo "EXISTE DÉJÀ : ne pas écraser"
$ sudo openssl genpkey -algorithm ed25519 -out "$SRC/secrets/play-ticket.key"
$ sudo chown 10001:10001 "$SRC/secrets/play-ticket.key" && sudo chmod 0400 "$SRC/secrets/play-ticket.key"     # comme la clé de signature des mises à jour (uid de castbridge-api)
$ PUB=$(sudo openssl pkey -in "$SRC/secrets/play-ticket.key" -pubout -outform DER | base64 -w0); echo "$PUB"   # clé PUBLIQUE (SPKI) en Base64 : peut s'afficher
```

La **clé privée** ne quitte jamais `secrets/` (ne pas l'afficher, ne pas la copier, ne pas la mettre dans `.env.play` ni dans une conversation). Elle est branchée sur `castbridge-api` par le fichier d'exploitation `docker-compose.override.yml` (copié à chaque release par `deploy-server.sh`), **quand w20-04 est déployé** ; lire d'abord le contenu actuel (2.6) et **fusionner**, ne pas remplacer :

```yaml
# à ajouter dans docker-compose.override.yml de la release en service (le nom de la variable est celui du DESIGN-W20 O-6 : à confirmer à la fusion de w20-04)
services:
  castbridge-api:
    secrets:
      - play_ticket_key
    environment:
      CASTBRIDGE_PLAY_TICKET_KEY_FILE: /run/secrets/play_ticket_key
secrets:
  play_ticket_key:
    file: ./secrets/play-ticket.key
```

**Vérifier :** `sudo stat -c '%U:%g %a' "$SRC/secrets/play-ticket.key"` = `10001:10001 400` ; `sudo docker exec castbridge-api test -r /run/secrets/play_ticket_key && echo ok` (après le déploiement de l'API avec w20-04). **Revenir en arrière :** retirer le bloc de l'override, redéployer l'API (`deploy-server.sh --rollback --apply` si c'était la dernière bascule) ; `sudo shred -u "$SRC/secrets/play-ticket.key"` si la paire est à refaire.

### 4.2 `.env.play` de production

**Lire d'abord :** `backend/.env.play.example` (bloc PRODUCTION). Le sous-réseau de `infra-net` est **à relever** (2.4) : l'adresse du conteneur nginx peut changer à son redémarrage et le compose d'infrastructure n'est pas dans ce dépôt, donc on déclare le **sous-réseau** de confiance. Variante plus stricte (exigence 2 de `PLAY-OPS-REQUIREMENTS.md`, préférée si l'on accepte de relire l'adresse après chaque recréation de `infra-nginx`) : l'adresse de nginx en `/32`.

```bash
$ SUBNET=$(sudo docker network inspect infra-net -f '{{range .IPAM.Config}}{{.Subnet}}{{end}}'); echo "$SUBNET"      # à relever ; attendu : un /16 ou /24 privé, jamais 172.16.0.0/12
# variante stricte (au choix) :  SUBNET=$(sudo docker inspect infra-nginx -f '{{(index .NetworkSettings.Networks "infra-net").IPAddress}}')/32
$ cp "$ROOT/services/play/.env.play" "$ROOT/services/play/.env.play.staging.$(date -u +%Y%m%d)"       # sauvegarde du profil de staging
$ ( umask 077; cat > "$ROOT/services/play/.env.play" <<EOF
CASTBRIDGE_PLAY_TRUSTED_PROXIES=$SUBNET
CASTBRIDGE_PLAY_TICKET_PUBKEY=$PUB
CASTBRIDGE_PLAY_TRUSTED_KEYS=server:$SERVER_PUB:REVOKE+ISSUE_TRIAL+ISSUE_PRODUCTION
CASTBRIDGE_PLAY_REVOCATIONS_URL=https://bridge.sti-cm.com/api/v1/revocations
CASTBRIDGE_PLAY_REVOCATIONS_FILE=/var/lib/castbridge-play/revocations.txt
CASTBRIDGE_PLAY_ORIGINS=https://bridge.sti-cm.com
CASTBRIDGE_PLAY_MAX_CONNECTIONS=1500
CASTBRIDGE_PLAY_MAX_PER_IP=24
CASTBRIDGE_PLAY_MAX_PER_48=512
CASTBRIDGE_PLAY_MAX_PER_IP_SHARED=64
CASTBRIDGE_PLAY_CREATES_PER_IP_HOUR=20
CASTBRIDGE_PLAY_CREATES_PER_IDENTITY_DAY=30
CASTBRIDGE_PLAY_CREATES_PER_48_HOUR=200
EOF
  )
```

`$SERVER_PUB` = champ `publicKey` de `GET /api/v1/admin/licenses/signing` (4.0 ; à relever avant d'écrire le fichier ; ce n'est pas la valeur de `CASTBRIDGE_LICENSES_TRUSTED_KEYS`). `CASTBRIDGE_PLAY_MAX_CONNECTIONS=1500` : voir 4.5 (nginx à 1 024 connexions par worker). Pas de `CASTBRIDGE_PLAY_DIRECT`. Lots de questions : `CASTBRIDGE_PLAY_LOTS_DIR` (lots libres) et `CASTBRIDGE_PLAY_RESERVED_DIR` + `CASTBRIDGE_PLAY_RESERVED_IDS` (réservés, volume distinct : le service refuse de démarrer si c'est le même dossier) ; le compose les monte à la demande (§ 5).

**Vérifier :** `curl -fsS http://127.0.0.1:7091/play/health` donne **`"revocations":"ok"`** (et non `none` ni `stale` : 4.0) ; `grep -E 'DIRECT|PRIVATE|_KEY|_TOKEN' "$ROOT/services/play/.env.play"` ne renvoie **rien** ; `stat -c '%a'` = `600` ; `$PUB` est bien la clé de 4.1 (pas celle de test de 3.3). **Revenir en arrière :** `cp` de la sauvegarde `.env.play.staging.<date>` sur `.env.play`.

### 4.3 Déployer l'image et le service (hors partie)

**Lire d'abord :** § 6 (ce que fait le script) ; `sudo docker exec castbridge-play curl -fsS http://127.0.0.1:8080/play/health` (champ `rooms` : le script **refuse** de continuer s'il n'est pas `0`, sauf `CB_PLAY_FORCE=1`). `deploy-server.sh` n'accepte ni `main` ni un commit nu, refuse un arbre modifié, et ne touche ni `castbridge-api`, ni la base, ni nginx.

```bash
Mac$ bash tools/release/deploy-server.sh server-play-0.1.0 --service play                    # DRY-RUN : lire le plan et le script distant
Mac$ bash tools/release/deploy-server.sh server-play-0.1.0 --service play --apply            # propriétaire seulement
Mac$ bash tools/release/deploy-server.sh --status --service play --apply                     # lecture seule
```

**Vérifier :** le script termine par `OK : castbridge-play en bonne santé, port publié sur 127.0.0.1 seulement` ; `sudo docker port castbridge-play` = `8080/tcp -> 127.0.0.1:7091` ; `sudo docker logs castbridge-play 2>&1 | head` ne contient **pas** l'avertissement DIRECT et **pas** de sortie code 2 ; `readlink -f ~/castbridge/current-play` = la nouvelle release ; `docker exec castbridge-play env | grep -E 'KEY|TOKEN|PASSWORD'` ne renvoie rien. **Revenir en arrière :** retour automatique en cas d'échec ; sinon `bash tools/release/deploy-server.sh --rollback --service play --apply`.

### 4.4 Route nginx `/play/` (le seul acte qui expose le service)

**Lire d'abord :** 2.2 (contenu réel de `castbridge.conf`), 2.3 (`/opt/infra` géré par git ? alors faire la même modification **dans ce dépôt-là**), et le choix 4.5 sur `worker_connections`. Les directives `limit_conn_zone` et `log_format` sont de niveau `http` : le fichier `conf.d/castbridge.conf` contient déjà un bloc `server`, donc il est inclus **dans** `http` par `nginx.conf` et ces deux lignes peuvent se placer **en tête du même fichier, avant `server {`** (aucun `map` n'est nécessaire : la forme `Upgrade`/`Connection "upgrade"` n'est posée que pour `/play/ws`). Un dossier `snippets/` inclus par `nginx.conf` **n'a pas pu être vérifié** (non relevé) : ce runbook n'en dépend pas. Si `nginx -t` refuse l'une des deux lignes en tête de fichier (nom déjà pris, contexte refusé), repli : supprimer la ligne `access_log` des quatre blocs et la remplacer par `access_log off;` (les journaux du service lui-même ne contiennent aucune requête), et retirer les `limit_conn` (le service plafonne déjà 8 connexions par adresse).

**1. Sauvegarde datée (obligatoire) :**

```bash
$ CONF=/opt/infra/nginx/conf.d/castbridge.conf
$ BAK=$CONF.bak-$(date -u +%Y%m%dT%H%M%SZ)
$ sudo cp -p "$CONF" "$BAK" && ls -l "$BAK"        # -p conserve le propriétaire (utilisateur « git ») et les droits
```

**2. Éditer** (`sudo -u git` ou `sudoedit`, selon 2.2 ; garder le propriétaire du fichier) :

(i) **tout en haut** du fichier, avant `server {` :

```nginx
# --- castbridge-play (w20-08) : niveau http ; conf.d/*.conf est inclus dans http ---
limit_conn_zone $binary_remote_addr zone=cbplay_addr:10m;
# journal de /play/ SANS query string ($uri au lieu de $request) : aucun secret, aucune adresse complète de requête
log_format cbplay '$remote_addr [$time_local] "$request_method $uri" $status $body_bytes_sent rt=$request_time "$http_user_agent"';
```

(ii) **dans le bloc `server { … }` de `bridge.sti-cm.com`, juste avant `location / {`** :

```nginx
    # --- castbridge-play (w20-08) : /play/ ---
    client_header_timeout 10s;     # NIVEAU SERVEUR : vaut pour tout le site (l'API aussi) ; 10 s pour recevoir les en-têtes

    # santé : réservée à 127.0.0.1 (elle donne le nombre de salles et de connexions). Depuis Internet : 403 ; la surveillance lit 127.0.0.1:7091 directement
    location = /play/health {
        allow 127.0.0.1;
        deny all;
        set $cb_play http://castbridge-play:8080;
        resolver 127.0.0.11 valid=30s;
        proxy_pass $cb_play;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header Connection "close";
        proxy_set_header X-Forwarded-For $remote_addr;
        access_log off;
    }

    # WebSocket : SEUL endroit où l'Upgrade est transmis
    location = /play/ws {
        limit_conn cbplay_addr 20;
        limit_conn_status 429;
        client_max_body_size 64k;
        set $cb_play http://castbridge-play:8080;
        resolver 127.0.0.11 valid=30s;
        proxy_pass $cb_play;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header X-Forwarded-For $remote_addr;      # ÉCRASE l'en-tête du client ($proxy_add_x_forwarded_for est INTERDIT)
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_read_timeout 75s;                             # le service envoie un ping toutes les 25 s
        proxy_send_timeout 75s;
        access_log /dev/stdout cbplay;
    }

    # repli SSE/long-poll : le POST est lu en entier par nginx avant d'être passé (un client lent ne tient pas un fil du service)
    location = /play/act {
        limit_conn cbplay_addr 20;
        limit_conn_status 429;
        client_max_body_size 64k;
        client_body_timeout 10s;
        proxy_request_buffering on;
        set $cb_play http://castbridge-play:8080;
        resolver 127.0.0.11 valid=30s;
        proxy_pass $cb_play;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header Connection "close";                # pas de keepalive amont : le service répond « Connection: close »
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_read_timeout 75s;
        access_log /dev/stdout cbplay;
    }

    # « /play » sans barre finale : la page de jeu (sinon nginx répondrait 301 vers /play/, que le service ne sert pas)
    location = /play {
        limit_conn cbplay_addr 20;
        limit_conn_status 429;
        set $cb_play http://castbridge-play:8080;
        resolver 127.0.0.11 valid=30s;
        proxy_pass $cb_play;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header Connection "close";
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_read_timeout 75s;
        access_log /dev/stdout cbplay;
    }

    # tout le reste de /play/ : page /play/j/<code>, flux SSE /play/events, long-poll /play/state, /play/.well-known/caps
    # ^~ : aucune expression régulière ailleurs dans le fichier ne peut capter ces adresses
    location ^~ /play/ {
        limit_conn cbplay_addr 20;
        limit_conn_status 429;
        client_max_body_size 64k;
        set $cb_play http://castbridge-play:8080;           # variable + resolver : nginx démarre même si castbridge-play est arrêté
        resolver 127.0.0.11 valid=30s;
        proxy_pass $cb_play;                                # sans URI : l'adresse d'origine est transmise telle quelle
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header Connection "close";
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;                                # SSE
        proxy_read_timeout 75s;                             # commentaire SSE toutes les 25 s, long-poll 25 s
        access_log /dev/stdout cbplay;
    }
    # --- fin castbridge-play ---
```

Notes : `Set-Cookie` du service (`__Host-cbp-<nonce>`) : **ne rien réécrire** dans nginx (`proxy_cookie_*` interdits). Le code de salle de `/play/j/<code>` apparaît dans `$uri` : accepté comme non secret (il est dans le lien d'invitation). `access_log /dev/stdout` envoie la ligne dans `sudo docker logs infra-nginx` ; si 2.2 a montré un volume sur `/var/log/nginx`, on peut remplacer par `access_log /var/log/nginx/castbridge-play.log cbplay;`. `client_header_timeout 10s` change le délai de **tout** le site (défaut 60 s) : si un client lent de l'API s'en plaint, le remonter à 20 s.

**3. Valider puis recharger (nginx est un conteneur ; il n'y a pas de `systemctl reload nginx` ici, à n'utiliser que sur un nginx installé en paquet) :**

```bash
$ sudo docker exec infra-nginx nginx -t              # attendu : « syntax is ok » et « test is successful » ; SINON ne pas recharger : retour arrière ci-dessous
$ sudo docker exec infra-nginx nginx -s reload       # rechargement à chaud : pas d'interruption des connexions de l'API
$ sudo docker logs --tail 5 infra-nginx
```

**Vérifier (essais, dans cet ordre) :**

```bash
# 1. l'API n'a pas changé de comportement
Mac$ curl -sS -o /dev/null -w '%{http_code}\n' https://bridge.sti-cm.com/api/v1/updates/public-key            # 200
# 2. la santé n'est PAS publique : 403 attendu (la lecture se fait par 127.0.0.1:7091, § 9)
Mac$ curl -sS -o /dev/null -w '%{http_code}\n' https://bridge.sti-cm.com/play/health                         # 403
# 3. la page de jeu : 200 et du HTML, avec et sans barre finale
Mac$ curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' https://bridge.sti-cm.com/play                  # 200 text/html…
Mac$ curl -sS -o /dev/null -w '%{http_code}\n' https://bridge.sti-cm.com/play/.well-known/caps               # 200
# 4. la poignée de main WebSocket : 101 attendu (curl attend ensuite : -m 5 l'arrête, code de sortie 28 normal)
Mac$ curl --http1.1 -i -N -m 5 \
        -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' \
        -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H 'Origin: https://bridge.sti-cm.com' \
        https://bridge.sti-cm.com/play/ws
#    attendu : HTTP/1.1 101 Switching Protocols … Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
# 5. une origine étrangère est refusée (code 4xx, jamais 101)
Mac$ curl --http1.1 -i -m 5 -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' \
        -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H 'Origin: https://exemple.invalid' https://bridge.sti-cm.com/play/ws | head -n 1
# 6. le journal ne contient pas la query string
Mac$ curl -sS -o /dev/null 'https://bridge.sti-cm.com/play/state?since=1&essai=NE-PAS-JOURNALISER'
$ sudo docker logs --tail 20 infra-nginx 2>&1 | grep '/play/state'          # attendu : « /play/state » SANS « ? » ni « essai »
# 7. le dernier saut de X-Forwarded-For est celui de nginx : un en-tête forgé par le client ne change pas l'adresse comptée
$ sudo docker exec infra-nginx nginx -T 2>/dev/null | grep -n 'X-Forwarded-For'      # chaque bloc /play/ : $remote_addr, jamais $proxy_add_x_forwarded_for
# 8. depuis un TÉLÉPHONE en DONNÉES MOBILES (pas le Wi-Fi) : ouvrir https://bridge.sti-cm.com/play : la page se charge, cadenas valide
```

**Revenir en arrière :**

```bash
$ sudo cp -p "$BAK" "$CONF"                           # BAK = la sauvegarde datée de l'étape 1
$ sudo docker exec infra-nginx nginx -t && sudo docker exec infra-nginx nginx -s reload
$ curl -sS -o /dev/null -w '%{http_code}\n' https://bridge.sti-cm.com/play                  # attendu : plus 200 de la page de jeu (la route n'existe plus)
```

Un retour arrière de nginx n'arrête pas le conteneur `castbridge-play` : il reste joignable par `127.0.0.1:7091` seulement (sans danger).

### 4.5 `worker_connections` : décision du propriétaire (nginx.conf n'est pas dans ce dépôt)

**Lire d'abord :** 2.2 et la ligne (4) de la vérification de la partie 2. Relevé : `worker_connections 1024`, `worker_processes auto` (4 vCPU : 4 workers). Capacité de proxification ≈ 4 × 1 024 / 2 = **2 048** connexions simultanées pour **tous** les sites (l'API comprise), moins que le plafond par défaut du service (3 000).

- **Option minimale (recommandée au départ, rien à modifier dans `/opt/infra`) :** laisser `CASTBRIDGE_PLAY_MAX_CONNECTIONS=1500` dans `.env.play` (déjà posé en 4.2) : au-delà, le service répond 503 lui-même, avant que nginx sature. À relever à l'usage : `rooms`/`connections` de `/play/health` (§ 9).
- **Option complète (DESIGN-W20 O-2) :** dans `/opt/infra/nginx/nginx.conf`, `worker_rlimit_nofile 8192;` dans le contexte principal et `worker_connections 4096;` dans `events {}`, puis remettre `CASTBRIDGE_PLAY_MAX_CONNECTIONS` à 3000. Même procédure que 4.4 : sauvegarde datée (`sudo cp -p /opt/infra/nginx/nginx.conf /opt/infra/nginx/nginx.conf.bak-<UTC>`), `nginx -t`, `nginx -s reload` ; vérifier la limite de descripteurs du conteneur (`ulimit -n`, 2.2) ≥ 8 192 ; retour arrière = restaurer la sauvegarde puis recharger.

**TLS et HSTS (lecture seule, O-3) :** `nginx -T | grep -E 'ssl_protocols|Strict-Transport'` (2.2). Si `ssl_protocols` n'est pas `TLSv1.2 TLSv1.3` (le fichier d'options de certbot le fixe d'ordinaire : à relever) ou si HSTS est absent, c'est une décision distincte du propriétaire (HSTS engage le domaine pour la durée annoncée) : **ne rien ajouter dans cette opération**.

**Pare-feu (O-7) :** rien à ouvrir (443 l'est). `sudo ufw status numbered` ne doit pas changer ; `ss -ltnp | grep 7091` ne montre que `127.0.0.1`. Le port de diagnostic 7443 (DESIGN-W20 D-W20-2) n'est **pas** couvert par ce runbook et reste fermé.

## 5. (c) Le fichier compose séparé

`backend/docker-compose.play.yml` (jamais fusionné avec `backend/docker-compose.yml`, qui n'est pas modifié) :

| Exigence | Dans le fichier |
|---|---|
| Image | `castbridge-play:current` (`:candidate` et `:previous` gérées par `deploy-server.sh`) |
| Port | `ports: ["127.0.0.1:7091:8080"]` (jamais `0.0.0.0`) |
| Réseau | `infra-net`, **externe** (créé par l'infrastructure, jamais par ce fichier) ; aucun accès à `castbridge-internal` (base) |
| Durcissement | `read_only: true`, `tmpfs: /tmp:size=32m`, `user: 10002:10002`, `cap_drop: [ALL]`, `no-new-privileges`, `mem_limit: 384m`, `ulimits nofile 16384` |
| Arrêt | `stop_grace_period: 30s` (le service annonce la maintenance et attend 25 s) |
| Santé | `curl -fsS http://127.0.0.1:8080/play/health` toutes les 30 s |
| État inscriptible | volume nommé `play-state:/var/lib/castbridge-play` (le conteneur est `read_only` : sans lui, la persistance des révocations échoue en silence) ; dossier créé et donné à l'uid 10002 par le `Dockerfile` |
| Lots | `/lots:ro` (libres, `CASTBRIDGE_PLAY_LOTS_DIR`) et `/reserved:ro` (réservés, `CASTBRIDGE_PLAY_RESERVED_DIR`), tous deux facultatifs ; jamais les réservés sur `LOTS_DIR` |
| Environnement | `env_file: .env.play` (modèle documenté variable par variable : `backend/.env.play.example`) ; aucune clé privée, aucun `CASTBRIDGE_ADMIN_TOKEN` |
| Confiance du proxy | `CASTBRIDGE_PLAY_TRUSTED_PROXIES` = sous-réseau d'`infra-net` **à relever** (variante `/32` : 4.2) ; le service refuse de démarrer sans elle (sauf `DIRECT=1`, staging) |

**Risque connu de la variante « sous-réseau » :** tout conteneur d'`infra-net` (donc aussi d'autres applications de l'hôte) pourrait joindre `castbridge-play:8080` en forgeant `X-Forwarded-For`. Conséquence : contourner les plafonds par adresse, pas d'accès aux données (le service n'a ni secret ni base). La variante `/32` supprime ce risque au prix d'une relecture après chaque recréation de `infra-nginx`. À réévaluer si un autre projet de l'hôte devient peu fiable.

Validation locale (aucun Docker lancé par l'agent) : le YAML est contrôlé par `python3 -c 'import yaml…'`. Sur le serveur : `cd <release>/backend && sudo docker compose -f docker-compose.play.yml config -q` (avec `.env.play` présent).

## 6. (d) `deploy-server.sh --service play`

Ajout **additif** : sans `--service` (ou `--service api`), le comportement est celui d'avant, octet pour octet (cas témoin du test).

| Commande | Effet |
|---|---|
| `bash tools/release/deploy-server.sh server-play-<v> --service play` | DRY-RUN : plan et script distant, aucune connexion |
| `… --service play --apply` | pousse le tag, extrait, construit, vérifie l'absence de salles, bascule, contrôle santé et port, lien `current-play` |
| `… --service play --push-only --apply` | pousse seulement le tag vers « bridge » |
| `bash tools/release/deploy-server.sh --status --service play --apply` | état (lecture seule) : release, santé, ports publiés, salles ouvertes, images |
| `bash tools/release/deploy-server.sh --rollback --service play --apply` | revient à `castbridge-play:previous` et à la release précédente (refuse s'il y a des salles, sauf `CB_PLAY_FORCE=1`) |

Variables propres au service : `CB_PLAY_LIVE_DIR` (défaut `~/castbridge/services/play` : contient `.env.play` en 0600, copié dans chaque release), `CB_PLAY_RUNTIME_IMAGE` (défaut : le digest relevé en 1), `CB_PLAY_FORCE=1`. Étapes distantes : extraction (`backend/docker-compose.play.yml`, `.env.play.example`, `backend/sql`, `android/core`, `android/gradle.properties`, `server-play`, `content/learn`, `content/langues`) dans `releases/<tag>-<UTC>` ; `docker build -f server-play/Dockerfile` depuis la **racine** de la release ; refus s'il y a des salles ; `castbridge-play:current` → `:previous`, `:candidate` → `:current` ; `compose --project-name castbridge-play up -d --no-build --no-deps castbridge-play` ; santé **et** port en `127.0.0.1` seulement (sinon retour arrière) ; liens `current-play` / `previous-play` ; ligne `deploy-play` dans `releases/HISTORY.log`. Ni `castbridge-api`, ni `castbridge-db`, ni `sti-*`, ni `infra-nginx` ne sont touchés. Test : `bash tools/tests/test_deploy_play.sh` (aucune connexion : faux `ssh`, faux `docker`).

Limite : le script **ne modifie jamais nginx** ; la route publique reste l'acte manuel 4.4. Il ne sauvegarde pas de base (le service n'en a pas).

## 7. (e) Base de données (facultatif, après w20-09)

**Lire d'abord :** `backend/sql/play-schema.sql`. `castbridge-play` (w20-03) n'utilise aucune base ; ne rien appliquer avant w20-09. Le mot de passe n'est jamais dans un fichier : il est substitué au vol.

```bash
$ PW=$(openssl rand -base64 36 | tr -d '/+=' | cut -c1-40)                       # n'est pas affiché ; à conserver dans le gestionnaire de secrets du propriétaire
$ sed "s|__MOT_DE_PASSE_PLAY__|$PW|" "$ROOT/current-play/backend/sql/play-schema.sql" \
    | sudo docker exec -i castbridge-db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'
```

**Vérifier :** `sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "SHOW GRANTS FOR '"'"'castbridge_play'"'"'@'"'"'%'"'"'"'` : droits limités à ``castbridge_play`.*``. **Revenir en arrière :** sauvegarde (`backend/backup.sh --db-only`), puis `DROP USER` et `DROP DATABASE` (commentés en bas du script SQL).

## 8. `assetlinks.json` (facultatif ; pas dans le périmètre des joueurs web)

Pour ouvrir `https://bridge.sti-cm.com/play/j/<code>` directement dans l'application CastBridge (lien d'application, w20-06). **L'empreinte du certificat de signature (`release.jks`) est à fournir par le propriétaire et n'est jamais écrite dans le dépôt.** Lecture de l'empreinte sur le Mac (SHA-256 du certificat, pas la clé) : `keytool -list -v -keystore <release.jks> -alias <alias>` (le mot de passe est saisi à l'invite).

```nginx
    # dans le server { } de bridge.sti-cm.com, à côté de /play/ (chemin exact : ne gêne pas /.well-known/acme-challenge/ de certbot)
    location = /.well-known/assetlinks.json {
        default_type application/json;
        return 200 '[{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"castbridge.sender","sha256_cert_fingerprints":["<EMPREINTE_SHA256_DU_CERTIFICAT : à fournir par le propriétaire>"]}}]';
    }
```

(`castbridge.sender` est l'identifiant technique de l'application du téléphone : à confirmer dans `android/sender/build.gradle.kts`.) Sauvegarde, `nginx -t`, `nginx -s reload` comme en 4.4. **Vérifier :** `curl -sS https://bridge.sti-cm.com/.well-known/assetlinks.json | python3 -m json.tool`. **Revenir en arrière :** restaurer la sauvegarde.

## 9. (g) Surveillance

`GET /play/health` ne contient aucun secret (ni adresse, ni code de salle, ni jeton) : `status`, `version`, `proto`, `rooms`/`maxRooms`, `connections`/`maxConnections`, `memoryUsedMb`/`memoryMaxMb`, `uptimeSec`. Il est **réservé à 127.0.0.1** dans nginx (4.4) : la surveillance le lit par le port local `127.0.0.1:7091` (pas par l'adresse publique, qui donne 403).

**Commande de contrôle** (sortie `OK` ou `ALERTE …`, code de sortie 0/1 ; sans `jq`) :

```bash
curl -fsS --max-time 5 http://127.0.0.1:7091/play/health | python3 -c '
import json, sys
h = json.load(sys.stdin); bad = []
if h["status"] != "ok": bad.append("status=" + h["status"])
if h.get("revocations") != "ok": bad.append("revocations=" + str(h.get("revocations")) + " (aucune salle ne s'ouvre : 4.0)")
if h["connections"] > 0.8 * h["maxConnections"]: bad.append("connexions %d/%d (>80 %%)" % (h["connections"], h["maxConnections"]))
if h["rooms"] > 0.8 * h["maxRooms"]: bad.append("salles %d/%d (>80 %%)" % (h["rooms"], h["maxRooms"]))
if h["memoryUsedMb"] > 0.85 * h["memoryMaxMb"]: bad.append("tas %d/%d Mo (>85 %%)" % (h["memoryUsedMb"], h["memoryMaxMb"]))
print("ALERTE " + "; ".join(bad) if bad else "OK rooms=%d connexions=%d tas=%dMo" % (h["rooms"], h["connections"], h["memoryUsedMb"]))
sys.exit(1 if bad else 0)' || echo "ALERTE : /play/health injoignable ou illisible"
```

À brancher comme la sonde existante (`ops/monitoring/`, toutes les 5 minutes, `docs`/`backend/README.md` § « Supervision et astreinte ») : exemple de cron à ajouter par le propriétaire : `*/5 * * * * <la commande ci-dessus> | logger -t castbridge-play-check`. Seuils : 400 salles, 3 000 connexions (1 500 tant que 4.5 n'est pas fait), 24 par adresse, 512 par /48 (plafonds du service). Alerte supplémentaire (DESIGN-W20 O-10) : plus de 1 000 codes faux par heure (le service ne journalise pas les requêtes : à approximer par le nombre de 4xx de `/play/ws` dans les journaux nginx).

| Quoi | Où | Commande |
|---|---|---|
| État du conteneur | Docker | `sudo docker ps --filter name=castbridge-play --format '{{.Names}} {{.Status}}'` ; `sudo docker inspect castbridge-play -f '{{.State.OOMKilled}} {{.RestartCount}}'` |
| Journaux du service (démarrage, refus, arrêt ; **aucune requête**) | `json-file`, 10 Mo × 3 | `sudo docker logs --tail 200 -f castbridge-play` |
| Journal d'accès de `/play/` (sans query string) | journal de `infra-nginx` | `sudo docker logs --since 1h infra-nginx > ~/n.log 2>&1; grep -e '"GET /play' -e '"POST /play' ~/n.log` (supprimer `~/n.log` ensuite : il contient des adresses ; ou le fichier de 4.4 si un volume existe) |
| Erreurs nginx | journal de `infra-nginx` | `grep -e '[error]' -e '[crit]' -e '[alert]' -F ~/n.log` (après la ligne précédente) |
| Mémoire | Docker | `sudo docker stats --no-stream castbridge-play` |
| Version en service | script | `bash tools/release/deploy-server.sh --status --service play --apply` |

Les 5 commandes d'astreinte : `sudo docker ps`, `/play/health` (ci-dessus), `sudo docker logs --tail 200 castbridge-play`, `deploy-server.sh --status --service play --apply`, `deploy-server.sh --rollback --service play --apply`. **Arrêt volontaire** (heure creuse) : vérifier `rooms` = 0, puis `sudo docker stop castbridge-play` (annonce « maintenance » et attend jusqu'à 25 s ; le conteneur est tué à 30 s).

## 10. Retour arrière global et faits à confirmer

**Tout retirer, dans l'ordre :** (1) route nginx : restaurer `$BAK` (4.4), `nginx -t`, `nginx -s reload` ; (2) service : `sudo docker stop castbridge-play && sudo docker rm castbridge-play` (le projet `castbridge-play` seulement) ; (3) images : `sudo docker rmi castbridge-play:current castbridge-play:previous` ; (4) `~/castbridge/services/play/` si le service est abandonné ; (5) la clé privée du ticket : `sudo shred -u <release>/secrets/play-ticket.key` et le bloc de l'override de l'API. `castbridge-api`, `castbridge-db`, `sti-*`, `infra-nginx` (hors la route) et le pare-feu n'ont jamais été modifiés par ce runbook.

**Faits à confirmer par le propriétaire sur la machine (B-W20-1, B-W20-2) :**

| Fait | Comment | Statut |
|---|---|---|
| vCPU (B-W20-2) | `nproc` | **4** (relevé 2026-10-03) ; capacités du DESIGN-W20 § 2.3 à confirmer par une mesure réelle |
| Contenu réel de `castbridge.conf` (B-W20-1) | 2.2 | à relever : le runbook suppose un seul `server` pour `bridge.sti-cm.com` avec `location /` |
| `/opt/infra` géré par git (utilisateur `git`) | 2.3 | à relever |
| Dossier `snippets/` inclus par `nginx.conf` | `nginx -T \| grep include` | **non vérifié** ; non utilisé |
| `ssl_protocols`, HSTS | 2.2 | à relever |
| `/var/log/nginx` en volume, limite `ulimit -n` de `infra-nginx` | 2.2 | à relever |
| Sous-réseau et adresse de `infra-net` / nginx | 2.4 | à relever |
| Taille de l'image construite, mémoire au repos, durée de la première construction | 3.2, 3.4 | à relever (jamais construite avant) |
| Contenu de `docker-compose.override.yml` de l'API | 2.6 | à relever avant 4.1 |
| Variable de clé privée côté API | `CASTBRIDGE_PLAY_TICKET_KEY_FILE` (`application.yml` : `castbridge.play.ticket-key-file`, fichier secret `play-ticket.key`, vide = la route de ticket répond 503) | confirmée à la fusion de w20-04 |
| Pare-feu du fournisseur (hors `ufw`) | panneau du fournisseur | non vérifié : sans effet ici (443 déjà ouvert, 7091 local) |

**Non fait par ce cahier :** aucune exécution sur le serveur ; pas d'émetteur de tickets (w20-04) ; pas de base utilisée (w20-09) ; pas de `map $http_upgrade` (impossible sans modifier `nginx.conf`) ; pas de 7443, pas de DNS `play.` ; l'empreinte `assetlinks.json` n'est pas écrite.
