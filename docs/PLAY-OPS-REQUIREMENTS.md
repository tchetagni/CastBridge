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

12. `CASTBRIDGE_PLAY_TICKET_PUBKEY` : clé **publique** Ed25519 du ticket (w20-04 produit l'émetteur) ; sans elle, aucune salle ne peut s'ouvrir (voulu). Aucune clé privée, aucune variable `*_KEY`/`*_TOKEN` dans l'environnement du conteneur.
13. `CASTBRIDGE_PLAY_LOTS_DIR` : dossier monté en **lecture seule** ; le contenu réservable n'y est pas avant w20-04.

## Exploitation

14. **Déployer hors partie.** Le service ne migre pas les salles : un redémarrage les perd. À l'arrêt (SIGTERM), il annonce « maintenance » aux salles, refuse les nouvelles et attend jusqu'à 25 s ; une partie en cours est interrompue au-delà. Choisir une heure creuse et vérifier `/play/health` (`rooms`) depuis la VM avant de relancer.
15. Surveillance : `rooms`, `connections`, `memoryUsedMb` de `/play/health` (depuis 127.0.0.1) ; plafonds 400 salles / 3 000 connexions / 8 par adresse.

## Ce qui reste hors de ce cahier

- **I1** (ticket rejouable, `jti`, plafond de salles par sujet) : exigence du cahier w20-04.
- Le mode strictement « SSE/long-poll derrière un second proxy » et la limitation de débit globale (hors nginx) ne sont pas traités.
