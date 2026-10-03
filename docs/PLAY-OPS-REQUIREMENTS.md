# castbridge-play : exigences de déploiement (audit Opus de w20-03, 2026-10-03)

> Le code seul ne suffit pas : le service de jeu est exposé à Internet derrière **le nginx partagé**. Tant que les points ci-dessous ne sont pas faits **par l'exploitant** (rien n'est fait depuis le dépôt), **pas d'exposition Internet**. Cahier d'écriture des fichiers : w20-08. Le service : `server-play/README.md`.

## Réseau et conteneur

1. **Port publié uniquement sur `127.0.0.1:7091`** (`127.0.0.1:7091:8080`), jamais `0.0.0.0` ; le conteneur `castbridge-play` rejoint `infra-net` pour que nginx l'atteigne par son nom Docker.
2. **`CASTBRIDGE_PLAY_TRUSTED_PROXIES` = l'adresse exacte de nginx en `/32`** (adresse du conteneur `infra-nginx` sur `infra-net`, à lire avec `docker inspect`, et à fixer par une adresse statique du réseau). Le défaut du service est désormais **aucun proxy de confiance** : sans cette variable, tous les clients passent pour l'adresse de nginx et partagent UN plafond de 8 connexions. Ne jamais mettre un réseau large (`172.16.0.0/12`).
3. Conteneur : `read_only: true`, `tmpfs /tmp`, `cap_drop: ALL`, `mem_limit: 384m`, non-root (uid 10002), aucun accès aux secrets de licence/activation, `stop_grace_period: 30s` (le service laisse 25 s de grâce à l'arrêt).
4. Image : `eclipse-temurin:25-jre` si le tag existe dans le registre (JEP 491 : plus d'épinglage des fils virtuels), sinon `21-jre` (les verrous explicites du code suffisent). Le `Dockerfile` n'a pas été construit à ce stade.

## nginx (bloc `location /play/` dans `/opt/infra/nginx/conf.d/castbridge.conf`)

5. `proxy_set_header X-Forwarded-For $remote_addr;` **dans `/play/`** : l'en-tête est **écrasé**, jamais complété (`$proxy_add_x_forwarded_for` interdit : le client pourrait y mettre un saut).
6. **Pas de keepalive amont** : `proxy_http_version 1.1` pour l'upgrade WebSocket, `proxy_set_header Connection $connection_upgrade` (la table `map` habituelle), aucun bloc `upstream` avec `keepalive` : le service répond `Connection: close`.
7. **`limit_conn`** par adresse sur `/play/` (zone `limit_conn_zone $binary_remote_addr`, ≈ 20 connexions) en plus du plafond du service (8 par adresse, IPv6 par /64) ; `client_header_timeout 10s`, `client_body_timeout 10s`.
8. **`proxy_request_buffering on`** pour `POST /play/act` (nginx lit le corps entier avant de le passer : un client lent ne tient pas un fil du service) ; `proxy_buffering off` pour `/play/events` (SSE) ; `proxy_read_timeout 75s` (le service envoie un ping WebSocket toutes les 25 s et un commentaire SSE toutes les 25 s).
9. **`/play/health` réservé à `127.0.0.1`** : `location = /play/health { allow 127.0.0.1; deny all; ... }` (il donne le nombre de salles et de connexions).
10. **`access_log` de `/play/` sans query string** (format dédié avec `$uri` au lieu de `$request`) : aucun secret, aucun code de salle dans les journaux (`/play/j/{code}` porte le code de la salle : le masquer aussi, ou l'accepter comme non secret). Le secret de session de repli est un cookie, jamais dans l'adresse ; le service lui-même ne journalise aucune requête.
11. TLS : certificat valide pour `bridge.sti-cm.com` ; `CASTBRIDGE_PLAY_ORIGINS=https://bridge.sti-cm.com` (défaut).

## Clés et configuration

12. `CASTBRIDGE_PLAY_TICKET_PUBKEY` : clé **publique** Ed25519 du ticket (`play-ticket.pub.b64` produit par `tools/play/gen-ticket-keypair.sh`) ; sans elle, aucune salle ne peut s'ouvrir (voulu). Aucune clé privée, aucune variable `*_KEY`/`*_TOKEN` dans l'environnement du conteneur.
13. `CASTBRIDGE_PLAY_LOTS_DIR` (lots libres) et `CASTBRIDGE_PLAY_RESERVED_DIR` : dossiers montés en **lecture seule** ; les paquets réservés ne sont pas publiés tant que le gel `content/quiz/reserved-ids.json` n'est pas fait (`docs/agent-reports/quiz-toutes-les-questions.md`, ordre a-e) : sans `reserved-ids.json`, le service ne sert **aucune** question réservée.
14. `CASTBRIDGE_PLAY_TRUSTED_KEYS` : clés **publiques** des émetteurs d'activations (même valeur que `CASTBRIDGE_LICENSES_TRUSTED_KEYS` de l'API, format `nom:clé:PORTÉES`) ; sans elle, aucune activation n'est valable, donc aucune salle. `CASTBRIDGE_PLAY_REVOCATIONS_URL=https://bridge.sti-cm.com/api/v1/revocations` (seul appel sortant du service, lecture publique) ; le réseau du conteneur ne doit joindre que l'API pour cela.
15. `CASTBRIDGE_PLAY_REVOCATIONS_URL` : adresse **https** de `GET /api/v1/revocations` (audit w20-04 B2). **Obligatoire** : sans elle le service REFUSE de démarrer (comme les proxies de confiance), sauf `CASTBRIDGE_PLAY_DIRECT=1` (staging). Tant qu'aucune liste signée n'a été acceptée, ou si la dernière date de plus de 24 h, `create` répond « Service indisponible » (`PLAY_MAINTENANCE`, à réessayer) et le ticket n'est pas brûlé.
16. `CASTBRIDGE_PLAY_REVOCATIONS_FILE` : fichier sur un **volume** (ex. `/var/lib/castbridge-play/revocations.txt`) où la dernière liste valide est écrite (atomiquement) et relue au démarrage (signature et `issuedAt` revérifiés) : un redémarrage ne repart pas « sans liste » si l'API est injoignable.
17. `CASTBRIDGE_PLAY_CREATES_PER_IDENTITY_DAY` (30) et `CASTBRIDGE_PLAY_CREATES_PER_48_HOUR` (200) : créations de salle par activation signée et par jour, et par /48 IPv6 (ou adresse IPv4) et par heure ; le plafond de salles ouvertes (`CASTBRIDGE_PLAY_MAX_ROOMS_PER_SUBJECT`) compte aussi l'activation signée, pas seulement l'appareil API. `CASTBRIDGE_PLAY_MAX_USED_TICKETS` vaut 200 000 par défaut.

**État en mémoire (mono-instance).** `jti` utilisés, compteurs d'adresses, d'identités et parties d'essai du jour vivent en MÉMOIRE : un redémarrage permet de rejouer UNE fois un ticket encore valable (< 10 min) et remet à zéro l'essai du jour. Acceptable avec une seule instance ; ne jamais lancer deux instances sans stockage partagé. Les droits ne sont jugés qu'à la CRÉATION : une location échue en cours de partie ne change pas la banque de la salle avant sa fin. En ligne, les locations à l'heure (usage en minutes) ne sont pas honorées (le service ne compte pas les minutes) ; seules les lignes à la journée le sont.

## Clé des tickets (étape de l'exploitant, w20-04)

À faire **par l'exploitant**, rien n'est fait depuis le dépôt :

1. Sur un poste de confiance (jamais sur la VM de production), `tools/play/gen-ticket-keypair.sh <dossier-secret>` : écrit `play-ticket.key` (privée, mode 600), `play-ticket.pub` et `play-ticket.pub.b64` (publiques) ; **refuse d'écraser** ; n'affiche que le chemin de la clé publique et son empreinte (SHA-256 du SPKI).
2. Noter l'**empreinte** hors ligne ; déposer la privée dans `backend/secrets/play-ticket.key` de la VM (`sudo chown 10001:10001` puis `sudo chmod 400` : le conteneur tourne sous l'uid 10001, un `chmod 600` par `ubuntu` le rendrait illisible, règle de `docs/DEPLOIEMENT-PREMIERE-EXPERIENCE.md` § 2.1 ; jamais dans le dépôt, jamais dans une sauvegarde non chiffrée), puis dans `backend/docker-compose.yml` monter le secret `play_ticket_key` (déjà **déclaré**) sur `castbridge-api` et fixer `CASTBRIDGE_PLAY_TICKET_KEY_FILE=/run/secrets/play_ticket_key`. Tant que ce n'est pas fait, `POST /api/v1/play/ticket` répond 503.
3. Mettre le **contenu** de `play-ticket.pub.b64` (une ligne) dans `CASTBRIDGE_PLAY_TICKET_PUBKEY` du conteneur `castbridge-play` ; ne jamais y copier la privée ni son chemin.
4. **Rotation** : générer une nouvelle paire dans un **autre** dossier, mettre l'ancienne et la nouvelle publiques dans `CASTBRIDGE_PLAY_TICKET_PUBKEY` et `_2`, remplacer la privée de l'API, attendre 15 min (vie maximale d'un ticket), retirer l'ancienne publique.
5. Vérifier : `docker inspect` du conteneur `castbridge-play` ne montre aucune clé privée ni `play_ticket_key` ; `GET /play/health` (depuis la VM) dit `"revocations":"ok"` après 15 min.

## Exploitation

14. **Déployer hors partie.** Le service ne migre pas les salles : un redémarrage les perd. À l'arrêt (SIGTERM), il annonce « maintenance » aux salles, refuse les nouvelles et attend jusqu'à 25 s ; une partie en cours est interrompue au-delà. Choisir une heure creuse et vérifier `/play/health` (`rooms`) depuis la VM avant de relancer.
15. Surveillance : `rooms`, `connections`, `memoryUsedMb` de `/play/health` (depuis 127.0.0.1) ; plafonds 400 salles / 3 000 connexions / 8 par adresse.

## Ce qui reste hors de ce cahier

- **I1** (ticket rejouable, `jti`, plafond de salles par sujet) : **fermée par w20-04** (`jti` à usage unique, salles par appareil, créations par adresse) ; à auditer par Opus.
- Le mode strictement « SSE/long-poll derrière un second proxy » et la limitation de débit globale (hors nginx) ne sont pas traités.

## Second audit Opus (2026-10-03) : points ajoutés

- **`CASTBRIDGE_PLAY_TRUSTED_PROXIES` est OBLIGATOIRE** : le service refuse de démarrer s'il est absent ou contient une entrée invalide (message sur `System.err`, code de sortie 2). Seule exception : `CASTBRIDGE_PLAY_DIRECT=1` (staging et tests, accès direct sans proxy, avertissement au démarrage) ; elle n'excuse pas une entrée invalide. Un `X-Forwarded-For` illisible venant du proxy de confiance est refusé (400).
- **Compose (écrit par w20-08)** : `stop_grace_period: 30s` obligatoire (le service annonce la maintenance et attend jusqu'à 25 s ; Docker tue à 10 s par défaut). Extrait attendu :
  ```
  castbridge-play:
    read_only: true
    tmpfs: [/tmp]
    stop_grace_period: 30s
    mem_limit: 384m
    ports: ["127.0.0.1:7091:8080"]
    environment:
      CASTBRIDGE_PLAY_TRUSTED_PROXIES: "<adresse exacte de nginx>/32"
  ```
- **Pas de blocage collectif** : un `resume` n'est jamais bloqué ni compté par adresse ; seuls les CODES faux de `join` comptent (30 par adresse IPv4 et par 5 min, 120 par /48 IPv6). Plafonds de connexions : 8 par adresse IPv4 ou /64, 64 par /48 IPv6.
- **Cookies de repli par onglet** : `__Host-cbp-<nonce>` (`Secure`, `Path=/`, `HttpOnly`, `SameSite=Strict`) : ne pas réécrire ni retirer le `Set-Cookie` dans nginx ; le cookie ne passe qu'en HTTPS (`Secure`).
- **Image** : `RUNTIME_IMAGE` (défaut `eclipse-temurin:25-jre`) se construit par digest : `docker pull eclipse-temurin:25-jre`, puis `docker inspect --format '{{index .RepoDigests 0}}' eclipse-temurin:25-jre`, puis `--build-arg RUNTIME_IMAGE=eclipse-temurin:25-jre@sha256:<digest>`. Le digest lu est à noter ici par l'exploitant (aucun digest n'est inventé dans le dépôt) : _digest : à renseigner_.

## Staging NON public (autorisé par le second audit ; la route nginx `/play/` publique reste interdite)

- `CASTBRIDGE_PLAY_DIRECT=1`, `CASTBRIDGE_PLAY_ORIGINS=http://localhost:7091`, `CASTBRIDGE_PLAY_MAX_PER_IP=64` (tous les testeurs partagent l'adresse du tunnel), `CASTBRIDGE_PLAY_TICKET_PUBKEY=<clé publique de test>`, port publié sur `127.0.0.1:7091` seulement, `stop_grace_period: 30s`.
- Accès par tunnel SSH vers `localhost:7091` (jamais d'ouverture du pare-feu).
- **Cookies `Secure` sur `http://localhost`** : Chrome et Firefox les acceptent (contexte sûr) ; **Safari les refuse** : la page de repli (SSE, long-poll) ne tient donc pas sous Safari en staging ; le WebSocket n'en a pas besoin, et en production (HTTPS) le problème n'existe pas.
- Pas de clé de production, pas de lots réservés, aucune route publique.

## Conditions avant la route nginx `/play/` publique

1. `CASTBRIDGE_PLAY_TRUSTED_PROXIES` posé à l'adresse exacte de nginx (/32) ; 2. règles nginx des points 5 à 10 ci-dessus ; 3. `stop_grace_period: 30s` dans le compose réel ; 4. image construite et son digest noté ; puis w20-04 pour le ticket à usage unique (I1).
