# Serveur CastBridge (`backend/`)

Serveur Java Spring Boot + MySQL, en Docker, pour les besoins actuels du projet :

1. **Mises à jour automatiques** de l'app TV et de l'app téléphone (pas de Play Store sur les TV) : dépôt d'APK,
   choix de l'APK selon les ABI de l'appareil, canaux `stable`/`beta`, déploiement progressif, retrait, version
   minimale supportée, **manifeste signé Ed25519**, téléchargement reprenable (Range) ;
2. **Banque de questions du quiz** : même format d'échange que la TV (branche `feat/tv-quiz`), validation stricte,
   import/export JSON/CSV, synchronisation incrémentale (ETag, `since`, suppressions), tirages côté serveur ;
3. **Suivi des appareils** : chaque TV/téléphone s'enregistre (jeton d'appareil), envoie un heartbeat toutes les
   15 min, signale ses plantages ; **interface d'administration web** (`/admin`) pour tout suivre et piloter ;
4. **Télémétrie d'usage et KPI** (avec consentement) : lots d'événements, agrégats, page d'accueil « Fonctionnalités
   les plus utilisées », indicateurs de parc, d'usage, d'envois, de lecture, de quiz… (voir
   [`docs/TELEMETRY.md`](../docs/TELEMETRY.md), qui contient aussi une proposition de texte d'information).

Projet Maven indépendant du build Gradle Android. Les routes et les formats sont décrits dans
[`docs/API-SERVER.md`](../docs/API-SERVER.md).

## Architecture

```
 Appareils (TV, téléphones)          Administrateur (navigateur)        CI GitHub (release.yml)
        │ HTTPS                               │ HTTPS                          │ HTTPS + jeton admin
        ▼                                     ▼                                ▼
 ┌──────────────── nginx partagé du VPS (TLS, sous-domaine ou /castbridge/) ─────────────────┐
 └────────────────────────────────────────┬──────────────────────────────────────────────────┘
                                          │ http://127.0.0.1:7090  (seule adresse publiée)
                             ┌────────────▼─────────────┐   réseau castbridge-edge
                             │ castbridge-api (Java 21) │   512 Mo, fs en lecture seule, uid 10001
                             │  :8080 API + /admin      │   :8081 actuator (non publié, healthcheck)
                             └──────┬─────────────┬─────┘
                  volume castbridge-apk           │ réseau castbridge-internal (internal: pas d'Internet)
                  (/data/apk : APK)        ┌──────▼──────────────┐
                                           │ castbridge-db       │ MySQL 8.4, 512 Mo, buffer pool 128M,
                                           │ (aucun port publié) │ performance_schema OFF
                                           └─────────────────────┘ volume castbridge-mysql
```

Code (`src/main/java/castbridge/server/`) :

| Paquet | Rôle |
|---|---|
| `updates` | releases (entité, dépôt, lecture du manifeste binaire de l'APK), politique de version minimale, signature Ed25519 des manifestes, téléchargements `/dl` |
| `quiz` | questions, validation, import/export JSON/CSV, synchro avec tombstones, tirages, seed initial |
| `devices` | enregistrement, heartbeats, plantages, historique (installations, versions, jours), rétention, géolocalisation approximative |
| `telemetry` | ingestion des événements d'usage (catalogue fermé, liste blanche, consentement, dédoublonnage), agrégats KPI, calculs des indicateurs, rétention |
| `admin` | interface web Thymeleaf (`/admin`, `/admin/kpi`), comptes BCrypt avec verrouillage |
| `config`, `web` | sécurité (deux chaînes : API par jeton, web par session + CSRF), limitation de débit, journaux d'accès, erreurs en français |

Schéma : Flyway (`src/main/resources/db/migration`), dates stockées en UTC, affichées en `Africa/Douala`.

## Variables d'environnement

Toutes dans `.env` (modèle : [`.env.example`](.env.example), jamais commité, `chmod 600`).

| Variable | Obligatoire | Rôle |
|---|---|---|
| `CASTBRIDGE_DB_PASSWORD`, `CASTBRIDGE_DB_ROOT_PASSWORD` | oui | mots de passe MySQL (utilisateur `castbridge`, root) |
| `CASTBRIDGE_ADMIN_TOKEN` | oui | jeton Bearer de l'API d'administration (≥ 32 caractères, sinon l'API admin est désactivée) ; utilisé par curl et la CI |
| `CASTBRIDGE_WEB_ADMIN_USER`, `CASTBRIDGE_WEB_ADMIN_PASSWORD` | oui (1er démarrage) | compte initial de l'interface web (mot de passe ≥ 12 caractères, stocké en BCrypt) |
| `CASTBRIDGE_WEB_ADMIN_RESET_PASSWORD` | non | `true` = remplace le mot de passe stocké par celui de `.env` au prochain démarrage (oubli) ; remettre `false` |
| `CASTBRIDGE_SIGNING_KEY_PATH` | oui | fichier PEM de la clé privée Ed25519 (monté en secret Docker) ; défaut `./secrets/castbridge-signing.pem` |
| `CASTBRIDGE_PUBLIC_BASE_URL` | recommandé | adresse publique, pour les liens de téléchargement des manifestes (`https://castbridge.exemple.org` ou `https://exemple.org/castbridge`) |
| `CASTBRIDGE_PORT` | non | port local publié sur 127.0.0.1 (défaut 7090) |
| `CASTBRIDGE_COOKIE_SECURE` | non | cookie de session `Secure` (défaut `true` ; `http://localhost` fonctionne aussi dans les navigateurs) |
| `CASTBRIDGE_GEO_COUNTRY_HEADER` | non | en-tête portant le pays ISO posé par le proxy/CDN (ex. `CF-IPCountry`) |
| `CASTBRIDGE_GEOIP_DIR`, `CASTBRIDGE_GEOIP_DB` | non | base locale MaxMind GeoLite2 (`.mmdb`, pays + ville) : dossier monté sur `/geoip`, chemin `/geoip/GeoLite2-City.mmdb` |
| `CASTBRIDGE_RATE_LIMIT_PER_MINUTE`, `CASTBRIDGE_RATE_LIMIT_BURST` | non | limite par IP des routes publiques (défaut 120/min, rafale 60) |
| `CASTBRIDGE_QUIZ_SEED` | non | importe la banque embarquée de la TV si la base est vide (défaut `true`) |
| `CASTBRIDGE_BACKUP_DIR`, `CASTBRIDGE_BACKUP_RETENTION_DAYS` | non | pour `backup.sh` (défaut `/var/backups/castbridge`, 14 jours) |
| `DEPLOY_REF` | non | révision déployée par défaut par `deploy.sh` (défaut `origin/main`) |

Autres réglages (valeurs par défaut sensées) : `CASTBRIDGE_HEARTBEAT_RETENTION_DAYS` (30), `CASTBRIDGE_IP_RETENTION_DAYS`
(30), `CASTBRIDGE_CRASH_RETENTION_DAYS` (180), `CASTBRIDGE_DEVICE_RETENTION_DAYS` (365),
`CASTBRIDGE_QUIZ_TOMBSTONE_DAYS` (365), `CASTBRIDGE_PACKAGE_TV` / `_PHONE` (`castbridge.receiver` / `castbridge.sender`).

## Clés Ed25519 (signature des mises à jour)

La clé **privée** reste sur le serveur ; la clé **publique** est embarquée dans les apps, qui refusent tout manifeste
qu'elle ne vérifie pas (un serveur compromis ou un intermédiaire ne peut donc pas faire installer un autre APK
sans la clé privée ; l'APK doit en plus porter la signature Android habituelle de l'app pour être installé par-dessus).

```sh
cd /opt/castbridge/backend
mkdir -p secrets && chmod 700 secrets
openssl genpkey -algorithm ed25519 -out secrets/castbridge-signing.pem
sudo chown 10001:10001 secrets/castbridge-signing.pem && sudo chmod 400 secrets/castbridge-signing.pem   # lisible par le conteneur (uid 10001) seulement
# clé publique brute (32 octets) en base64, à embarquer dans les apps :
sudo openssl pkey -in secrets/castbridge-signing.pem -pubout -outform DER | tail -c 32 | base64
```

Une fois le serveur démarré, la même valeur est donnée par `curl https://<serveur>/api/v1/updates/public-key`
(champ `publicKey`). Elle se colle dans `android/core/src/main/kotlin/castbridge/core/update/UpdateKeys.kt`
(`PUBLIC_KEY`), puis les apps sont reconstruites. **Sauvegardez la clé privée hors du serveur** (coffre de mots de
passe) : la perdre oblige à republier les apps à la main avec une nouvelle clé publique.

Changement de clé : publier d'abord une version des apps signée avec l'ancienne clé qui embarque les deux clés
(`UpdateKeys.PUBLIC_KEYS`), puis changer la clé du serveur.

Formats acceptés par le serveur : PEM (`-----BEGIN PRIVATE KEY-----`), DER PKCS#8, ou la graine brute de 32 octets en
base64 (variable `CASTBRIDGE_SIGNING_KEY`, alternative au fichier).

## Déploiement pas à pas (VPS Ubuntu 24.04, Docker 29, Compose v5)

Première installation (utilisateur membre du groupe `docker`) :

```sh
sudo mkdir -p /opt/castbridge && sudo chown "$USER" /opt/castbridge
git clone https://github.com/<compte>/CastBridge.git /opt/castbridge
cd /opt/castbridge/backend
cp .env.example .env && chmod 600 .env
# remplir .env : mots de passe (openssl rand -base64 36 | tr -d '/+=' | cut -c1-40), jeton admin, compte web, URL publique
# générer la clé Ed25519 (section ci-dessus)
./deploy.sh origin/feat/backend        # ou origin/main une fois la branche fusionnée
./smoke-test.sh                          # vérifications de bout en bout sur http://127.0.0.1:7090
```

`deploy.sh` (idempotent) : verrou, `git fetch` + `checkout` détaché de la révision, image en service gardée sous
`castbridge-api:previous`, `docker compose build`, sauvegarde de la base, `up -d`, attente du healthcheck
(readiness = application + base). Si la nouvelle version ne devient pas saine : l'image et la révision précédentes
sont remises en service automatiquement (message et 80 dernières lignes de journal affichés).

Mises à jour suivantes : `./deploy.sh <révision>`. Retour manuel à la version précédente :

```sh
docker tag castbridge-api:previous castbridge-api:current
docker compose --project-name castbridge up -d --no-build castbridge-api
```

Les migrations Flyway ne reviennent pas en arrière : elles doivent rester compatibles avec la version précédente du
code (ajouter des colonnes/tables, ne supprimer qu'à la version d'après). La sauvegarde faite par `deploy.sh` avant
chaque déploiement permet de revenir à l'état exact de la base.

Commandes utiles :

```sh
docker compose --project-name castbridge ps
docker compose --project-name castbridge logs -f castbridge-api     # journaux JSON (ECS), sans secrets ni IP
docker stats castbridge-api castbridge-db
```

Ressources mesurées (image construite localement) : image `castbridge-api` 414 Mo sur disque ; au repos après le
démarrage (≈ 5 s) : API ≈ 380 Mo de mémoire du conteneur (tas limité à 70 % de 512 Mo, Serial GC), MySQL ≈ 250 à
330 Mo. Si le conteneur API est tué pour mémoire (`OOMKilled` dans `docker inspect`), passer `mem_limit` à `640m`.

## Exposition HTTPS par le nginx partagé (à décider, non appliqué)

Le conteneur n'écoute que sur `127.0.0.1:7090`. Deux possibilités ; dans les deux cas nginx doit **écraser**
`X-Forwarded-For` avec `$remote_addr` (le serveur s'en sert pour la limite par IP et la géolocalisation).

Sous-domaine (le plus simple : cookies et liens sans préfixe) :

```nginx
server {
    listen 443 ssl;
    http2 on;
    server_name castbridge.exemple.org;
    # ssl_certificate / ssl_certificate_key : comme les autres sites du serveur (certbot)

    client_max_body_size 200m;          # dépôt d'APK
    proxy_request_buffering off;        # l'APK est envoyé au fil de l'eau au serveur
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;

    location / {
        proxy_pass http://127.0.0.1:7090;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;
    }
}
```

Chemin sous un domaine existant (`https://exemple.org/castbridge/`) :

```nginx
location /castbridge/ {
    client_max_body_size 200m;
    proxy_request_buffering off;
    proxy_read_timeout 300s;
    proxy_pass http://127.0.0.1:7090/;              # le "/" final retire le préfixe
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Prefix /castbridge; # liens et redirections de /admin corrects
}
```

Puis `CASTBRIDGE_PUBLIC_BASE_URL=https://castbridge.exemple.org` (ou `https://exemple.org/castbridge`) dans `.env`,
`./deploy.sh`, et dans les apps l'URL du serveur. Tester avec `./smoke-test.sh https://castbridge.exemple.org`.

## Interface d'administration (`/admin`)

Connexion par identifiant et mot de passe (compte créé au premier démarrage depuis `.env`, BCrypt coût 12). Après
5 mots de passe faux, le compte est verrouillé 15 minutes ; la page de connexion est aussi limitée par IP. Session
de 30 minutes (cookie `CBSESSION` HttpOnly, Secure, SameSite=Strict), CSRF sur tous les formulaires, CSP stricte
(aucun script ni style en ligne), thème sombre, utilisable sur téléphone, en français.

Écrans :

- **Tableau de bord** : **en premier, « Fonctionnalités les plus utilisées »** (TV et téléphone ensemble ou
  séparés) : utilisations, appareils distincts et % du parc actif, temps total et moyen, tendance par rapport à la
  période précédente (flèche verte/rouge), barres triées et courbe par semaine, filtres période / version /
  plateforme / pays / modèle / groupe, export CSV ; puis tuiles (appareils connus, en ligne dans les 15 dernières minutes, vus en 24 h / 30 jours,
  bloqués), graphique en barres des versions installées (TV et téléphone), plateformes (Android TV, Google TV,
  Fire OS, box Android, téléphone…), pays approximatifs, derniers plantages avec lien vers l'appareil.
- **Indicateurs** (`/admin/kpi`) : onglets Parc (DAU/WAU/MAU, nouveaux, perdus, rétention J1/J7/J30 par cohorte,
  répartitions), Usage (sessions, durée, écrans, entonnoir), Envois, Lecture, Quiz (dont les questions trop
  faciles / difficiles), Échecs, Téléchargements, Mises à jour (adoption), Qualité, Connectivité ; mêmes filtres,
  export CSV par onglet. Graphiques : Chart.js 4.4.1 (licence MIT) servi localement
  (`static/admin/assets/chart.umd.min.js`), aucun appel externe, données passées par attributs `data-chart`.
- **Appareils** : liste filtrable (texte, état en ligne/hors ligne/bloqué, app, plateforme, fabricant, ABI, groupe,
  versionCode, pays) et triable (nom, modèle, version, dernier contact, pays), pastille verte/grise/rouge.
- **Fiche appareil** : toutes les informations remontées (version, canal, plateforme, fabricant/modèle, système,
  build, empreinte, ABI, écran, RAM, stockage interne avec jauge, clé USB, passerelle Bluetooth / SSH / Wi-Fi Direct,
  nombre de vidéos, pays/ville, IP ≤ 30 jours, premier/dernier contact, dernière erreur) ; formulaire nom/groupe/note
  (« Salon Esaie ») ; actions : forcer une vérification de mise à jour au prochain heartbeat, canal forcé
  stable/beta, bloquer/débloquer, oublier l'appareil ; histogramme des contacts par jour sur 30 jours, versions
  successives, installations successives (une nouvelle ligne = réinstallation), plantages dépliables avec le détail,
  chronologie des 200 derniers événements d'usage, 100 derniers heartbeats détaillés, consentement et date ;
  « Effacer l'appareil et ses données » (droit à l'effacement, en cascade).
- **Versions** : formulaire de publication d'APK (app, ABI, canal, versionCode, versionName, déploiement %, notes,
  obligatoire) avec vérification du manifeste de l'APK, version minimale supportée par app/canal, liste des versions
  avec réglage du pourcentage de déploiement, lien de téléchargement, retrait, suppression.
- **Quiz** : onglets brouillons / validées / rejetées / toutes avec compteurs, filtres (texte, parcours, niveau,
  filière, région), chaque question avec ses 4 choix (le bon est marqué), explication et source, boutons Valider /
  Brouillon / Rejeter / Supprimer ; import JSON ou CSV (vérification seule cochée par défaut, erreurs listées),
  exports JSON/CSV.

L'API d'administration (`/api/v1/admin/**`, jeton Bearer) offre les mêmes fonctions pour les scripts et la CI ; le
jeton n'ouvre pas l'interface web et la session web n'ouvre pas l'API.

## Sauvegardes

`backup.sh` : `mysqldump --single-transaction` compressé (vérifié complet), rétention 14 jours, et copie miroir des
APK. `deploy.sh` l'appelle avant chaque déploiement (`--db-only`). Tâche quotidienne :

```sh
sudo touch /var/log/castbridge-backup.log && sudo chown "$USER" /var/log/castbridge-backup.log
( crontab -l 2>/dev/null; echo '15 3 * * * /opt/castbridge/backend/backup.sh >> /var/log/castbridge-backup.log 2>&1' ) | crontab -
```

Restauration :

```sh
gunzip -c /var/backups/castbridge/db/castbridge-AAAAMMJJ-HHMMSS.sql.gz \
  | docker exec -i castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'
docker cp /var/backups/castbridge/apk/. castbridge-api:/data/apk/
```

Copiez aussi `/var/backups/castbridge` hors du VPS (et la clé privée Ed25519, et `.env`).

## Sécurité (résumé)

- Aucun secret dans le dépôt : `.env`, `secrets/` ignorés par git ; clé privée en secret Docker lisible par
  l'uid 10001 seul ; les journaux ne contiennent ni en-têtes, ni chaînes de requête, ni IP.
- API admin : jeton Bearer comparé en temps constant (hachages SHA-256 comparés avec `MessageDigest.isEqual`) ;
  OpenAPI (`/v3/api-docs`) seulement avec ce jeton ; pas d'interface Swagger.
- Appareils : jeton aléatoire de 256 bits par appareil (seul son SHA-256 est stocké), ANDROID_ID jamais transmis en
  clair (SHA-256 salé par l'app, re-haché côté serveur), IP brute effacée après 30 jours, heartbeats détaillés
  30 jours puis agrégats journaliers.
- Télémétrie : consentement à deux niveaux (essentiel / statistiques d'usage, désactivées par défaut), catalogue
  fermé, clés interdites refusées, messages nettoyés des chemins/URL/noms de fichiers, événements bruts 13 mois,
  droits d'accès (`GET /api/v1/devices/me`) et d'effacement (`DELETE /api/v1/devices/me`, ou par l'admin).
- Limite de débit par IP (seau à jetons en mémoire) sur `/api`, `/dl` et la connexion web ; CORS fermé ; en-têtes
  `X-Content-Type-Options`, `X-Frame-Options: DENY`, CSP, `Referrer-Policy`, HSTS derrière HTTPS.
- Conteneurs : MySQL sans port publié sur un réseau interne sans Internet ; API non-root, système de fichiers en
  lecture seule, aucune capability, `no-new-privileges`, limites mémoire ; actuator sur un port non publié.
- Téléchargements : noms de fichiers validés (pas de `..`), fichier retrouvé via la base, jamais par chemin libre.

## Tests

```sh
cd backend && ./mvnw -q verify        # Java 21 ; ou mvn -q verify
```

- `SigningAndApkTest` : vecteurs RFC 8032, formats de clé, texte signé canonique (repris à l'identique par le test
  Kotlin de `:core`), lecture du manifeste binaire d'un vrai APK (`src/test/resources/apk/receiver-42.apk`, construit
  avec `aapt2 link` depuis `src/test/apk-src/AndroidManifest.xml`), répartition du déploiement progressif.
- `UpdatesApiTest`, `QuizApiTest`, `DevicesApiTest`, `AdminWebTest`, `RateLimitTest`, `TelemetryApiTest` (lot gzip,
  dédoublonnage, liste blanche, clés interdites, consentement, KPI sur un jeu synthétique, purge, effacement) :
  application complète via
  MockMvc sur **H2 en mode MySQL** (mêmes migrations Flyway, base neuve par classe).
- `MySqlContainerTest` : les mêmes parcours (dont la télémétrie et tous les KPI) sur un vrai **MySQL 8.4**
  (Testcontainers, données en tmpfs) ; ignoré automatiquement si
  Docker n'est pas disponible (les tests H2 tournent quand même). La CI (`.github/workflows/backend.yml`) a Docker.
- `smoke-test.sh` : vérification d'un serveur qui tourne (après déploiement).

## Côté Android (`android/core`)

Logique pure, testée (`gradle :core:test`), sans modifier `:receiver`/`:sender` :

- `castbridge.core.update` : `UpdateClient` (vérification, signature, téléchargement reprenable vers un `.part`,
  proxy optionnel : SOCKS `127.0.0.1:1080` quand la TV passe par la passerelle Bluetooth du téléphone),
  `UpdateManifest`, `Ed25519` (vérification en Kotlin pur, Android 8+), `UpdateSchedule` (au démarrage, toutes les
  12 h avec décalage aléatoire par appareil, backoff exponentiel, au plus une vérification par heure),
  `UpdateKeys.PUBLIC_KEY` (**à remplir au déploiement**).
- `castbridge.core.device` : `DeviceFacts` (ce que l'app lit d'Android, dont le consentement), `Platform`
  (détection générique), `DeviceReport`, `DeviceClient` (enregistrement, heartbeat avec réenregistrement
  transparent, plantages, `myData()` / `eraseMe()`).
- `castbridge.core.telemetry` : `Telemetry` (filtrage par consentement et catalogue, sessions, fonctionnalités,
  écrans), `EventQueue` (file persistante bornée à 2 Mo), `TelemetryUploader` (lots gzip de 500, rejouables).

L'intégration (service Android, stockage des préférences, installation du fichier vérifié) reste à faire dans les
apps.

## Reste à faire / décisions

- Exposition HTTPS via le nginx partagé : sous-domaine ou chemin (décision d'Esaie), puis `CASTBRIDGE_PUBLIC_BASE_URL`.
- Clé Ed25519 de production à générer et sa clé publique à coller dans `UpdateKeys.PUBLIC_KEY`.
- Intégration Android (heartbeat, vérification et installation des mises à jour, écran d'information et réglage du
  consentement, émission des événements `feature_used` / `screen_time`…) ; secrets GitHub pour `release.yml`.
- Validation juridique du texte d'information et des durées (`docs/TELEMETRY.md` §7).
- Base GeoLite2 optionnelle (licence MaxMind gratuite) pour la ville ; sinon pays seulement via un en-tête du proxy.
