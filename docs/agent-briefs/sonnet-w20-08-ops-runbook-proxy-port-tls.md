# w20-08 — Exploitation : `docs/PLAY-OPS.md` (nginx `/play/`, WebSocket, `worker_connections`, TLS, `ufw`, port de diagnostic), compose additif `castbridge-play`, script SQL, `deploy-server.sh --service play`, surveillance ; `assetlinks.json`
<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune (documents et scripts **non exécutés**) · statut : PRÊT (après w20-03)
> **Groupe : W20-S3** (outils + docs) · prérequis : w20-03 fusionné · porte : `bash -n tools/release/deploy-server.sh && bash tools/release/deploy-server.sh server-play-0.1.0 --service play` (**DRY-RUN**, aucune connexion) + `docker compose -f backend/docker-compose.yml config -q` (avec un `.env` factice local)
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S, ≈ 1 j) · audit Opus : non

**Vague 20 · Effort S · Modèle : sonnet · Statut PRÊT (après w20-03).** Conception : `DESIGN-W20` § 2.1, § 2.3, § 2.9 (O-1…O-10), § 7 (B-W20-1). Branche `claude/sonnet-w20-08`. Rapport : `docs/agent-reports/sonnet-w20-08.md`. **Interdiction absolue** : `ssh`, `docker` contre `bridge.sti-cm.com`, `--apply`, toute connexion réseau sortante. Le cahier **écrit ce que le propriétaire exécutera**, avec des chemins **proposés** et une case « à confirmer sur la machine » pour chaque fait inconnu.

## Objectif
(1) `docs/PLAY-OPS.md` : le runbook O-1…O-10 **mot pour mot exécutable** : bloc `map $http_upgrade`, `location /play/` (upgrade, `X-Forwarded-For $remote_addr` **écrasé**, `proxy_read_timeout 75s`, `proxy_buffering off`, `client_max_body_size 64k`, `limit_conn`), `worker_connections 4096` + `worker_rlimit_nofile`, `ssl_protocols TLSv1.2 TLSv1.3`, HSTS, `nginx -t` + rechargement, **sauvegarde préalable** du fichier, commandes de **lecture** préalables (`nginx -T | grep -E 'worker_connections|ssl_protocols|server_name bridge'`, `ufw status numbered`, `ss -ltnp | grep -E '7090|7091'`), port de diagnostic 7443 (ouverture ciblée `ufw allow from <IP> to any port 7443 proto tcp`, fermeture), DNS `play.` (plus tard), `assetlinks.json` à `https://bridge.sti-cm.com/.well-known/assetlinks.json` (empreinte du certificat de signature `release.jks` **à fournir par le propriétaire**, jamais écrite ici), rollback, **ce qu'on vérifie après** (`curl -I https://bridge.sti-cm.com/play/health`, `wscat`/`websocat` facultatif, un test depuis un téléphone en données mobiles). (2) Compose : service `castbridge-play` dans `backend/docker-compose.yml` (zone additive : image `castbridge-play:current`, `127.0.0.1:${CASTBRIDGE_PLAY_PORT:-7091}:8090`, `mem_limit 384m`, `read_only`, `tmpfs /tmp 32m`, `cap_drop ALL`, `no-new-privileges`, `ulimits nofile 16384`, réseaux `castbridge-internal` + `castbridge-edge`, volumes `castbridge-apk:/data/apk:ro`, secret **public** `play_ticket_pub`, variables `CASTBRIDGE_PLAY_*`, healthcheck `GET /play/health`, logging json 10m×3) ; `backend/.env.example` (clés ajoutées, sans valeur). (3) `backend/sql/play-schema.sql` (O-5 : base + utilisateur limité ; mot de passe = variable). (4) `tools/release/deploy-server.sh` : option `--service play` (image `castbridge-play:candidate` construite depuis `server-play/` extrait du dépôt bare, bascule `current/previous`, healthcheck `/play/health`, rollback ; **le service `castbridge-api` n'est pas touché**) ; `--status` affiche aussi `play`. (5) Surveillance : ajout dans la doc de `sonnet-w1-12` (ou `backend/README.md` § surveillance) : `GET /play/health` toutes les 5 min, alertes § O-10.

## Pourquoi (preuves)
- `backend/README.md:147-190` : deux propositions nginx **non appliquées** (sous-domaine, chemin) : base à étendre avec l'upgrade WebSocket, absent partout dans le dépôt (vérifié).
- `backend/docker-compose.yml:88-104` : modèle de durcissement à copier ; `:106-111` réseaux ; `:119-121` secrets.
- `tools/release/deploy-server.sh:24-37` : étapes 3-7 (release, image candidate, sauvegarde, bascule, santé, rollback) : à paramétrer par service, **sans** changer le comportement pour `castbridge-api`.
- `docs/SERVER-STRATEGY.md:4` : toute modification du nginx/certbot partagé = sauvegarde, `nginx -t`, **approbation explicite du propriétaire** ; `:7-8` : VM 7,8 Go, ≈ 4,6 Go disponibles.
- `docs/REMOTE-TUNNEL.md:118` : `ufw allow 2200/tcp` : précédent de la forme des commandes `ufw`.

## Fichiers possédés
- Nouveaux : `docs/PLAY-OPS.md`, `backend/sql/play-schema.sql`, `backend/.env.example` (s'il n'existe pas ; sinon zone additive).
- Zones additives : `backend/docker-compose.yml` (service + secret + variables, **rien de modifié** dans `castbridge-api`/`castbridge-db`), `tools/release/deploy-server.sh` (option `--service`, défaut `api` = comportement actuel octet pour octet), `backend/README.md` (§ « Service de jeu en ligne », renvoi), `docs/RELEASES.md` (§ serveur : tag `server-play-<v>`).
- Interdit : tout fichier sous `server-play/src/` (w20-03), `backend/src/`.

## Étapes
1. **Rouge** : `tools/release/tests/test_deploy_server_play.sh` (ou extension du test existant s'il y en a un) : `--service play` en DRY-RUN imprime le plan avec `castbridge-play:candidate` et `/play/health` ; sans `--service` : sortie **identique** à avant (diff vide sur un cas témoin).
2. `PLAY-OPS.md` (structure : Avant / Lire la machine / O-1…O-10 / Vérifier / Revenir en arrière / Faits à confirmer B-W20-1/B-W20-2).
3. Compose + SQL + `.env.example` ; `docker compose config -q` localement avec un `.env` factice.
4. `deploy-server.sh --service play` ; `--status`.
5. **Vert** (DRY-RUN) ; rapport.

## Critères d'acceptation
- Aucune commande du runbook n'est exécutée par l'exécutant ; chaque bloc commence par « Lire d'abord : … » et finit par « Vérifier : … ».
- `deploy-server.sh` sans `--service` : comportement inchangé (test témoin). `--service play --apply` refuse `main` et un commit nu comme aujourd'hui.
- `docker compose config -q` passe ; `castbridge-play` n'a **aucun** secret privé ni `CASTBRIDGE_ADMIN_TOKEN` dans son environnement (`grep` du fichier dans le rapport).

## Cas limites
- Le nginx est un conteneur (`infra-nginx`) et non un paquet système : le runbook donne les **deux** variantes (`docker exec infra-nginx nginx -t` / `systemctl reload nginx`). `worker_connections` déjà ≥ 4 096 ⇒ étape O-2 « rien à faire ».

## À ne pas faire
- Pas de `--apply`, pas de `ssh`, pas de secret (même factice ressemblant à un vrai), pas de modification de `infra-*`. Ne pas écrire l'empreinte d'`assetlinks.json`.

## Rapport
Format RAPPORT + `SYMBIOSE: cap=— · proto=— · reason=— · deux écrans=—` + liste des **faits à confirmer** par le propriétaire (B-W20-1, B-W20-2) + sortie du DRY-RUN.
