# Déploiement du serveur 1.2.0 : portefeuille NDEM / MBOKO (runbook du coordinateur)

> **Statut : PRÉPARÉ, JAMAIS EXÉCUTÉ.** Rédigé le 2026-10-04 sans aucun accès à la production. Tout ce qui est écrit ici a été relu dans les sources (`tools/release/deploy-server.sh`, `backend/`, `docs/`) ou répété sur un **vrai MySQL 8.4** (Testcontainers, `ProductionUpgradeRehearsalTest`) ; ce qui n'a pas pu l'être est dit à la fin (§ 14). **Aucun secret dans ce fichier** : ni valeur de clé, ni mot de passe, ni jeton.
> Décision du propriétaire (2026-10-04) : la cible est **identique à la production, sans mode d'essai** : licences ET portefeuille allumés, preuve de liaison exigée, aucune clé de test, aucun drapeau qui abaisse une protection.

Noms : **CastBridge** (téléphone), **CastBridge-TV** (TV). Serveur en service : `server-1.1.0` (révision `849fbec`), Flyway au niveau **V61**, modules licences et tunnel éteints, MySQL 8.4 dans le conteneur `castbridge-db`. Cette livraison : `server-1.2.0` (code 4), migration **V62**.

## 1. Contenu de la livraison

**Dans `server-1.2.0`** (tout est dans `backend/`, ce que `deploy-server.sh` extrait) :
- **Migration V62 `V62__wallet.sql`** : 15 tables **nouvelles** (`wallet_account`, `wallet_balance`, `wallet_txn`, `wallet_entry`, `wallet_identity`, `wallet_escrow`, `wallet_result`, `wallet_alert`, `wallet_recv_code`, `wallet_policy`, `wallet_voucher_batch`, `wallet_voucher`, `wallet_license_claim`, `wallet_license_span`, `wallet_admin_grant`) et 39 lignes de politique d'exploitation (`wallet_policy`). **Aucune table existante n'est modifiée** (prouvé : `CHECKSUM TABLE` et `SHOW CREATE TABLE` de chacune des 49 tables d'avant, identiques avant et après).
- **Module portefeuille** (éteint par défaut dans le code, `castbridge.wallet.enabled=false`) :
  - TV (jeton d'appareil) : `POST /api/v1/wallet/sync`, `GET /api/v1/wallet/history`, `GET /api/v1/wallet/policy`, `POST /api/v1/wallet/escrow`, `POST /api/v1/wallet/convert`, `POST /api/v1/wallet/receive-code`, `GET /api/v1/wallet/receive-code/{code}`, `POST /api/v1/wallet/transfer`.
  - Sans jeton d'appareil (résultat signé par le service de jeu) : `POST /api/v1/wallet/settle` (503 tant que `CASTBRIDGE_WALLET_PLAY_RESULT_PUBKEYS` est vide).
  - Administration `/api/v1/admin/wallet/**` : `POST /grant`, `POST /grant/{id}/approve`, `POST /grant/{id}/reject`, `POST /rebind` (jeton administrateur **plus** compte propriétaire nommé `X-Admin-User` **plus** code TOTP à usage unique `X-Totp`, plafonds par don / par jour glissant / par heure dans `wallet_policy`, second administrateur au-delà, journal d'audit chaîné des licences) ; `GET /reconcile` et `POST /escrow-expiry` (jeton seul).
  - Opérations : blocage (escrow) `cbe1`, règlement (settle) `cbr1`, conversion NDEM/MBOKO, transfert entre TV avec codes de réception, bons hors ligne `cbv1`, instantané de solde signé `cbw1`.
- **Script d'hôte `tools/wallet/collect-results.sh`** (collecteur cron des résultats de parties misées) : **dans le dépôt, PAS extrait sur le serveur** (le déploiement n'extrait que `backend/`). À installer seulement avec le service de jeu misé (§ 13).
- **Retour arrière de schéma `tools/wallet/rollback-V62.sql`** : idem, absent du serveur ; se lit depuis le dépôt bare (§ 10.4).
- Aussi embarqué depuis `server-1.1.0` (le tag `server-1.1.1` existe mais n'a pas été déployé d'après le relevé) : lots Langues, billets de jeu en ligne `cbp1` (route `/api/v1/play/ticket`, 503 sans clé), ordres différés, évènements de location, **plafond de transferts de licence à 0 par défaut** (`CASTBRIDGE_LICENSES_DEFAULT_TRANSFER_CAP`, décision du 2026-10-04 ; les licences déjà créées gardent leur plafond).

**PAS dans cette livraison** :
- le suivi des activations (V63, module en cours de correction) ;
- le client TV du quiz en ligne et les mises (`CASTBRIDGE_WALLET_PLAY_RESULT_PUBKEYS` vide : règlement refusé, `castbridge-play` non redéployé ici) ;
- la TV portefeuille (build w22-07a) : **à construire avec la clé publique du portefeuille** (§ 4.3) ;
- aucune modification de nginx, du service `castbridge-play`, de `sti-*`.

## 2. Ce qui a été prouvé sur un vrai MySQL 8.4 (répétition)

`backend/src/test/java/castbridge/server/wallet/ProductionUpgradeRehearsalTest.java` (Docker, `mysql:8.4` avec les réglages exacts de `castbridge-db`, classement `utf8mb4_0900_ai_ci`). Commande : `cd backend && mvn -o -q test -Dtest=ProductionUpgradeRehearsalTest`.

| Preuve | Résultat |
|---|---|
| Base montée à V61, lignes réalistes (appareils TV et téléphone, compte administrateur, client, produit, licence payante, poste actif), puis V62 | 49 tables anciennes : contenu (`CHECKSUM TABLE`) et définition identiques ; 15 tables créées ; 39 lignes de politique |
| Contraintes dans MySQL | 12 `CHECK`, unicités (`uq_wallet_account`, `uq_wallet_txn_idem`, `uq_wallet_admin_grant_idem`), clés étrangères : présentes **et refusent** (monnaie inconnue, solde négatif d'un joueur, doublon de clé d'idempotence, montant nul, politique hors bornes, période d'attribution ≠ 30 jours) ; clés comparées exactement (`ascii_bin` : `Grant:1` ≠ `grant:1`) |
| Démarrage de l'application sur la base mise à niveau, module éteint (défaut) | V62 appliquée par le démarrage ; routes `/api/v1/wallet/**` et `/api/v1/admin/wallet/**` : **404** ; quiz, enregistrement d'appareil, `GET /api/v1/devices/me` d'un appareil enregistré **avant** la mise à jour : OK |
| Même base, module allumé | `GET /api/v1/wallet/policy` 200 (taux 1000, interrupteurs actifs), jeton inconnu 401, `GET /api/v1/admin/wallet/reconcile` 200 `ok:true`, synchronisation sans preuve : refus 4xx (jamais 500) |
| **Configuration cible** : licences ET portefeuille allumés, routes publiques, clé de licence dans un dossier de secrets, TOTP exigé, preuve de liaison exigée, plafond de licence 0 | l'allumage des licences n'écrit **rien** dans `lic_*` (aucun client, licence, poste, émission, révocation, évènement ; l'audit reste à 0 ; seule ligne ajoutée : le compte web du profil de test, qui existe déjà en production) ; `GET /api/v1/admin/licenses/signing` rend la clé publique du fichier, sans portée `SUPER_UNLIMITED` / `TRANSFER` / `COMMAND_OPEN_ALL` ; `GET /api/v1/revocations` 200 `cbx1…` ; **TV d'essai** (clé d'essai, aucune licence) avec preuve de liaison : **100 NDEM** crédités une seule fois ; **TV de production** dont la licence n'est pas encore enregistrée : notice `LICENSE_PENDING`, rien de crédité, puis, la licence et son poste enregistrés, tranches de production créditées (≥ 1000 NDEM et ≥ 10 MBOKO) ; sans preuve de liaison : refus 4xx ; réconciliation `ok:true` |
| Retour arrière `rollback-V62.sql` **avec des écritures présentes dans toutes les tables à clés étrangères**, exécuté par le client `mysql` du conteneur comme le fera l'opérateur | tables supprimées, ligne `62` retirée de `flyway_schema_history`, données anciennes identiques, script rejouable, V62 se réapplique ensuite proprement (grand livre vide) |
| Contre-épreuve | tables supprimées **sans** retirer la ligne d'historique : Flyway croit V62 appliquée et ne recrée rien (le module allumé échouerait). Réparation documentée : `DELETE FROM flyway_schema_history WHERE version = '62';` puis redémarrer |
| **Retour à l'image précédente avec V62 déjà dans l'historique** (`deploy-server.sh --rollback`) | l'ancien jeu de migrations (jusqu'à V61, réglages Flyway par défaut) migre sans erreur et sans rien exécuter : la migration « future » est ignorée (avertissement seulement). `--rollback` est donc sûr même après V62 |
| **Durée de V62** | **≈ 0,1 s** sur une base de 100 000 appareils (+ 100 000 installations) comme sur une base réaliste, disque en mémoire. V62 ne lit ni ne copie aucune ligne existante : la durée ne dépend pas du volume ; sur le disque du VPS, prévoir **moins de quelques secondes** |
| `WalletMySqlContainerTest` (oracle du grand livre, concurrence 16 fils) sur MySQL 8.4 | 2 tests verts |

Mutation vérifiée : retirer la ligne `DELETE FROM flyway_schema_history` du script de retour arrière fait échouer 2 tests par assertion.

## 3. Prérequis et vérifications avant le jour J (sur le Mac du coordinateur)

1. `python3 tools/release/check_versions.py` : **0 erreur** (le commit de montée de version `server 1.2.0 / code 4` supprime les 2 erreurs « server-play-0.1.x : code 3 >= code actuel 3 » qui existaient avec le code 3).
2. Le commit de version est dans la branche à déployer, **la branche est poussée vers GitHub puis `git fetch`** (le script refuse une révision qu'aucune branche `origin/*` n'atteint), arbre suivi propre.
3. Le coordinateur crée le tag annoté `git tag -a server-1.2.0 -m "serveur 1.2.0 : portefeuille NDEM/MBOKO (V62)"` sur ce commit (jamais `main`, jamais un commit nu). **Pas de tag créé dans la préparation.**
4. Remote `bridge` présent (`git remote -v` : `ubuntu@bridge.sti-cm.com:castbridge/castbridge.git`).
5. Plan à blanc (aucune connexion) : `bash tools/release/deploy-server.sh server-1.2.0` doit afficher `référence : server-1.2.0 (tag) -> <sha> (1.2.0)` et aucune ligne `BLOQUANT`.

## 4. Environnement, secrets et fichier d'override : exactement quoi mettre

`deploy-server.sh` copie sur le serveur `.env`, `secrets/`, `geoip/` et `docker-compose.override.yml` **depuis la release en service** (lien `/home/ubuntu/castbridge/current`, sinon `/home/ubuntu/castbridge/services/castbridge/backend`), il ne lit **ni `docker-compose.licenses.yml` ni les variables du dépôt**. Les variables `CASTBRIDGE_WALLET_*` et `CASTBRIDGE_LICENSES_*` n'atteignent le conteneur **que si l'override du serveur les déclare** (`docker-compose.yml` n'en passe aucune). Donc :

- **Ordre** : (1) déployer `server-1.2.0` avec les modules **éteints** (§ 6), (2) vérifier, (3) créer les secrets et éditer l'override **dans la release devenue `current`**, (4) recréer le conteneur (§ 7). Les déploiements suivants recopient l'override et les secrets de `current` : rien à refaire.
- `previous` (la release 1.1.0) garde son ancien override : un retour automatique ou `--rollback` redonne un serveur **sans** modules, cohérent.

### 4.1 Secrets à créer sur le serveur (jamais dans le dépôt, jamais affichés, jamais envoyés dans une conversation)

Règle connue (pièges) : les fichiers de `secrets/` appartiennent à l'**uid 10001** (utilisateur du conteneur), droits **0400** ; `ubuntu` ne peut pas les lire ; la copie vers la release suivante se fait par `sudo cp -a`. Ne **jamais** écraser un fichier existant.

```sh
CUR=$(readlink -f /home/ubuntu/castbridge/current)      # release en service (après le déploiement du § 6)
cd "$CUR" && ls -l secrets/                              # voir l'existant ; NE PAS TOUCHER castbridge-signing.pem
(umask 077
 [ -e secrets/license-signing.key ] || openssl genpkey -algorithm ed25519 -out secrets/license-signing.key
 [ -e secrets/wallet-signing.key ]  || openssl genpkey -algorithm ed25519 -out secrets/wallet-signing.key
 [ -e secrets/license-totp.key ]    || openssl rand -base64 32 > secrets/license-totp.key
 [ -e secrets/license-audit.key ]   || openssl rand -base64 32 > secrets/license-audit.key)
sudo chown 10001:10001 secrets/license-signing.key secrets/wallet-signing.key secrets/license-totp.key secrets/license-audit.key
sudo chmod 400         secrets/license-signing.key secrets/wallet-signing.key secrets/license-totp.key secrets/license-audit.key
sudo ls -ln secrets/                                     # attendu : -r-------- 1 10001 10001 pour les quatre fichiers
```

- **`wallet-signing.key`** : clé Ed25519 PKCS#8 (PEM) qui signe les instantanés de solde `cbw1` et les blocages `cbe1`. Sans elle (ou illisible), les routes de la TV répondent **503 « Portefeuille indisponible »**. Rotation : l'ancienne clé reste acceptée en vérification via `CASTBRIDGE_WALLET_PREVIOUS_KEY_FILE`.
- **`license-signing.key`** : clé du serveur pour les licences (portée limitée dans le code). `license-totp.key` protège les secrets TOTP au repos ; `license-audit.key` chaîne le journal d'audit, **à choisir une fois et ne plus changer**. Procédure complète d'origine : `docs/DEPLOIEMENT-PREMIERE-EXPERIENCE.md` § 2.1 et `docs/LICENSE-ADMIN.md` § 8.
- **Sauvegarde hors du serveur, chiffrée** de ces quatre fichiers avant toute émission (`docs/DEPLOIEMENT-PREMIERE-EXPERIENCE.md` § 1.3 : `sudo tar` des `secrets/`, puis chiffrement). Perdre `wallet-signing.key` rend illisibles par la TV les instantanés déjà signés tant qu'une nouvelle clé publique n'est pas installée.

### 4.2 Clés publiques à relever (publiques : sans danger dans l'override et dans les builds)

```sh
cd "$CUR"
sudo openssl pkey -in secrets/wallet-signing.key  -pubout -outform DER -out /tmp/wallet.pub.der
sudo openssl pkey -in secrets/license-signing.key -pubout -outform DER -out /tmp/license.pub.der
tail -c 32 /tmp/wallet.pub.der  | openssl base64 -A; echo     # clé publique brute du PORTEFEUILLE (44 caractères base64)
tail -c 32 /tmp/license.pub.der | openssl base64 -A; echo     # clé publique brute des LICENCES du serveur
sudo rm -f /tmp/wallet.pub.der /tmp/license.pub.der
```

(Procédure essayée localement sur une clé jetable : DER de 44 octets, 32 octets bruts, 44 caractères base64.) La clé des licences se relit aussi par `GET /api/v1/admin/licenses/signing` (`kid` + `publicKey`) : les deux doivent concorder.

### 4.3 Qui doit connaître quelle clé publique

| Clé publique | Où la déclarer |
|---|---|
| **Portefeuille** (`wallet-signing.key`) | **dans l'APK de la TV portefeuille (w22-07a)**, au même titre que `activation-trusted-keys.txt` : la TV vérifie `cbw1` avec elle. **Non vérifié ici** : le mécanisme exact de la TV (liste compilée) est dans le dossier de la TV, pas dans ce runbook. Construire la TV **après** avoir relevé la clé. |
| **Serveur des licences** (`license-signing.key`) | (a) `CASTBRIDGE_WALLET_TRUSTED_KEYS` avec les portées `ISSUE_TRIAL+ISSUE_PRODUCTION+REACTIVATE` (jamais `SUPER_UNLIMITED`, `TRANSFER`, `COMMAND_OPEN_ALL`) ; (b) l'anneau de clés de la TV (`docs/DEPLOIEMENT-PREMIERE-EXPERIENCE.md` § 2.4, portées `ISSUE_TRIAL,ISSUE_PRODUCTION,REVOKE,REGISTRY,REACTIVATE,POLICY`) ; (c) `CASTBRIDGE_PLAY_TRUSTED_KEYS` du service de jeu (§ 13). |
| **Outils hors ligne du propriétaire** (bureau, téléphone) qui ont émis les clés des TV d'essai | `CASTBRIDGE_WALLET_TRUSTED_KEYS` (portées `ISSUE_TRIAL+ISSUE_PRODUCTION`, plus `SUPER_UNLIMITED` seulement si ces outils émettent des clés « super ») **et** `CASTBRIDGE_LICENSES_TRUSTED_KEYS` (pour importer leur registre, portées `ISSUE_TRIAL+ISSUE_PRODUCTION+TRANSFER+REVOKE+REGISTRY` comme au § 2.1 de `docs/LICENSE-ADMIN.md`). **Sans cette ligne, leurs activations sont ignorées par le portefeuille** (`UNKNOWN_KEY`) : la TV n'aurait ni essai ni production. Leurs clés publiques sont **à fournir par le propriétaire** (approbation 9). |

### 4.4 Bloc à fusionner dans `docker-compose.override.yml` de `current` (le contenu actuel de ce fichier est **inconnu** : relever d'abord `cat "$CUR/docker-compose.override.yml"`, sauvegarder `cp -p`, **fusionner** sans dupliquer `services:` ni `castbridge-api:`, garder le réseau `infra-net`)

```yaml
services:
  castbridge-api:
    environment:
      # licences : allumées, comme en production
      CASTBRIDGE_LICENSES_ENABLED: "true"
      CASTBRIDGE_LICENSES_PUBLIC_ROUTES: "true"          # sert GET /api/v1/revocations (liste signée réelle)
      CASTBRIDGE_LICENSES_TRIAL_ISSUANCE: manual
      CASTBRIDGE_LICENSES_SECRETS_DIR: /run/secrets
      CASTBRIDGE_LICENSES_REQUIRE_TOTP: "true"           # défaut, ne jamais le mettre à false
      CASTBRIDGE_LICENSES_TRUSTED_KEYS: "bureau:<clé publique>:ISSUE_TRIAL+ISSUE_PRODUCTION+TRANSFER+REVOKE+REGISTRY,telephone:<clé publique>:ISSUE_TRIAL+ISSUE_PRODUCTION+TRANSFER+REVOKE"
      # (plafond de transferts de licence : défaut 0, ne rien écrire)
      # portefeuille : allumé
      CASTBRIDGE_WALLET_ENABLED: "true"
      CASTBRIDGE_WALLET_KEY_FILE: /run/secrets/wallet-signing.key
      CASTBRIDGE_WALLET_TRUSTED_KEYS: "serveur:<clé publique des licences>:ISSUE_TRIAL+ISSUE_PRODUCTION+REACTIVATE,bureau:<clé publique>:ISSUE_TRIAL+ISSUE_PRODUCTION,telephone:<clé publique>:ISSUE_TRIAL+ISSUE_PRODUCTION"
      CASTBRIDGE_WALLET_REQUIRE_BIND_PROOF: "true"       # défaut ; voir § 4.5
      CASTBRIDGE_WALLET_PLAY_RESULT_PUBKEYS: ""          # vide : règlement /wallet/settle refusé (503) tant que le service de jeu misé n'est pas déployé
    secrets:
      - castbridge_signing_key
      - source: license_signing_key
        target: license-signing.key
      - source: license_totp_key
        target: license-totp.key
      - source: license_audit_key
        target: license-audit.key
      - source: wallet_signing_key
        target: wallet-signing.key

secrets:
  license_signing_key:
    file: ./secrets/license-signing.key
  license_totp_key:
    file: ./secrets/license-totp.key
  license_audit_key:
    file: ./secrets/license-audit.key
  wallet_signing_key:
    file: ./secrets/wallet-signing.key
```

Validé avec Docker Compose v5.5 sur un faux dossier (base seule et base + ce bloc : `config -q` valide, variables et cibles `/run/secrets/*` présentes ; le secret `play_ticket_key` déclaré par `docker-compose.yml` sans fichier ne gêne pas tant qu'aucun service ne l'utilise). Vérifier chez soi : `cd "$CUR" && sudo docker compose --project-name castbridge -f docker-compose.yml -f docker-compose.override.yml --env-file .env config -q && echo valide`. Supprimer la ligne `CASTBRIDGE_LICENSES_TRUSTED_KEYS` (et les entrées `bureau`/`telephone`) tant que le propriétaire n'a pas fourni ses clés publiques : une entrée sans portée est ignorée, jamais « toutes les portées ».

### 4.5 `CASTBRIDGE_WALLET_REQUIRE_BIND_PROOF` : quelle valeur ?

- **Cible de production : `true`** (défaut du code). La première liaison d'une identité à un appareil exige la **preuve de possession** de la clé d'installation de la TV (corps de `sync`, champ `bind`).
- **La CastBridge-TV 0.14.31 ne signe pas la liaison** (pas de portefeuille) : avec `true`, une synchronisation de cette TV est refusée en 4xx. **Le build TV portefeuille w22-07a la signe** : c'est lui qu'il faut installer sur les TV d'essai du propriétaire.
- Valeur pour la démonstration : **`true`** (la décision du 2026-10-04 supprime le mode d'essai). Passer à `false` serait un affaiblissement (liaison « premier venu ») qui exige une **approbation écrite distincte du propriétaire** ; la répétition prouve le parcours complet avec `true` (preuve fournie : accepté ; absente : refusé).

### 4.6 Comptes administrateurs et TOTP (dons du portefeuille)

Les actions sensibles (dons, réaffectation de liaison) exigent un compte **propriétaire** nommé avec TOTP actif (`X-Admin-User`, `X-Totp`), le journal d'audit chaîné des licences consigne la personne. Il faut donc que le module des licences soit allumé (inscription du TOTP : `/admin/licenses/security`, clé `license-totp.key`). Un **second** compte propriétaire avec son propre TOTP est nécessaire pour approuver un don au-delà du plafond (`admin.grantMax` 10 000 NDEM / 100 MBOKO par don, `admin.dailyMax` 100 000 / 1 000 par jour glissant, `admin.grantsPerHour` 10 ; valeurs de `wallet_policy`, modifiables seulement par SQL dans leurs bornes `CHECK`). Les noms des comptes sont une approbation du propriétaire (approbation 10).

## 5. Liste de contrôle avant déploiement (avant la fenêtre)

```sh
ssh ubuntu@bridge.sti-cm.com
ROOT=/home/ubuntu/castbridge
df -h /var /home | tail -3                                     # disque : ≥ 5 Go libres (l'image candidate se construit à côté de l'ancienne)
sudo docker ps --format '{{.Names}} {{.Status}}'               # castbridge-api et castbridge-db : (healthy) ; infra-nginx, sti-* : ne pas toucher
sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1; SELECT COUNT(*) FROM device;"'   # attendu : 61, puis le nombre d'appareils (noter)
ls -ld "${CASTBRIDGE_BACKUP_DIR:-/var/backups/castbridge}/db"; ls -lh "${CASTBRIDGE_BACKUP_DIR:-/var/backups/castbridge}/db" | tail -3   # dernières sauvegardes et leur taille (noter)
readlink -f $ROOT/current 2>/dev/null || echo "pas de lien current : première exécution tracée (voir docs/coordination/VERSIONING-DEVOPS-2026-10-02.md § 11)"
sudo -n true && echo "sudo sans mot de passe : ok"
cat $ROOT/releases/HISTORY.log 2>/dev/null | tail -3
```

- **nginx : aucune modification.** `docs/PLAY-OPS.md` (relevé de l'hôte) : `/opt/infra/nginx/conf.d/castbridge.conf`, site `bridge.sti-cm.com`, **`location /` → `proxy_pass http://castbridge-api:8080`** : `/api/v1/wallet/**`, `/api/v1/admin/wallet/**` et `/api/v1/revocations` passent par cette route générale. **VÉRIFIÉ par lecture des documents, pas sur l'hôte** : avant la fenêtre, `sudo docker exec infra-nginx nginx -T 2>/dev/null | grep -n -E 'server_name|location|proxy_pass'` doit montrer que seul `location /` (et éventuellement `location /play/`) existe pour `bridge.sti-cm.com` et qu'aucune `location /api/` ne renvoie ailleurs. `client_max_body_size 200m` et `proxy_read_timeout 120s` conviennent (corps du portefeuille < 10 Ko).
- Heure creuse ; prévenir qu'`castbridge-api` redémarre deux fois (§ 6 puis § 7), **coupure d'API de l'ordre de 30 à 90 s chacune** (démarrage Spring sous 512 Mo + sonde de santé ; pas de coupure pendant la construction de l'image, qui se fait à côté). `castbridge-play` n'est pas touché (les parties en cours continuent ; les billets `cbp1` sont émis par l'API).
- `HEALTH_TIMEOUT` du script : 240 s ; au-delà, retour automatique.

## 6. Étape 1 : déploiement de `server-1.2.0` modules éteints (commandes exactes, sur le Mac)

```sh
python3 tools/release/check_versions.py                         # 0 erreur
bash tools/release/deploy-server.sh server-1.2.0                # plan, aucune connexion : relire
bash tools/release/deploy-server.sh --status --apply            # état avant : révision en service 849fbec (ou « révision initiale inconnue »)
bash tools/release/deploy-server.sh server-1.2.0 --apply        # déploie
bash tools/release/deploy-server.sh --status --apply            # état après
```

Sortie attendue de `--apply` (lue dans le script, à comparer ; horodatages et chemins varient) :
```
[deploy-server] push de refs/tags/server-1.2.0 vers bridge          (ou : « déjà présent … même objet : rien à pousser »)
[distant …] construction castbridge-api:candidate (<sha>, 1.2.0)
[distant …] sauvegarde de la base avant migration
[backup …] base : /var/backups/castbridge/db/castbridge-AAAAMMJJ-HHMMSS.sql.gz (<taille>)
[distant …] image en service conservée sous castbridge-api:previous
[distant …] démarrage de castbridge-api depuis /home/ubuntu/castbridge/releases/server-1.2.0-<UTC> (base et autres conteneurs non touchés)
[distant …] OK : castbridge-api en bonne santé
[distant …] en service : …/releases/server-1.2.0-<UTC> (<sha>, 1.2.0) ; précédente : …
```
`--status` après : `REVISION` = `<sha>` du tag, `version : 1.2.0`, `santé : healthy`, `previous : revision 849fbec…, version 1.1.0` (ou « unknown » si l'image d'origine n'était pas étiquetée), la ligne `deploy server-1.2.0 …` dans l'historique.

**Notez** le nom du fichier de sauvegarde et sa taille : c'est le point de restauration de tout ce runbook.

**En cas d'échec** du script : il revient seul en arrière (image précédente + fichiers de la release précédente) et sort en code 1 ; lire les 80 dernières lignes du journal affichées, ne rien réessayer avant de comprendre. Cas connus : `la révision … est absente du dépôt bare` (le push n'a pas eu lieu), `sauvegarde impossible : déploiement annulé` (rien n'a été modifié en service), `secrets/ impossible (sudo sans mot de passe requis)`.

**Vérifier V62** (modules encore éteints) :
```sh
ssh ubuntu@bridge.sti-cm.com
sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -e "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 2; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE \"wallet\\_%\"; SELECT COUNT(*) FROM wallet_policy; SELECT COUNT(*) FROM device;"'
# attendu : 62 1 / 61 1 ; 15 ; 39 ; le même nombre d'appareils qu'avant
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:7090/api/v1/wallet/policy      # 404 (module éteint)
cd /home/ubuntu/castbridge/current && ./smoke-test.sh                                    # smoke test existant : aucun ÉCHEC
```

## 7. Étape 2 : allumer licences puis portefeuille (override + redémarrage du seul `castbridge-api`)

1. § 4.1 : créer les quatre secrets dans `current/secrets/`. § 4.2 : relever les clés publiques. Si la TV portefeuille doit être (re)construite, **lui transmettre la clé publique du portefeuille maintenant** (§ 4.3).
2. § 4.4 : fusionner l'override de `current` (sauvegarde `cp -p` avant). **Allumer d'abord les licences seules** (ne mettre que les variables `CASTBRIDGE_LICENSES_*`) :
   ```sh
   cd /home/ubuntu/castbridge/current
   sudo docker compose --project-name castbridge -f docker-compose.yml -f docker-compose.override.yml --env-file .env config -q && echo valide
   sudo docker compose --project-name castbridge -f docker-compose.yml -f docker-compose.override.yml --env-file .env up -d --no-build --no-deps castbridge-api
   sudo docker logs --since 2m castbridge-api 2>&1 | grep -i -E "licence module|ERROR|invalid"   # attendu : « licence module: server signing key <kid> loaded »
   sudo docker exec castbridge-api sh -c 'for f in license-signing.key license-totp.key license-audit.key; do test -r /run/secrets/$f && echo "$f lisible" || echo "$f ILLISIBLE"; done'
   TOKEN=$(grep '^CASTBRIDGE_ADMIN_TOKEN=' .env | cut -d= -f2-)    # ne pas l'afficher
   curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:7090/api/v1/admin/licenses/signing    # keyLoaded:true, kid, publicKey (identique au § 4.2)
   curl -s http://127.0.0.1:7090/api/v1/revocations | cut -c1-30                                      # « cbx1… »
   ```
   `.env` est copié par le script en 0600 pour `ubuntu` : le lire sans `sudo` ; ne jamais copier le jeton ailleurs ni l'afficher (la variable `TOKEN` sert aux commandes `curl` de cette session et du § 9).
3. **Premier propriétaire** : se connecter à `/admin/licenses/security`, activer le TOTP (QR dans FreeOTP/Aegis), créer le **second** compte propriétaire et activer son TOTP.
4. Seulement ensuite : ajouter les variables `CASTBRIDGE_WALLET_*` de l'override, même `config -q` puis même `up -d --no-build --no-deps castbridge-api`.
5. **Ce que l'allumage écrit** (mesuré sur MySQL 8.4) : rien dans les tables `lic_*` ; les écritures viennent ensuite des actions du propriétaire (création de client/licence, émission, import de registre). Les tâches planifiées des licences tournent à 03:20 et 03:40 (heure de Douala).

## 8. Enregistrer les licences des TV d'essai (production) et vérifier les attributions

Le portefeuille lit la **licence** (`lic_seat` actif portant le code d'appareil de la TV, licence `PAID`) pour les attributions de production : une TV qui a une clé de production **émise hors serveur** et dont la licence n'est pas enregistrée reçoit la notice « Licence en attente d'enregistrement » et **aucune** tranche ; dès l'enregistrement, les tranches dues depuis le début de la licence sont créditées **rétroactivement** (prouvé sur MySQL).

**Procédure A : import du registre hors ligne** (TV dont la clé de production vient du bureau ou du téléphone du propriétaire) :
1. Les clés publiques du bureau et du téléphone sont dans `CASTBRIDGE_LICENSES_TRUSTED_KEYS` (§ 4.4) et le serveur a été recréé.
2. Exporter le registre de chaque outil (fichier JSON d'évènements signés `license`, `issue`).
3. *Licences > Registre > Importer*, **cocher d'abord « Simulation »** : lire le rapport (appliqué, déjà connu, refusé `UNKNOWN_KEY`/`BAD_SIGNATURE`/`KEY_NOT_ALLOWED`, conflits). Puis relancer sans la case, TOTP du propriétaire. Conflits `UNKNOWN_LICENSE` / `OVER_QUOTA` / `TWO_TOOLS` : décider un à un avec motif (`docs/LICENSE-ADMIN.md` § 2.5). L'import est une union idempotente.
4. **Vérifier** : `GET /api/v1/admin/licenses/devices/<code d'appareil>` rend le poste actif et sa licence ; ou en lecture seule `SELECT l.license_id, l.kind, l.state, l.start_at, s.state FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE s.device_code = '<code>';`.

**Procédure B : émission par le serveur** (seulement si l'APK de la TV contient déjà la clé publique des licences du serveur, `docs/DEPLOIEMENT-PREMIERE-EXPERIENCE.md` § 2.4, sinon la TV refusera l'activation) : *Licences > Émettre* avec la demande d'appareil de la TV (code, `k`, lignes `factor=`) ; la licence peut être laissée en « auto » ; copier l'activation `cba1…` (affichée une seule fois) vers la TV.

**Vérifier une TV d'essai (clé d'essai, sans licence)** : après une synchronisation de la TV portefeuille (preuve de liaison signée), en lecture seule :
```sh
sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT a.holder, a.cur, a.pocket, b.balance FROM wallet_account a JOIN wallet_balance b ON b.account_id = a.id WHERE a.holder = \"<code d appareil>\";"'
```
Attendu pour un essai : **100 NDEM** (valeur de `grant.trial.ndem`), une seule fois même après plusieurs synchronisations. Pour une TV de production avec licence : `grant.production.ndem` (1000) et `grant.production.mboko` (10) **par période de 30 jours depuis le début de la licence** (rétroactif). Une TV dont la clé n'est signée par aucune clé de `CASTBRIDGE_WALLET_TRUSTED_KEYS` n'obtient rien : lire le journal `sudo docker logs castbridge-api | grep -i wallet`.

## 9. Tests de fumée après déploiement (depuis l'hôte, 127.0.0.1:7090)

```sh
B=http://127.0.0.1:7090
# 1. politique (jeton d'un appareil de test enregistré par l'API)
REG=$(curl -s -X POST $B/api/v1/devices/register -H 'Content-Type: application/json' -d '{"installId":"00000000-0000-4000-8000-000000000001","app":"tv","versionCode":88,"androidIdHash":"'"$(printf 'smoke-test-1.2.0' | openssl dgst -sha256 | awk '{print $NF}')"'"}')
echo "$REG" | cut -c1-60                                              # contient deviceId et deviceToken (ne pas les conserver)
TV_TOKEN=<deviceToken de la réponse>
curl -s -H "Authorization: Bearer $TV_TOKEN" $B/api/v1/wallet/policy   # 200 : rate 1000, switches {stakesNdem, stakesMboko, transfer, convert, vouchers: true}
curl -s -o /dev/null -w '%{http_code}\n' -H 'Authorization: Bearer inconnu' $B/api/v1/wallet/policy   # 401
# 2. synchronisation SANS preuve de liaison : refus 4xx attendu, jamais 500
curl -s -o /dev/null -w '%{http_code}\n' -X POST $B/api/v1/wallet/sync -H "Authorization: Bearer $TV_TOKEN" -H 'Content-Type: application/json' -d '{"deviceCode":"0000-0000-0000-0000","activations":[]}'
# 3. lecture administrateur (jeton)
curl -s -H "Authorization: Bearer $TOKEN" $B/api/v1/admin/wallet/reconcile    # "ok":true (grand livre vide au début)
# 4. TOTP du propriétaire accepté SANS écrire dans le grand livre : don vers une identité inconnue
curl -s -w '\n%{http_code}\n' -X POST $B/api/v1/admin/wallet/grant -H "Authorization: Bearer $TOKEN" -H 'X-Admin-User: <compte>' -H 'X-Totp: <code à 6 chiffres>' -H 'Content-Type: application/json' -d '{"identity":"0000-0000-0000-0000","currency":"NDEM","amount":1,"reason":"test de fumée TOTP"}'
#    attendu : 404 « Identité inconnue » (le TOTP a été accepté, rien n'est écrit) ; 403 « Code TOTP incorrect ou déjà utilisé » si le code est faux ; ne pas réutiliser un code (usage unique)
# 5. licences
curl -s $B/api/v1/revocations | cut -c1-30                              # cbx1…
```
La **synchronisation réelle** (avec activation et preuve de liaison signée) ne se fait qu'avec une vraie TV portefeuille et sa vraie activation : elle est prouvée en répétition (§ 2), pas en fumée. Supprimer ensuite l'appareil de test (`DELETE /api/v1/devices/me` avec son jeton).

## 10. Surveillance pendant 24 h et arbre de décision de retour arrière

### 10.1 Surveillance (toutes les heures pendant 4 h, puis matin et soir)
```sh
sudo docker ps --format '{{.Names}} {{.Status}}' | grep castbridge                        # (healthy), pas de redémarrage inattendu
sudo docker stats --no-stream castbridge-api castbridge-db                                # mémoire d'api bien sous 512 Mo ; non mesurée en charge : à regarder
sudo docker logs --since 1h castbridge-api 2>&1 | grep -c -E ' ERROR '                    # 0 attendu
sudo docker logs --since 1h castbridge-api 2>&1 | grep -i -E 'wallet|licen' | tail -20
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:7090/api/v1/admin/wallet/reconcile | grep -o '"ok":[a-z]*'      # "ok":true
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:7090/api/v1/admin/licenses/audit/verify                       # chaîne d'audit intacte
sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT at, kind, detail FROM wallet_alert ORDER BY id DESC LIMIT 10; SELECT kind, COUNT(*) FROM wallet_txn GROUP BY kind;"'
sudo docker logs infra-nginx --since 1h 2>&1 | grep 'bridge.sti-cm.com' | grep -c ' 5[0-9][0-9] '   # réponses 5xx : 0 attendu
df -h /var | tail -1
```
À surveiller : `ok:false` de la réconciliation (**alerte immédiate** : couper le module, § 10.2, et garder la base), alertes `wallet_alert` (gain anormal au règlement), `503 Portefeuille indisponible` (clé absente ou illisible : droits `10001:10001` `0400`), messages `wallet : une entrée de castbridge.wallet.trusted-keys est ignorée` (format de la liste), `wallet : licences illisibles`, `LICENSE_PENDING` en masse (licences non enregistrées), 429 (limites d'écriture 30/min par identité, 600/min global, 60/min sur `settle`), mémoire de `castbridge-api`.

### 10.2 Arbre de décision (du plus léger au plus lourd ; décider dans cet ordre)
1. **Le portefeuille se comporte mal, le reste va bien** : `CASTBRIDGE_WALLET_ENABLED: "false"` dans l'override de `current`, puis `config -q` et `up -d --no-build --no-deps castbridge-api` : toutes les routes du portefeuille répondent 404, **les tables et le grand livre restent**, rien n'est perdu. C'est le retour normal.
2. **Les licences posent problème** : `CASTBRIDGE_LICENSES_ENABLED: "false"` (même méthode) : pages et API des licences en 404, `/api/v1/revocations` en 404 ; **conséquence** : le portefeuille ne lit plus aucune licence (« en attente »), les dons TOTP ne marchent plus (comptes TOTP des licences). Éteindre aussi le portefeuille si les attributions de production comptent.
3. **L'application 1.2.0 ne démarre pas ou est malsaine** : le script revient seul en arrière pendant le déploiement ; après coup : `bash tools/release/deploy-server.sh --rollback --apply` (image `castbridge-api:previous` + release précédente ; **V62 reste dans la base**, ce qui est sans danger : prouvé que l'ancien code migre sur une base qui a V62). Sortie attendue : `OK : retour à <release précédente> (<révision>)`.
4. **Les tables du portefeuille doivent disparaître** (dernier recours, **seulement si aucune écriture réelle** n'existe, ou après décision écrite du propriétaire de la perdre) : d'abord l'étape 3 (ou arrêter l'API), puis ce § 10.4.
5. **Corruption de données** : restaurer la sauvegarde du § 6 (`backend/README.md`, « Sauvegardes ») : arrêter `castbridge-api`, `gunzip -c <fichier>.sql.gz | sudo docker exec -i castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'`. Perd tout ce qui a été écrit depuis la sauvegarde.

### 10.3 Avant tout retrait du portefeuille : photographier le grand livre
```sh
sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --no-tablespaces -u"$MYSQL_USER" "$MYSQL_DATABASE" wallet_account wallet_balance wallet_txn wallet_entry wallet_identity wallet_escrow wallet_result wallet_alert wallet_recv_code wallet_policy wallet_voucher_batch wallet_voucher wallet_license_claim wallet_license_span wallet_admin_grant' | gzip -9 > ~/castbridge-snapshots/wallet-$(date +%F-%H%M).sql.gz
```
(le dump complet de `backup.sh` contient déjà ces tables ; ce dump séparé permet de ne restaurer que le portefeuille.)

### 10.4 Retirer V62 (`rollback-V62.sql`), essayé sur MySQL 8.4
Le script n'est **pas** sur le serveur : le lire depuis le dépôt bare **au tag** (sans scp) :
```sh
cd /home/ubuntu/castbridge
git --git-dir=castbridge.git show server-1.2.0:tools/wallet/rollback-V62.sql > /tmp/rollback-V62.sql
sudo docker cp /tmp/rollback-V62.sql castbridge-db:/tmp/rollback-V62.sql
sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" < /tmp/rollback-V62.sql' && echo "retour arrière V62 exécuté"
sudo docker exec castbridge-db rm -f /tmp/rollback-V62.sql; rm -f /tmp/rollback-V62.sql
```
Le script supprime les 15 tables (ordre des clés étrangères sûr même avec des écritures), est rejouable, **et retire la ligne `62` de `flyway_schema_history`** (c'est ce qui permet de rejouer V62 plus tard). **Ne jamais laisser tourner `server-1.2.0` (même module éteint) pendant que les tables sont absentes sans le vouloir** : au prochain démarrage Flyway recrée V62 (grand livre vide). Si les tables ont été supprimées à la main **sans** retirer la ligne d'historique : `DELETE FROM flyway_schema_history WHERE version = '62';` puis redémarrer (démontré par la répétition).

## 11. Analyse de perte de données

| Opération | Données touchées | Perte possible |
|---|---|---|
| Déploiement `server-1.2.0` | aucune ligne existante (V62 = tables nouvelles, 0,1 s) | aucune ; sauvegarde `backup.sh --db-only` avant la bascule ; l'API est indisponible 30 à 90 s au redémarrage (téléversements et téléchargements en cours coupés, reprise côté client) |
| Allumer / éteindre un module | variables d'environnement | aucune (tables conservées) |
| `--rollback` du script | image et fichiers de la release | aucune donnée ; V62 reste ; les écritures du portefeuille faites entre-temps restent en base (inaccessibles tant que l'ancien code tourne) |
| `rollback-V62.sql` | **tout le grand livre** (soldes, écritures, blocages, politique modifiée, bons, dons admin) | **totale pour le portefeuille** : seulement si aucune écriture réelle, ou après le dump du § 10.3 |
| Perte de `wallet-signing.key` | aucune ligne | les instantanés signés par l'ancienne clé ne se vérifient plus sur la TV tant qu'une nouvelle clé publique n'est pas installée : sauvegarde chiffrée hors serveur obligatoire |
| Perte de `license-totp.key` / `license-audit.key` | aucune ligne | TOTP illisibles (à réinscrire) / audit invérifiable (`docs/LICENSE-ADMIN.md` § 8) |
| Restauration de la sauvegarde | tout ce qui a été écrit après elle | oui : décision du propriétaire |

## 12. Écarts restants avec la production (honnêtes, avec la valeur visée et ce qui les lève)

| Écart | Aujourd'hui | Valeur de production visée | Ce qui le lève |
|---|---|---|---|
| Suivi des activations (V63, module « w23 ») | **non déployé** (en correction) | déployé après audit sans point critique | une version serveur ultérieure (`server-1.3.x`), migration « plus haut + 1 » |
| Règlement des mises (`/wallet/settle`) | `CASTBRIDGE_WALLET_PLAY_RESULT_PUBKEYS` vide, 503 | clé(s) publique(s) du service de jeu | déploiement du service de jeu misé (version `server-play` qui signe `cbr1`) |
| Collecteur d'hôte `tools/wallet/collect-results.sh` | non installé (script hors du serveur) | cron toutes les 5 minutes sur l'hôte | avec le service de jeu misé ; extraire le script par `git --git-dir=… show server-1.2.0:tools/wallet/collect-results.sh` |
| Service de jeu `castbridge-play` | version actuelle non touchée ; son mode `CASTBRIDGE_PLAY_REVOCATIONS` doit être `on` (défaut) avec `CASTBRIDGE_PLAY_REVOCATIONS_URL=http://castbridge-api:8080/api/v1/revocations` (hôte autorisé en http), `CASTBRIDGE_PLAY_TRUSTED_KEYS=server:<clé publique des licences>:REVOKE+ISSUE_TRIAL+ISSUE_PRODUCTION` ; le mode `off` du POC est supprimé de la cible | liste signée **réelle** servie par cette API | `CASTBRIDGE_LICENSES_PUBLIC_ROUTES: "true"` (§ 4.4) puis redéploiement du service de jeu avec ces variables (`docs/PLAY-OPS.md`) ; **valeur actuelle de `.env.play` non relevée : à relever** |
| `CASTBRIDGE_PLAY_MAX_ROOMS` (défaut du service : 400 ; le mode POC `off` limite à 20) | valeur d'exploitation **non relevée** | celle de production (défaut 400, limites de `docs/PLAY-OPS-REQUIREMENTS.md`) | la relever dans `.env.play`, la fixer, redéployer hors partie |
| Liaison par preuve de possession (`CASTBRIDGE_WALLET_REQUIRE_BIND_PROOF`) | `true` ; la TV 0.14.31 ne signe pas | `true` | installer le build TV portefeuille w22-07a sur chaque TV d'essai |
| Clé publique du portefeuille dans la TV | aucune TV ne la contient encore | compilée dans la TV portefeuille | relever la clé (§ 4.2) puis construire la TV (**non vérifié ici**) |
| Clés des outils hors ligne du propriétaire | inconnues de ce dépôt | déclarées (§ 4.3) | le propriétaire les fournit (approbation 9) |
| Limites d'écriture et de règlement (30/min par identité, 600/min global, 60/min `settle`, 120/min et rafale 60 de l'API) | valeurs par défaut du code, jamais éprouvées en charge réelle | celles de production | observer 24 h (§ 10.1), ajuster par variables `CASTBRIDGE_WALLET_*` / `CASTBRIDGE_RATE_LIMIT_*` |
| Mémoire de l'API (512 Mo, JVM) avec licences et portefeuille | mesurée seulement en tests | sous 512 Mo en charge | `docker stats` pendant 24 h |
| Plafonds d'administration (`admin.*`) et politique | valeurs de lancement de V62 | valeurs décidées par le propriétaire | `UPDATE wallet_policy SET val = … WHERE name = …` (bornes `CHECK`) après décision |
| Rédaction juridique du portefeuille | **reportée au 2026-12-31** (décision du propriétaire) | rédigée | hors de cette livraison |
| Contenu de `docker-compose.override.yml` du serveur et de `castbridge.conf` | **non relevés** | — | les relever avant la fenêtre (§ 4.4, § 5) |

## 13. Ce qu'il faudra faire plus tard (hors de cette livraison)
Service de jeu misé : `CASTBRIDGE_WALLET_PLAY_RESULT_PUBKEYS`, cron de `collect-results.sh` (`*/5 * * * *` sur l'hôte, jamais dans un conteneur), `CASTBRIDGE_PLAY_WALLET_PUBKEY` côté jeu (clé publique du portefeuille, § 4.2), interrupteurs de mise (`switch.stakes.*` de `wallet_policy`, actifs par défaut). Rien de cela n'est requis pour déployer ni pour essayer le portefeuille (essai, conversion, transfert, dons).

## 14. Ce qui n'a PAS été vérifié

- **Le contenu réel de la base de production** (volumes, lignes atypiques, données antérieures à V61, collation effective de `castbridge`) : la répétition utilise des lignes réalistes et 100 000 appareils synthétiques sur une base neuve.
- **Le chemin nginx** : lu dans `docs/PLAY-OPS.md`, jamais sur l'hôte.
- **Le contenu réel de `docker-compose.override.yml`, de `.env`, de `secrets/` du serveur** et le comportement de `sudo cp -a` sur ce VPS ; Docker Compose du serveur (la validation a été faite avec Compose v5.5 sur le Mac).
- **La TV** : aucune TV n'a été utilisée ; la liaison signée, `cbw1` côté TV et l'installation de la clé publique du portefeuille sont prouvées seulement côté serveur (vecteurs et tests).
- **Les licences réelles du propriétaire** (clés de production émises hors serveur, codes d'appareil) et le format exact de leurs registres.
- La mémoire et le débit sous charge réelle ; le temps de construction de l'image sur le VPS.

## APPROBATIONS À OBTENIR DU PROPRIÉTAIRE (chacune : oui / non)

1. **Créneau de déploiement** (date, heure, heure creuse) : deux redémarrages de `castbridge-api` de 30 à 90 s chacun. oui / non, créneau : ________
2. **Allumer le module des licences en production** (`CASTBRIDGE_LICENSES_ENABLED=true`, routes publiques, TOTP exigé) : oui / non
3. **Allumer le portefeuille en production** (`CASTBRIDGE_WALLET_ENABLED=true`) : oui / non
4. **Preuve de liaison exigée** (`REQUIRE_BIND_PROOF=true`, donc seules les TV portefeuille w22-07a se synchronisent) : oui / non
5. **Génération des clés sur le serveur** (`license-signing.key`, `wallet-signing.key`, `license-totp.key`, `license-audit.key`, 0400, uid 10001) et **sauvegarde chiffrée hors du serveur** (où ?) : oui / non
6. **Codes d'appareil des TV d'essai** dont il faut enregistrer les licences (liste fournie par le propriétaire) : fournis oui / non
7. **Procédure d'enregistrement des licences** : A (import du registre) ou B (émission serveur, exige la clé publique du serveur dans l'APK de la TV) : choix ________
8. **Construire la TV portefeuille avec la clé publique du portefeuille** relevée au § 4.2 : oui / non
9. **Clés publiques du bureau et du téléphone propriétaire** (à déclarer dans `CASTBRIDGE_LICENSES_TRUSTED_KEYS` et `CASTBRIDGE_WALLET_TRUSTED_KEYS`) : fournies oui / non
10. **Noms des comptes administrateurs** (deux comptes propriétaires, chacun avec TOTP : demandeur et approbateur des dons au-delà du plafond) : ________ et ________
11. **Plafonds d'administration et politique de lancement** (grantMax 10 000 / 100, dailyMax 100 000 / 1 000, 10 dons par heure ; taux de conversion 1000 ; essai 100 NDEM ; production 1000 NDEM + 10 MBOKO par 30 jours) : acceptés oui / non, modifications : ________
12. **Politique de retour arrière** : éteindre le module d'abord, puis `--rollback`, `rollback-V62.sql` seulement avec ton accord écrit si des écritures réelles existent : oui / non
