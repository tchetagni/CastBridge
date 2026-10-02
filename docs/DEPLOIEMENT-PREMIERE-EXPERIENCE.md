# Déploiement et première expérience complète (TV + téléphone + serveur)

> Guide pas à pas pour quelqu'un qui n'est pas expert en serveurs. **Aucun secret dans ce document** (seulement des noms de variables et de fichiers).
> Branche déployée : `origin/integration/agents` (décision du propriétaire). **On ne fusionne rien dans `main`.**
> Rédigé le 2026-10-02 d'après le code du dépôt. Tout ce qui n'a pas pu être vérifié est listé dans « Points à confirmer » (en fin de document) : **lisez-les avant l'étape 4.**
> Vérification automatique du serveur : `ops/first-run/check-server.sh` (lecture seule).

**Légende de chaque étape** : *Quoi* · *Pourquoi* · *Commande* · *Vérifier* · *Annuler*.

**Vue d'ensemble de l'ordre (il compte)**

```
0 Prérequis  →  1 Sauvegarde + photo de l'état  →  2 Clés (serveur) puis clé publique dans la TV
   →  3 Choix des modules  →  4 Déploiement (module licences ÉTEINT, puis allumé)  →  5 Contenu libre
   →  6 Contrôles  →  7 Scénario chez vous (TV + téléphone)  →  8 Si ça tourne mal
```

Règle d'or : **la TV doit connaître la clé publique d'un outil avant que cet outil émette quoi que ce soit** (étape 2.4 avant la construction de la TV de l'étape 7).

---

## 0. Prérequis : ce que vous devez avoir sous la main

| Besoin | Détail |
|---|---|
| Accès SSH au serveur | Compte `ubuntu@bridge.sti-cm.com` (d'après `docs/HANDOFF.md` § 4). Le guide suppose un compte **membre du groupe `docker`** ; sinon préfixez `docker` par `sudo` ou exportez `DOCKER_CMD="sudo docker"` (lu par `deploy.sh` et `backup.sh`). |
| Le code poussé | Voir étape 1.0 : **le code doit être commité et poussé** sur la branche `integration/agents` du dépôt que le serveur interroge. |
| Domaine et HTTPS | `https://bridge.sti-cm.com`, déjà servi par le nginx partagé (rien à changer ici). |
| Dossier du serveur | D'après `docs/HANDOFF.md` : `~/castbridge/services/castbridge/backend` (à confirmer, voir « Points à confirmer »). Dans ce guide : `BACKEND=~/castbridge/services/castbridge/backend`. |
| Compte d'administration web | Existe déjà (créé au premier démarrage). Variables concernées, **noms seulement** : `CASTBRIDGE_WEB_ADMIN_USER`, `CASTBRIDGE_WEB_ADMIN_PASSWORD` (lue seulement pour créer le compte), `CASTBRIDGE_WEB_ADMIN_RESET_PASSWORD`. |
| Valeurs de `backend/.env` (déjà présentes, à ne pas changer) | `CASTBRIDGE_DB_PASSWORD`, `CASTBRIDGE_DB_ROOT_PASSWORD`, `CASTBRIDGE_ADMIN_TOKEN` (≥ 32 caractères), `CASTBRIDGE_SIGNING_KEY_PATH`, `CASTBRIDGE_PUBLIC_BASE_URL`, `CASTBRIDGE_PORT`, `CASTBRIDGE_COOKIE_SECURE`, `DEPLOY_REF`. |
| Application TOTP | FreeOTP, Aegis ou Google Authenticator sur votre téléphone (double authentification de l'administration des licences, étape 4.5). |
| Sur votre Mac | Dépôt à jour, Android SDK + Gradle (commandes de `docs/HANDOFF.md` § 5), dossier `~/.castbridge-signing/` (clés de confiance de la TV, **jamais dans Git, jamais copié ailleurs**), outil `python3`, `scp`. |
| Téléphone et TV | Le Samsung S21+ (utilisateur principal, **pas la copie « Dual App »** où la permission Bluetooth est refusée) et la TV de référence (GaiaOS, 32 bits). |
| Une personne de confiance joignable | Voir § 8 (contact : à renseigner). |

Un jeton d'administration se lit **sans l'afficher** (jamais dans un message, un courriel ou un capture d'écran) :

```sh
read -rs CB_ADMIN_TOKEN; export CB_ADMIN_TOKEN     # collez le jeton, Entrée (rien ne s'affiche)
```

---

## 1. Avant de toucher à quoi que ce soit

### 1.0 Le code est-il bien sur le serveur ? (sur votre Mac)

*Quoi / pourquoi* : `deploy.sh` ne déploie que ce qui est **commité et poussé**. À la date de rédaction, des fichiers nécessaires (par exemple la route des contenus libres `FreeContentController.java` et l'outil `tools/free-content/build_free_archive.py`) **ne sont pas suivis par git** et 170+ fichiers sont modifiés mais non commités : sans commit et sans push, le serveur déployé n'aura pas ces routes (le contrôle de l'étape 6 restera KO).

```sh
cd <votre dépôt CastBridge>
git status --short | head          # doit être VIDE avant le déploiement
git log -1 --format='%h %s'        # la révision que vous voulez déployer
git branch --show-current          # integration/agents
```

Commit et push : **c'est votre décision** (ce guide ne les fait pas). Une fois fait, pousser vers le dépôt que le serveur lit. D'après `docs/HANDOFF.md` § 5, c'est le dépôt nu du serveur (remote `bridge`) et non GitHub :

```sh
git remote -v                                      # repérer le remote 'bridge' (ubuntu@bridge.sti-cm.com:castbridge/castbridge.git)
git push bridge integration/agents                 # envoie la branche au dépôt du serveur
git push origin integration/agents                 # et à GitHub (si vous le voulez)
```

*Vérifier* : `git ls-remote bridge integration/agents` rend la même révision que `git rev-parse HEAD`.
*Annuler* : un push de branche ne change rien au serveur tant que `deploy.sh` n'a pas été lancé ; `main` n'est jamais touché.

### 1.1 Se connecter et repérer l'état actuel

```sh
ssh ubuntu@bridge.sti-cm.com
BACKEND=~/castbridge/services/castbridge/backend     # à confirmer : voir « Points à confirmer »
cd "$BACKEND" && pwd && ls
git -C "$BACKEND" rev-parse HEAD                      # NOTEZ cette révision : c'est votre point de retour
git -C "$BACKEND" remote -v                           # d'où le serveur récupère le code (origin = dépôt nu ou GitHub ?)
git -C "$BACKEND" status --short                      # doit être vide, sinon deploy.sh refusera ("modifications locales non commitées")
docker compose --project-name castbridge ps           # castbridge-api et castbridge-db : « healthy »
docker image ls castbridge-api                        # notez l'image « current »
cat docker-compose.override.yml                       # réglages propres au serveur (réseau du nginx partagé) : à GARDER
```

### 1.2 Sauvegarde complète

*Quoi* : un dump de la base compressé et vérifié + une copie des APK. *Pourquoi* : les nouvelles versions appliquent de nouvelles migrations de base (V4, V30, V50 à V52, V60, V61…) qui **ne reviennent pas en arrière** ; seule la sauvegarde permet de retrouver l'état exact.

```sh
cd "$BACKEND" && ./backup.sh
```

*Vérifier* : une ligne `[backup …] base : /var/backups/castbridge/db/castbridge-AAAAMMJJ-HHMMSS.sql.gz (… )` (le dossier peut être `~/castbridge/backups` selon `CASTBRIDGE_BACKUP_DIR` dans `.env`). Notez le nom du fichier.

```sh
ls -lh "${CASTBRIDGE_BACKUP_DIR:-/var/backups/castbridge}/db" | tail -3
```

*Annuler* : rien à annuler (lecture seule sur la base).

### 1.3 Photo de `.env` et des secrets (restent sur le serveur, chiffrée si vous la sortez)

```sh
umask 077
mkdir -p ~/castbridge-snapshots && chmod 700 ~/castbridge-snapshots
cp -p "$BACKEND/.env" ~/castbridge-snapshots/env-$(date +%F-%H%M)
sudo tar -C "$BACKEND" -czf ~/castbridge-snapshots/secrets-$(date +%F-%H%M).tar.gz secrets
sudo chown "$USER" ~/castbridge-snapshots/*
ls -l ~/castbridge-snapshots
```

Pour une copie **hors du serveur** (recommandée pour les clés), chiffrez avant de télécharger :

```sh
gpg --symmetric --cipher-algo AES256 -o ~/castbridge-snapshots/secrets.tar.gz.gpg ~/castbridge-snapshots/secrets-*.tar.gz   # demande une phrase de passe
# puis, depuis votre Mac : scp ubuntu@bridge.sti-cm.com:castbridge-snapshots/secrets.tar.gz.gpg <coffre>
```

*Annuler* : `rm -r ~/castbridge-snapshots` quand tout est stable (ne jamais laisser traîner ces copies indéfiniment).

### 1.4 Le plan de retour arrière (à lire avant d'avancer)

| Niveau | Quand | Comment |
|---|---|---|
| 1. Automatique | La nouvelle version ne devient pas « healthy » | `deploy.sh` remet l'image `castbridge-api:previous` et la révision précédente, affiche les 80 dernières lignes de journal. |
| 2. Interrupteur | Les licences posent problème mais le serveur tourne | `CASTBRIDGE_LICENSES_ENABLED=false` (dans l'override), redéployer/redémarrer : pages et API des licences répondent 404, rien d'autre ne change. |
| 3. Version précédente à la main | Après coup | `docker tag castbridge-api:previous castbridge-api:current` puis `docker compose --project-name castbridge up -d --no-build castbridge-api` (voir § 8). Les migrations sont **additives** : l'ancien code les ignore (affirmé par `backend/README.md` et `docs/LICENSE-ADMIN.md` § 8.6). |
| 4. Restauration de la base | Dernier recours | `gunzip -c <dump> \| docker exec -i castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'` (§ 8). |

---

## 2. Clés et secrets (jamais dans Git)

Trois familles de clés, à ne pas confondre :

| Clé | Rôle | Où | Action ici |
|---|---|---|---|
| Clé de signature des mises à jour (`castbridge-signing.pem`) | signe les manifestes d'APK ; sa clé publique est dans les apps (`UpdateKeys`) | `secrets/castbridge-signing.pem`, `CASTBRIDGE_SIGNING_KEY_PATH` | **Existe déjà : ne pas régénérer** (`deploy.sh` s'arrête sans elle). |
| Clé de signature des **licences du serveur** (`license-signing.key`) | le serveur signe les activations qu'il émet (portée limitée) | `secrets/license-signing.key` | **À créer** (2.1). |
| Clé de chiffrement des TOTP (`license-totp.key`) et clé de l'audit (`license-audit.key`) | protègent la double authentification et chaînent le journal d'audit | `secrets/` | **À créer** (2.1). L'audit : **choisir maintenant, ne plus jamais changer.** |
| Jeton d'administration + compte web | accès API / interface web | `.env` ; la base pour le mot de passe web (BCrypt coût 12) | **Existent déjà.** Le TOTP s'active dans l'interface (4.5). |

### 2.1 Créer les trois fichiers de secrets des licences

*Quoi* : générer les clés **sur le serveur**, dans `secrets/` (dossier déjà ignoré par git). *Pourquoi* : sans elles le module licences ne démarre pas à moitié configuré (c'est voulu).

```sh
cd "$BACKEND"
ls -l secrets/                                              # vérifier ce qui existe : NE JAMAIS ÉCRASER un fichier existant
for f in license-signing.key license-totp.key license-audit.key; do [ -e "secrets/$f" ] && echo "EXISTE DÉJÀ : $f (ne pas toucher)"; done
umask 077
[ -e secrets/license-signing.key ] || openssl genpkey -algorithm ed25519 -out secrets/license-signing.key
[ -e secrets/license-totp.key ]    || openssl rand -base64 32 > secrets/license-totp.key
[ -e secrets/license-audit.key ]   || openssl rand -base64 32 > secrets/license-audit.key
sudo chown 10001:10001 secrets/license-*.key && sudo chmod 400 secrets/license-*.key
ls -l secrets/
```

Les droits `10001:10001` + `400` suivent la règle de la clé existante (`backend/README.md`, « Clés Ed25519 ») : le conteneur tourne sous l'uid 10001 et doit pouvoir lire le fichier (un simple `chmod 600` par `ubuntu` le rendrait illisible dans le conteneur : à confirmer, voir « Points à confirmer »). Pour relire un de ces fichiers en tant que vous : `sudo cat`.

*Vérifier* (après l'étape 4.4, le conteneur doit lire les fichiers) : `docker exec castbridge-api sh -c 'for f in license-signing.key license-totp.key license-audit.key; do test -r /run/secrets/$f && echo "$f lisible" || echo "$f ILLISIBLE"; done'`.
*Annuler* : supprimer les fichiers **avant** toute émission (`sudo rm secrets/license-*.key`). Après une émission, on ne supprime plus la clé (les activations déjà émises ne se vérifient qu'avec sa clé publique).
**Sauvegarde obligatoire hors du serveur** (étape 1.3, version chiffrée) : sans `license-signing.key`, plus d'émission serveur ; sans `license-totp.key`, les TOTP sont illisibles ; sans `license-audit.key`, l'audit ne se vérifie plus.

### 2.2 Mettre le module licences dans le fichier d'override du serveur

*Quoi* : `deploy.sh` n'utilise que `docker-compose.yml` **et** `docker-compose.override.yml` ; il **ne lit pas** `docker-compose.licenses.yml`. Pour que le module reste allumé après chaque déploiement, il faut recopier le contenu de la surcouche dans le fichier `docker-compose.override.yml` du serveur (fichier local, hors git).

*Commande* : éditez (`nano docker-compose.override.yml`) et **fusionnez** ces blocs avec ce qui existe déjà (ne dupliquez pas la clé `services:` ni `castbridge-api:` ; gardez le réseau du nginx partagé) :

```yaml
services:
  castbridge-api:
    environment:
      CASTBRIDGE_LICENSES_ENABLED: "true"            # mettre "false" pour la phase 4.3 (module éteint), puis "true"
      CASTBRIDGE_LICENSES_PUBLIC_ROUTES: "true"      # liste de révocation publique (signée) ; "false" = route en 404
      CASTBRIDGE_LICENSES_TRIAL_ISSUANCE: manual
      CASTBRIDGE_LICENSES_SECRETS_DIR: /run/secrets
      CASTBRIDGE_LICENSES_REQUIRE_TOTP: "true"
    secrets:
      - source: license_signing_key
        target: license-signing.key
      - source: license_totp_key
        target: license-totp.key
      - source: license_audit_key
        target: license-audit.key

secrets:
  license_signing_key:
    file: ./secrets/license-signing.key
  license_totp_key:
    file: ./secrets/license-totp.key
  license_audit_key:
    file: ./secrets/license-audit.key
```

*Vérifier* : la configuration finale est valide et contient bien les variables :

```sh
docker compose --project-name castbridge -f docker-compose.yml -f docker-compose.override.yml --env-file .env config -q && echo "configuration valide"
docker compose --project-name castbridge -f docker-compose.yml -f docker-compose.override.yml --env-file .env config | grep -E "LICENSES_|license-" | sed 's/^ *//'
```

*Annuler* : remettre la copie d'origine : `cp -p ~/castbridge-snapshots/…` (faites une copie de `docker-compose.override.yml` avant de le modifier : `cp -p docker-compose.override.yml ~/castbridge-snapshots/override-$(date +%F-%H%M)`).

### 2.3 La clé publique du serveur : à relever **après** le premier démarrage avec les licences allumées (étape 4.4)

Le serveur rend sa clé publique par `GET /api/v1/admin/licenses/signing` (jeton d'administration). La commande figure à l'étape 4.4. Elle donne `kid` (16 chiffres hexadécimaux) et `publicKey` (base64, 32 octets).

### 2.4 Ajouter cette clé publique aux clés de confiance de la TV (sur votre Mac) **avant** de construire la TV de l'étape 7

*Quoi* : une ligne par outil dans `~/.castbridge-signing/activation-trusted-keys.txt`, au format `kid=<kid> pub=<publicKey> scopes=<portées séparées par des virgules>` (lu à la **compilation** de la TV par `android/receiver/build.gradle.kts`). *Pourquoi* : la TV ne fait confiance qu'aux clés présentes dans son APK ; une clé inconnue ou une ligne sans `scopes=` n'accorde **aucune** portée (échec fermé, `TrustedKeyParser`).

Portées exactes de la clé du **serveur** : `ISSUE_TRIAL,ISSUE_PRODUCTION,REVOKE,REGISTRY,REACTIVATE,POLICY`. **Jamais** `SUPER_UNLIMITED`, ni `TRANSFER`, ni `COMMAND_OPEN_ALL` (le serveur ne les a pas : `docs/LICENSE-ADMIN.md` § 3.4).

```sh
# sur votre Mac ; remplacez les deux valeurs par celles affichées à l'étape 4.4 (ce sont des clés PUBLIQUES)
KID=<kid>; PUB=<publicKey>
F=~/.castbridge-signing/activation-trusted-keys.txt
cp -p "$F" "$F.avant-$(date +%F-%H%M)"
printf 'kid=%s pub=%s scopes=ISSUE_TRIAL,ISSUE_PRODUCTION,REVOKE,REGISTRY,REACTIVATE,POLICY\n' "$KID" "$PUB" >> "$F"
chmod 600 "$F"
awk '{print $1, $3}' "$F"      # affiche seulement « kid=… scopes=… » de chaque ligne (la clé du serveur apparaît, SANS SUPER_UNLIMITED)
```

La ligne exacte est aussi imprimée, prête à coller, par `ops/first-run/check-server.sh` quand `CB_ADMIN_TOKEN` est défini.

*Vérifier* : la ligne du serveur ne contient pas `SUPER_UNLIMITED` ; la clé du téléphone propriétaire (celle qui émet la clé de production de l'étape 7) est **déjà** dans le fichier d'après `docs/HANDOFF.md` (kid `35662fecbf07dbdf`) : `awk '{print $1}' "$F" | grep 35662fecbf07dbdf`.
*Annuler* : `cp -p "$F.avant-…" "$F"`.
*Attention* : ajouter une clé exige de **reconstruire et réinstaller la TV** (étape 7.1). Si la TV est déjà installée avec l'ancienne liste, elle refusera les clés du serveur.

---

## 3. Quels modules allumer pour la première expérience

| Module | Décision | Réglage (nom exact) | Remarque |
|---|---|---|---|
| Mises à jour, quiz, appareils, télémétrie, admin web | déjà en service | (rien) | La version déployée aujourd'hui est ancienne (`docs/HANDOFF.md` § 2 : `992db18`). |
| **Licences** | **ALLUMER** | `CASTBRIDGE_LICENSES_ENABLED=true` (défaut `false`) | Par l'override (2.2). Migrations V50 à V52 appliquées même module éteint. |
| Routes publiques des licences | allumer | `CASTBRIDGE_LICENSES_PUBLIC_ROUTES=true` (défaut `false`) | `GET /api/v1/revocations` (liste signée, publique) et `GET /api/v1/entitlements/me` (jeton d'appareil). La TV n'applique pas encore la liste (`docs/TRIAL-EDITION.md`, paragraphe sur l'interrupteur de déploiement : « Pas encore : liste de révocation côté TV »), donc **facultatif** ; à `true`, `check-server.sh` voit la route. À confirmer. |
| Émission d'essai | manuelle | `CASTBRIDGE_LICENSES_TRIAL_ISSUANCE=manual` | Seule valeur livrée. |
| Double authentification | exigée | `CASTBRIDGE_LICENSES_REQUIRE_TOTP=true` | Sans TOTP, le compte propriétaire lit mais ne modifie pas. |
| Clés d'outils hors ligne importées | non | `CASTBRIDGE_LICENSES_TRUSTED_KEYS` (vide) | Utile plus tard pour importer le registre du bureau/téléphone (`LICENSE-ADMIN.md` § 2.5). |
| **Assistance à distance (tunnel SSH)** | **ÉTEINT** | `CASTBRIDGE_TUNNEL_ENABLED` (défaut `false` ; non transmis par `docker-compose.yml`) | Ne pas l'allumer : il exige d'abord `ops/tunnel/setup.sh --apply`, `sshd` sur le port 2200, pare-feu, empreinte d'hôte (`ops/tunnel/README.md`). Éteint : `/api/v1/tunnel/**` répond 404. |
| Boutique, jetons, agents de terrain | **pas implémentés** | — | Aucune route serveur correspondante dans `backend/src/main/java/castbridge/server/` (paquets : admin, content, devices, library, licenses, lots, orders, quiz, telemetry, tunnel, updates, web). |
| **Contenus libres (CC BY-SA)** | **PUBLIER l'archive** | `CASTBRIDGE_FREE_CONTENT_FILE` (défaut `/data/apk/lots/castbridge-contenus-libres.zip`) | Étape 5.1. Route publique, sans activation. |
| Catalogue de bouquets signé | facultatif | `CASTBRIDGE_BUNDLES_CATALOG_FILE` (défaut `/data/apk/lots/bundles-catalog.json`) | Étape 5.2. |
| Publication de lots (`/api/v1/admin/lots`, `/api/v1/lots/**`) | **hors première expérience** | — | Ne rien publier. |

---

## 4. Déploiement

On déploie **en deux temps** (c'est la procédure de `docs/LICENSE-ADMIN.md` § 8.5 : sauvegarde, migration module éteint, vérification, puis interrupteur).

### 4.1 Prévoir la durée et la mémoire

Le conteneur API est limité à 512 Mo (`mem_limit: 512m`). Si, après le déploiement, `docker inspect castbridge-api -f '{{.State.OOMKilled}}'` répond `true`, passer `mem_limit` à `640m` dans l'override (`backend/README.md`, « Ressources mesurées »).

### 4.2 Premier temps : nouvelle version, module licences ÉTEINT

Dans l'override (2.2) mettez `CASTBRIDGE_LICENSES_ENABLED: "false"` (ou n'ajoutez pas encore le bloc). Puis, sur le serveur :

```sh
cd "$BACKEND"
./deploy.sh origin/integration/agents        # si vous n'êtes pas dans le groupe docker : DOCKER_CMD="sudo docker" ./deploy.sh origin/integration/agents
```

*Sortie attendue* (lignes `[deploy AAAA-MM-JJ HH:MM:SS] …`) :

```
révision : <ancienne 10 car.> -> <nouvelle 10 car.> (origin/integration/agents)
image en service conservée sous castbridge-api:previous
construction de l'image
sauvegarde de la base avant migration
démarrage
OK : castbridge-api en bonne santé (<nouvelle 10 car.>)
```

La construction Maven/Docker prend plusieurs minutes. L'attente de santé dure jusqu'à 240 s (`DEPLOY_HEALTH_TIMEOUT`).

*Vérifier* :

```sh
docker compose --project-name castbridge ps                       # castbridge-api « healthy »
git -C "$BACKEND" rev-parse --short HEAD                           # = révision affichée par deploy.sh
curl -s -o /dev/null -w '%{http_code}\n' https://bridge.sti-cm.com/api/v1/updates/public-key   # 200
docker compose --project-name castbridge logs --tail=50 castbridge-api       # pas d'erreur Flyway ni de stack trace
```

*Si ça échoue* : `deploy.sh` affiche « ÉCHEC : retour à la version précédente », les 80 dernières lignes de journal, puis remet `castbridge-api:previous` : le service précédent est rétabli (« version précédente rétablie »). Si le message dit « pas d'image précédente (premier déploiement) : service arrêté », voir § 8.
*Annuler* : § 8, niveau 3.

### 4.3 Test de fumée local (sur le serveur)

```sh
cd "$BACKEND" && ./smoke-test.sh          # défaut http://127.0.0.1:7090
```

Il lit `CASTBRIDGE_ADMIN_TOKEN` dans `.env`, ne publie aucun APK (sauf si `SMOKE_APK` est défini : **ne pas le définir ici**), **mais enregistre un appareil factice** (`model: smoke-test`) dans la base : c'est normal, supprimable ensuite depuis `/admin` (Appareils). Attendu : une ligne `OK` par contrôle puis `Tout est bon.`.

### 4.4 Second temps : allumer les licences et relever la clé publique

1. Dans `docker-compose.override.yml` : `CASTBRIDGE_LICENSES_ENABLED: "true"` (les secrets de l'étape 2.1 doivent exister).
2. Redéployer (relit l'override ; refait une sauvegarde de base) :

```sh
cd "$BACKEND" && ./deploy.sh origin/integration/agents
```

3. Vérifier que le module est allumé et que la clé est lue :

```sh
set -a; . ./.env; set +a
curl -s -H "Authorization: Bearer $CASTBRIDGE_ADMIN_TOKEN" https://bridge.sti-cm.com/api/v1/admin/licenses/signing
```

Réponse attendue (valeurs publiques, vous pouvez les noter) : `{"keyLoaded":true,"kid":"<16 hexa>","publicKey":"<base64>","scopes":[…6 portées…],"issuableKinds":[…],"format":…,"trialIssuance":"manual"}`. Si `keyLoaded` est `false` : fichier `license-signing.key` absent ou illisible (§ 8).
Notez `kid` et `publicKey` : ils servent à l'étape 2.4. **Si vous aviez déjà construit la TV, retournez à 2.4 puis reconstruisez-la (7.1) avant d'émettre quoi que ce soit avec cette clé.**

4. Éteint ou allumé, vérifier que l'assistance à distance est bien éteinte (variable absente ou `false`, 404 sur la route) :

```sh
docker exec castbridge-api env | grep -c '^CASTBRIDGE_TUNNEL_ENABLED=true' || echo "tunnel éteint (attendu)"
curl -s -o /dev/null -w '%{http_code}\n' https://bridge.sti-cm.com/api/v1/tunnel/experts      # 404 attendu
```

*Annuler* : remettre `CASTBRIDGE_LICENSES_ENABLED: "false"` et redéployer ; les pages `/admin/licenses` et `/api/v1/admin/licenses/**` répondent de nouveau 404, les données restent.

### 4.5 Activer votre double authentification (une seule fois)

Dans le navigateur : `https://bridge.sti-cm.com/admin` (compte web existant) puis *Licences > Sécurité > Activer*, scanner le QR avec l'application TOTP, saisir le code. Sans cela, votre compte propriétaire ne peut **rien modifier** dans les licences (lecture seule) ; **inutile pour le scénario de l'étape 7** (les clés sont émises par le téléphone, hors ligne), utile pour tester une émission par le serveur.

### 4.6 Journaux

```sh
docker compose --project-name castbridge logs -f castbridge-api      # temps réel (Ctrl-C pour sortir) ; format JSON (ECS), sans secrets ni IP
docker compose --project-name castbridge logs --since 30m castbridge-api
docker compose --project-name castbridge logs --tail=100 castbridge-db
docker stats --no-stream castbridge-api castbridge-db
```

---

## 5. Publier le contenu

### 5.1 Archive des contenus libres (CC BY-SA)

*Quoi / pourquoi* : le serveur relaie un fichier zip construit **hors ligne** par vous. Sans ce fichier : `GET /api/v1/free-content/info` répond 404 et le téléphone affiche « L'archive des contenus libres n'est pas encore publiée sur le serveur ».

**Sur votre Mac**, depuis le dépôt :

```sh
python3 tools/free-content/build_free_archive.py --dry-run                         # lire le bilan (inclus / exclus / refusés)
python3 tools/free-content/build_free_archive.py --out /tmp/cb-free --generated-at "$(date -u +%FT%TZ)" --build 1
python3 tools/free-content/build_free_archive.py --check /tmp/cb-free/castbridge-contenus-libres-*-v1.zip     # code 0 = intègre
```

Le fichier produit s'appelle `castbridge-contenus-libres-<aaaammjj>-v<N>.zip` (un essai du 2026-10-02 : 125 fichiers, environ 0,98 Mo). **Le serveur attend le nom `castbridge-contenus-libres.zip`** : renommez en le copiant. Avant de publier, relisez les points juridiques de `docs/FREE-CONTENT.md` § 4 (décisions qui vous reviennent : étiquettes de licence, contenu « original » libre ou réservé…) : la route est **publique**.

```sh
cp /tmp/cb-free/castbridge-contenus-libres-*-v1.zip /tmp/castbridge-contenus-libres.zip
chmod 644 /tmp/castbridge-contenus-libres.zip
scp /tmp/castbridge-contenus-libres.zip ubuntu@bridge.sti-cm.com:/tmp/
```

**Sur le serveur** : le chemin `/data/apk/lots/` est **dans le volume Docker `castbridge-apk`** (pas sur le disque du serveur) ; on y dépose le fichier avec `docker cp` (même méthode que la restauration des APK dans `backend/README.md`).

```sh
docker exec castbridge-api mkdir -p /data/apk/lots
docker cp /tmp/castbridge-contenus-libres.zip castbridge-api:/data/apk/lots/castbridge-contenus-libres.zip
docker exec castbridge-api ls -l /data/apk/lots/
rm /tmp/castbridge-contenus-libres.zip
```

Aucun redémarrage : le fichier est relu à chaque requête (cache sur chemin + date + taille).

*Vérifier* :

```sh
curl -s https://bridge.sti-cm.com/api/v1/free-content/info          # {"available":true,"sizeBytes":…,"sha256":…,"licence":"CC BY-SA",…}
curl -sI https://bridge.sti-cm.com/api/v1/free-content | head -5    # 200 + application/zip
```

*Annuler* : `docker exec castbridge-api rm /data/apk/lots/castbridge-contenus-libres.zip` (la route repasse en 404). Remplacer l'archive = recopier un nouveau fichier de même nom.

### 5.2 (Facultatif) Catalogue de bouquets signé

Utile seulement pour que les outils du propriétaire importent la liste des bouquets (`docs/CONTENT-PUBLISH.md` § 5 bis). La clé de signature du catalogue est **hors dépôt** (clé PEM du propriétaire) : ne la copiez jamais sur le serveur.

```sh
python3 tools/trial-edition/trial_edition.py select
python3 tools/trial-edition/trial_edition.py sign-catalog --manifest content/TRIAL-MANIFEST.json --key <chemin de votre clé PEM> --out /tmp/bundles-catalog.json
scp /tmp/bundles-catalog.json ubuntu@bridge.sti-cm.com:/tmp/
# sur le serveur :
docker exec castbridge-api mkdir -p /data/apk/lots
docker cp /tmp/bundles-catalog.json castbridge-api:/data/apk/lots/bundles-catalog.json && rm /tmp/bundles-catalog.json
curl -s -o /dev/null -w '%{http_code}\n' https://bridge.sti-cm.com/api/v1/catalog/bundles       # 200
```

*Annuler* : `docker exec castbridge-api rm /data/apk/lots/bundles-catalog.json` (404, « non publié »).

### 5.3 Ce qui n'est PAS publié

Aucun lot (`/api/v1/admin/lots` non utilisé), aucune release d'APK sur le canal `stable` (installez les APK à la main, étape 7.1 : une release publiée serait proposée à tous les appareils inscrits).

---

## 6. Contrôles (smoke tests)

### 6.1 Depuis votre Mac (ou n'importe où) : `ops/first-run/check-server.sh`

Lecture seule (uniquement des `GET`), français, `OK` / `KO` / `ignoré (pas de jeton)`, code de sortie ≠ 0 si un contrôle **obligatoire** échoue ; le jeton, s'il est fourni par `CB_ADMIN_TOKEN`, n'est jamais affiché ni passé en argument.

```sh
bash ops/first-run/check-server.sh                                  # sans jeton : les contrôles protégés sont « ignoré (pas de jeton) »
read -rs CB_ADMIN_TOKEN; export CB_ADMIN_TOKEN                      # puis, avec jeton :
bash ops/first-run/check-server.sh
bash ops/first-run/check-server.sh http://127.0.0.1:7090            # sur le serveur, directement sur l'API
```

| Contrôle | Obligatoire | Attendu après le déploiement |
|---|---|---|
| `/api/v1/updates/public-key` | oui | 200 (c'est la seule route de « santé » publique : voir « Points à confirmer ») |
| `/actuator/health` | oui | 404 (non exposé) |
| `/api/v1/admin/licenses/signing` sans jeton | oui | 401 (404 = version ancienne ou module éteint) |
| Même route avec jeton | oui (si jeton) | 200 et `keyLoaded:true` ; le script imprime la ligne de confiance de la TV |
| Journal d'audit | non | 200 |
| `/api/v1/revocations` | oui | 200 (exige `CASTBRIDGE_LICENSES_PUBLIC_ROUTES=true`) |
| `/api/v1/free-content/info` puis HEAD du téléchargement | oui | 200 |
| `/api/v1/catalog/bundles` | non | 200 si publié, sinon KO facultatif |
| `/api/v1/tunnel/experts` | information | 404 (assistance à distance éteinte) |

**État actuel de la production (2026-10-02, avant déploiement)** : `public-key` 200, `signing` 401, `revocations` 404, `free-content/info` 404, `catalog/bundles` 404, `tunnel/experts` 404 : le script rend donc `KO` pour `revocations` et `free-content/info` et se termine avec le code 1. **C'est attendu aujourd'hui.**

### 6.2 Sur le serveur : `backend/smoke-test.sh`

Voir 4.3. À relancer après chaque déploiement : `cd "$BACKEND" && ./smoke-test.sh` (ou `./smoke-test.sh https://bridge.sti-cm.com`).

### 6.3 Contrôle d'intégrité du journal d'audit des licences

```sh
curl -s -H "Authorization: Bearer $CB_ADMIN_TOKEN" https://bridge.sti-cm.com/api/v1/admin/licenses/audit/verify
```

Doit indiquer une chaîne intacte. Notez l'empreinte de tête **ailleurs** (ancrage extérieur, `LICENSE-ADMIN.md` § 3.3).

---

## 7. Le scénario de la première expérience (chez vous, avec votre TV et votre téléphone)

Pré-condition : étapes 1 à 6 faites et au vert. **Important** : dans ce scénario la clé de production est émise par **la console du téléphone** (hors ligne, avec la clé du téléphone propriétaire, déjà reconnue de la TV). **La clé du serveur n'est donc pas sollicitée** ; elle sert seulement à tester une émission par le serveur (7.8, facultatif). Les clés de la TV sont lues **à la compilation** : si vous avez ajouté une clé à l'étape 2.4, reconstruisez la TV.

### 7.1 Construire et installer (sur votre Mac)

```sh
export ANDROID_HOME=~/Library/Android/sdk
G=$(ls -d ~/.gradle/wrapper/dists/gradle-8.14.3-bin/*/gradle-8.14.3/bin/gradle)
cd <dépôt>/android
$G :receiver:assembleRelease -PrequireActivation=true                         # CastBridge-TV verrouillée (refuse de se construire sans clé de confiance)
$G :sender:assembleRelease -Pcastbridge.superAdmin=true                       # CastBridge téléphone AVEC l'entrée super-admin (haché lu dans ~/.castbridge-signing, jamais distribuer cet APK)
# APK : android/receiver/build/outputs/apk/release/receiver-armeabi-v7a-release.apk   et   android/sender/build/outputs/apk/release/sender-release.apk
```

Les APK « release » doivent être **signés** (`docs/RELEASES.md` § 6 ; la clé de debug du Mac est utilisée tant que la décision D12 n'est pas prise : une mise à jour ne s'installe que si elle porte la même signature que l'existant). Installation : TV par la clé USB (dossier `Download/`, puis installation avec la télécommande) ou par l'API de la TV ; téléphone par USB (`adb install -r`). **Ne pas publier ces APK sur le serveur.** Détail et pièges : `docs/RELEASES.md` § 5 à 8.

*Annuler* : réinstaller l'ancien APK (même signature) ; une TV mise à jour reçoit une grâce de 30 jours (`lock.graceDays`), une installation neuve est verrouillée aussitôt.

### 7.2 Installer et ouvrir CastBridge-TV (la TV est verrouillée)

*Écran attendu* : fond sombre, titre « CastBridge TV », ligne « Version 0.14.17-beta-verrouillee », « Bluetooth d'activation : … », le texte d'avis de verrouillage, les **conditions d'utilisation** avec une case à cocher, puis « Code d'appareil » en **très gros caractères** (`XXXX-XXXX-XXXX-XXXX`), la zone « Saisissez ou collez la clé d'activation », les boutons « Valider la clé », « Chercher la clé sur la clé USB », « Rendre la TV visible pour le téléphone (Bluetooth) », et « Télécharger tous les contenus libres ». Au premier lancement, la TV demande les permissions Bluetooth (acceptez). Pas de bouton « Retour » possible tant qu'elle est verrouillée.

Cochez la case des conditions d'utilisation **sur la TV** (obligatoire avant toute clé), puis appuyez sur « Rendre la TV visible pour le téléphone (Bluetooth) » et acceptez la fenêtre système (5 minutes).

### 7.3 Émettre la clé de production sur le téléphone et l'envoyer

1. Ouvrir CastBridge (la version avec super-admin). Entrée cachée : **7 touchers sur le logo, puis un appui long** ; saisir **votre code de déverrouillage** (jamais écrit dans ce guide ni dans le dépôt). Écran « Super administration ».
2. Appairer la TV (Bluetooth), onglet **Activer** : « Lire le code de la TV » (la demande d'appareil complète remplit le champ).
3. Section « Production » : durée **Illimitée** (puce par défaut). Laisser « SUPER_UNLIMITED » éteint. **Générer** : la console affiche « Licence lic-… (générée) ».
4. **Envoyer l'activation à la TV** (Bluetooth), ou, sans Bluetooth, copier le fichier `activation` dans `Download/CastBridge/` d'une clé USB branchée sur la TV (lue automatiquement toutes les 2 s) ou coller la clé dans le champ de la TV (la clé compacte ne porte pas de liste de droits et se tape très longuement : préférer l'activation complète par Bluetooth ou fichier).
5. Sur la TV, le message devient « Clé reçue du téléphone par Bluetooth. Appuyez sur « Valider la clé » pour activer. » : appuyez sur **« Valider la clé »**.

Une clé n'est installable que pendant **48 heures** après sa création.

### 7.4 Vérifier que la TV est en production

| À vérifier | Attendu |
|---|---|
| Message après validation | « Version complète : clé de production acceptée (…). Ouverture… » puis l'écran d'accueil. |
| Badge permanent (sur chaque écran) | **PRODUCTION**, avec « Clé illimitée ». |
| Tuile **Langues** | présente (Chinois, Japonais pilotes ; contenu de démarrage embarqué). |
| **Apprendre** | le contenu de base (mode classe : les 2 premières fiches de chaque pack, pour chaque classe et matière) s'affiche ; « Contenu de base : leçons complètes à recevoir du téléphone » (`docs/HANDOFF.md`, 2026-10-02). |
| Restrictions de l'essai | levées : bibliothèque, USB, téléchargements, autres jeux accessibles. |

### 7.5 « Télécharger tous les contenus libres »

- **Sur la TV** (écran d'activation, **sans clé**, hors ligne) : bouton « Télécharger tous les contenus libres ». La TV écrit elle-même l'archive zip dans `Download/CastBridge/` d'une clé USB (ou du stockage) : message de progression « Écriture de l'archive : N % » puis un message de succès avec l'emplacement ; échec possible sans clé USB (« Aucun dossier d'écriture disponible »). Test à faire **avant** la clé et, si vous voulez, après.
- **Sur le téléphone** : Réglages, section « Données hors ligne », « Contenus libres (CC BY-SA) » : l'écran vérifie le serveur (« Vérification auprès du serveur… ») puis affiche « Archive disponible : … Mo, créée le …, licence CC BY-SA » ; bouton **« Télécharger tous les contenus libres »** (reprise possible). Sans serveur : « Exporter les contenus libres inclus dans l'application ». Enregistré dans Téléchargements/CastBridge.
- Si le téléphone dit « n'est pas encore publiée sur le serveur » : étape 5.1 non faite.

### 7.6 Une clé d'essai sur un second appareil

Sur un **second appareil** (autre TV ou émulateur Android TV avec le même APK verrouillé) : relever son code d'appareil, puis dans la console : onglet Activer, section **Essai** (durée vide = 30 jours, jamais « illimitée »), générer, envoyer (fichier ou clé saisie, car l'émulateur n'a pas de Bluetooth). Attendu : badge **ESSAI**, « Clé valable jusqu'au JJ/MM/AAAA (30 j) », « Lots locatifs : … d'essai restantes » ; seuls le streaming, le Sudoku et « Apprendre » (lots de la fenêtre d'essai) s'ouvrent ; bibliothèque, réception de fichiers, USB, téléchargements, autres jeux, quiz et échecs répondent par un refus avec un message ; l'écran de mise à niveau « Version complète » propose de saisir une clé de production.

### 7.7 Ce qui n'existe PAS encore (ne pas le chercher)

Boutique ; jetons ; ventes par agents de terrain ; rapports du contrôle parental vers le téléphone ; assistance à distance (tunnel SSH : éteint côté serveur) ; mode minimal du téléphone ; liste de révocation appliquée par la TV (`docs/TRIAL-EDITION.md`, paragraphe sur l'interrupteur de déploiement : « Pas encore : liste de révocation côté TV ») ; locations de lots depuis le serveur (conception cible, `docs/RENTAL-LOTS.md` § 15) ; publication de lots. Le canal Bluetooth propriétaire est « implémenté, non testé sur de vrais appareils » (`docs/OWNER-CONSOLE.md`) : les résultats de cette première expérience sont justement ce test.

### 7.8 (Facultatif) Émission par le serveur avec la clé du serveur

Une fois la ligne de l'étape 2.4 compilée dans la TV : dans `https://bridge.sti-cm.com/admin/licenses` (TOTP activé, étape 4.5), *Émettre* avec la demande d'appareil complète (fichier `device-request.txt` dans `Download/CastBridge/` de la TV, ou lecture Bluetooth), licence laissée vide (« auto »), durée illimitée : le serveur affiche l'activation `cbx1.…` **une seule fois** ; la déposer comme fichier `activation` dans `Download/CastBridge/` de la clé USB. Si la TV répond « clé inconnue », la ligne de l'étape 2.4 n'est pas dans cette TV (troubleshooting ci-dessous).

### 7.9 Dépannage (symptôme, cause, remède)

| Symptôme | Cause probable | Remède |
|---|---|---|
| La TV répond « Clé refusée … » / clé de signature inconnue | La clé publique de l'émetteur n'est pas dans la TV (liste lue **à la compilation**), ou sa ligne n'a pas `scopes=` (aucune portée), ou une ligne a été modifiée | Étape 2.4 : coller la ligne **entière**, reconstruire et réinstaller la TV. Contrôler avec `awk '{print $1, $3}' ~/.castbridge-signing/activation-trusted-keys.txt`. |
| « Clé refusée … Elle est valide mais pas pour cette TV » | Code d'appareil de **une autre TV**, ou module Wi-Fi remplacé | Relire le code sur la TV (`Lire le code de la TV`) et regénérer. |
| La TV demande d'accepter les conditions avant de prendre la clé | Case des conditions d'utilisation non cochée **sur la TV** (`TunnelTerms.MUST_ACCEPT`) | Cocher la case sur la TV, puis « Valider la clé ». |
| Bouton Bluetooth sans effet, téléphone ne trouve pas la TV, « permission refusée » | Permissions Bluetooth non accordées (Android 12 et plus : `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`) ; ou la copie « Dual App » Samsung du téléphone (permission refusée) ; Bluetooth de la TV éteint | Accorder les permissions (sur la TV : relancer l'écran d'activation) ; utiliser l'utilisateur principal du téléphone ; allumer le Bluetooth de la TV ; « Rendre la TV visible » (5 min). Repli : fichier `activation` sur clé USB. |
| « Clé périmée : à refaire (une clé est valable 48 h) » | Clé créée il y a plus de 48 h, ou **horloge de la TV fausse** (date remise à 2000, pas de réseau) | Régler la date/l'heure de la TV, regénérer une clé et l'installer dans les 48 h. |
| Clé refusée alors qu'elle est récente et que la date de la TV a reculé | `TvClock` détecte un retour en arrière de l'horloge (texte exact du message non vérifié) | Remettre l'heure exacte (réseau), puis regénérer la clé. |
| Trois refus et la liaison Bluetooth se coupe | Protection : trois refus sur une liaison la coupent | Reconnecter, vérifier le code, renvoyer. |
| La TV est « ACTIVATION TERMINÉE » ou demande un nouveau code | Durée de la clé (droit `usage`) écoulée ; l'essai dure 30 jours | Émettre une nouvelle clé (production : durée illimitée). |
| « essai déjà utilisé sur cette TV » | La fenêtre unique de lots d'essai n'est accordée qu'une fois | Normal ; passer en production. |
| Accès à l'interface web/API de la TV : `401` | PIN absent ou faux (`X-CB-Pin`) ; **5 échecs depuis une même IP = blocage de 60 s** | Lire le PIN sur la TV (Connexion & réglages), attendre 60 s. |
| `403 {"error":"Hôte non autorisé"}` en appelant la TV | Contrôle anti DNS-rebinding : la TV n'accepte que l'adresse IP privée (ou `localhost`) comme en-tête `Host`, **pas un nom** | Utiliser l'**adresse IP** de la TV (`http://192.168.x.y:8765/…`), jamais son nom d'hôte. |
| Le téléphone : « Serveur injoignable ou réponse invalide » | Pas d'Internet, DNS, ou serveur arrêté | Tester `bash ops/first-run/check-server.sh` ; contrôler les journaux (4.6). |
| Le téléphone : « n'est pas encore publiée sur le serveur » | Archive libre absente (404 sur `/api/v1/free-content/info`) | Étape 5.1. |
| `signing` répond 404 avec le jeton | Module éteint ou version ancienne | 4.4 (variable à `true`, redéployer) ; contrôler la révision déployée. |
| `signing` : `keyLoaded:false` | `license-signing.key` absent, illisible par l'uid 10001 ou mal formé | `docker exec castbridge-api ls -l /run/secrets` ; refaire `chown 10001:10001` + `chmod 400` (2.1) ; redéployer. |
| Création de licence refusée : « Activez d'abord la double authentification » | TOTP non activé (propriétaire sans TOTP = lecture seule) | 4.5. |
| `deploy.sh` : « modifications locales non commitées dans le dépôt : abandon » | Fichier modifié à la main dans le dépôt du serveur | `git -C "$BACKEND" status` ; rétablir (`git checkout -- <fichier>`) ou déplacer le réglage dans `.env` / l'override (non suivis). |
| `deploy.sh` : « clé de signature introuvable » | `CASTBRIDGE_SIGNING_KEY_PATH` ou `secrets/castbridge-signing.pem` absent | Restaurer la clé depuis la sauvegarde 1.3 (ne **jamais** en générer une autre : les apps ne reconnaîtraient plus les mises à jour). |
| `deploy.sh` : « un autre déploiement est en cours » | Verrou `/tmp/castbridge-deploy.lock` tenu | Attendre ; vérifier `ps aux \| grep deploy.sh`. |

---

## 8. Retour arrière et incidents

### 8.1 Revenir à la version précédente à la main

```sh
cd "$BACKEND"
git checkout --quiet --detach <révision notée à l'étape 1.1>
docker tag castbridge-api:previous castbridge-api:current
docker compose --project-name castbridge -f docker-compose.yml -f docker-compose.override.yml --env-file .env up -d --no-build castbridge-api
docker compose --project-name castbridge ps
```

(`castbridge-api:previous` est l'image qui tournait avant **le dernier** `deploy.sh` : si vous avez déployé deux fois, elle est celle du premier déploiement ; la révision notée en 1.1 reste la référence.)

### 8.2 Éteindre seulement les licences

`CASTBRIDGE_LICENSES_ENABLED: "false"` dans l'override, puis `./deploy.sh origin/integration/agents` ; vérifier que `GET /api/v1/admin/licenses/signing` rend 404 et que `GET /api/v1/updates/public-key` rend 200.

### 8.3 Restaurer la base (dernier recours, perd ce qui a été écrit depuis la sauvegarde)

```sh
cd "$BACKEND"
docker compose --project-name castbridge stop castbridge-api
gunzip -c <dossier de sauvegarde>/db/castbridge-AAAAMMJJ-HHMMSS.sql.gz \
  | docker exec -i castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'
docker compose --project-name castbridge up -d castbridge-api
```

Puis redéposer les secrets de `~/castbridge-snapshots` si besoin et lancer `bash ops/first-run/check-server.sh`. Pour les licences seules : `castbridge-licenses-….sql.gz`, puis *Audit > Vérifier l'intégrité* (`docs/LICENSE-ADMIN.md` § 8). Les migrations Flyway déjà appliquées (V50 à V52, V60, V61…) restent inscrites dans `flyway_schema_history` : une base restaurée à l'état d'avant réapplique ses migrations au prochain démarrage de la **nouvelle** version ; avec l'**ancienne** version, il n'y a rien à faire.

### 8.4 Où sont les journaux

| Quoi | Où |
|---|---|
| API (JSON, sans secret ni IP) | `docker compose --project-name castbridge logs castbridge-api` |
| Base | `docker compose --project-name castbridge logs castbridge-db` |
| Sauvegardes | `/var/backups/castbridge/` ou `~/castbridge/backups` (voir `CASTBRIDGE_BACKUP_DIR`), journal cron `/var/log/castbridge-backup.log` |
| Déploiements | la sortie de `deploy.sh` (aucun fichier : copiez-la si besoin : `./deploy.sh … 2>&1 \| tee ~/deploy-$(date +%F-%H%M).log`) |
| Audit des licences | `/admin/licenses/audit` et `GET /api/v1/admin/licenses/audit/verify` |
| TV | écran « Connexion & réglages » (PIN), `GET /api/screenshot` de la TV (avec PIN) |
| Santé interne | `docker inspect castbridge-api -f '{{.State.Health.Status}}'` ; contrôles sur le port 8081 **non publié** (`/actuator/health/readiness`) uniquement depuis le conteneur |

### 8.5 En cas d'urgence (cinq commandes)

```sh
docker compose --project-name castbridge ps
docker compose --project-name castbridge logs --tail=200 castbridge-api
cd "$BACKEND" && ./backup.sh --db-only
cd "$BACKEND" && ./deploy.sh <révision de retour>
bash ops/first-run/check-server.sh
```

Le serveur est **partagé avec d'autres projets de production** (`docs/HANDOFF.md` § 4) : ne touchez qu'à `~/castbridge/` et au fichier nginx de CastBridge ; ne redémarrez ni Docker en entier, ni nginx, sans savoir ce que cela coupe ailleurs.

### 8.6 Qui contacter

- Propriétaire / décisions : Esaie Tchetagni (coordonnées : **à renseigner**).
- Administration du serveur (VPS, nginx partagé) : **à renseigner** (nom, téléphone, courriel).
- Hébergeur / support du VPS : **à renseigner**.
- Juridique (publication des contenus libres, conditions d'utilisation) : **à renseigner**.

---

## Points à confirmer

Choses que ce guide ne peut pas affirmer parce que le code ou les documents sont absents, contradictoires ou non vérifiables d'ici (aucune connexion au serveur n'a été faite, hors `GET` publics).

1. **Le code n'est pas encore sur `origin/integration/agents`.** `origin/integration/agents` est à la révision `faf8636` (= `HEAD` local), mais 177 fichiers sont modifiés et **non commités** ; `backend/.../lots/FreeContentController.java` et `tools/free-content/build_free_archive.py` sont **non suivis** par git. Déployer `origin/integration/agents` tel quel ne donnera donc **pas** `/api/v1/free-content` (404). Il faut commiter et pousser (décision du propriétaire ; je n'ai rien commité).
2. **Quel dépôt le serveur interroge-t-il ?** `deploy.sh` fait `git fetch origin` **sur le serveur**. `docs/HANDOFF.md` § 5 dit que le déploiement passe par `git push bridge …` (dépôt nu `ubuntu@bridge.sti-cm.com:castbridge/castbridge.git`), pas par GitHub. Vérifier `git -C "$BACKEND" remote -v` ; si `origin` du serveur est le dépôt nu, la branche doit y être poussée (`git push bridge integration/agents`, étape 1.0). Le remote `bridge` local ne montre aujourd'hui que `bridge/main`.
3. **Chemin du dossier backend sur le serveur et groupe docker.** Le guide de la mission dit `docker` sans `sudo` ; `docs/HANDOFF.md` § 5 déploie avec `DOCKER_CMD="sudo docker"` et dans `~/castbridge/services/castbridge/backend` (alors que `backend/README.md` parle de `/opt/castbridge/backend`). À vérifier à l'étape 1.1.
4. **`deploy.sh` ne lit pas `docker-compose.licenses.yml`** (seulement `docker-compose.yml` + `docker-compose.override.yml`). J'ai donc proposé de fusionner la surcouche dans l'override du serveur (étape 2.2) ; le contenu exact de l'override actuel du serveur (réseau `infra-net`) est inconnu : la fusion est à faire à la main et à contrôler par `docker compose … config`. Une amélioration future de `deploy.sh` serait plus sûre (non faite : hors périmètre).
5. **`docker-compose.licenses.yml` passe une variable périmée** (`CASTBRIDGE_LICENSES_WINDOW_DAYS`, 30) alors que le code lit `CASTBRIDGE_LICENSES_WINDOW_HOURS` (48 h, `application.yml`) ; `docs/LICENSE-ADMIN.md` parle encore de 30 jours. Je n'ai pas recopié cette variable dans l'override. Les clés sont de toute façon installables 48 h (`docs/OWNER-CONSOLE.md`).
6. **Droits des fichiers de secrets.** `docs/LICENSE-ADMIN.md` dit `chmod 600` ; or le conteneur tourne sous l'uid 10001 et les secrets Docker « fichier » gardent les droits de l'hôte : j'ai repris la règle de la clé existante (`chown 10001:10001`, `chmod 400`). Non testé sur le serveur ; vérification proposée (2.1).
7. **Pas de route de santé publique dédiée.** `backend/README.md` cite `GET /api/v1/admin/health`, qui **n'existe pas** dans le code (aucune occurrence dans `backend/src/main`). Le contrôle utilise `GET /api/v1/updates/public-key` (comme `README` § Supervision) ; la vraie santé est `/actuator/health/readiness` sur le port 8081, non publié.
8. **`CASTBRIDGE_LICENSES_PUBLIC_ROUTES=true` : recommandé, pas démontré nécessaire.** La route de révocation est publique (liste signée) mais la TV ne l'applique pas encore ; je l'ai mise à `true` pour que le contrôle soit vert et par cohérence avec `docs/LICENSE-ADMIN.md` § 9. À confirmer.
9. **Archive des contenus libres.** `docs/FREE-CONTENT.md` annonce « 0 pack étiqueté, archive vide » (état du 2026-10-02 matin), mais l'outil construit aujourd'hui un zip de 125 fichiers (environ 0,98 Mo) : le document est en retard sur `tools/free-content/license-tags.json`/les étiquettes modifiés localement. Les **points juridiques du § 4** (licence « original » libre ou réservé, texte légal, audio de synthèse GPL, sources CC BY-SA 3.0) ne sont **pas tranchés** : à décider avant de rendre l'archive publique. Le nom du fichier produit (`castbridge-contenus-libres-<date>-v<N>.zip`) diffère de celui que le serveur attend (`castbridge-contenus-libres.zip`) : renommage dans le guide.
10. **`docker cp` vers le volume d'un conteneur en lecture seule.** `backend/README.md` utilise ce même mécanisme pour restaurer les APK ; `docs/API-SERVER.md` dit simplement `scp … server:/data/apk/lots/` (qui ne s'applique pas tel quel : `/data/apk` est dans un volume Docker). Non testé ici. Repli si `docker cp` échoue : `sudo cp` vers `$(docker volume inspect castbridge-apk -f '{{.Mountpoint}}')/lots/`, puis `sudo chown -R 10001:10001` sur ce dossier.
11. **La clé du serveur n'intervient pas dans le scénario principal.** La clé de production est émise hors ligne par le téléphone (clé déjà de confiance : kid `35662fecbf07dbdf` d'après `docs/HANDOFF.md` ; **non vérifié dans le fichier**, que je n'ai pas lu par consigne). L'étape 2.4 reste indispensable avant d'utiliser une activation émise par le serveur (7.8).
12. **Non vérifié sur de vrais appareils** : canal Bluetooth propriétaire (`docs/OWNER-CONSOLE.md` : « non encore testé sur de vrais appareils »), contenu de base de la TV (`docs/HANDOFF.md` : « Pas d'APK construit ni d'essai sur appareil »), installation d'une activation émise par le serveur sur une vraie TV (`docs/LICENSE-ADMIN.md` § 12), MySQL 8.4 avec les migrations V50 et suivantes sur la **vraie** base de production (les migrations « n'ont jamais été déployées »), mémoire 512 Mo sous charge.
13. **Pas de préproduction.** `docs/LICENSE-ADMIN.md` § 8.2 recommande un essai sur le port 7091 avec une base de test ; sur un VPS partagé j'ai remplacé cela par un déploiement en deux temps (module éteint puis allumé) avec sauvegarde, ce qui est plus léger mais **moins sûr**. À vous de décider si une préproduction est nécessaire. Avant de fusionner, `docs/LICENSE-ADMIN.md` demande aussi de vérifier qu'aucune autre branche n'a pris les numéros de migration V50 à V52 (V60 et V61 existent ici).
14. **Phrase exacte de l'écran de TV de production et libellés** décrits d'après le code (`ActivationActivity.kt`, `docs/OWNER-CONSOLE.md`) : les libellés peuvent différer légèrement sur l'appareil. La tuile « Langues » et le « contenu de base » sont décrits d'après `docs/HANDOFF.md`, non d'après un essai.
15. **Contacts du § 8.6** : à renseigner (aucun dans le dépôt).
