# Serveur 1.2.1 : suivi des activations (V63, éteint), enregistrement automatique des licences (V64, V65, V66, allumé), dates MySQL tolérantes

> Réécrit le 2026-10-04 d'après le second audit Opus (`docs/agent-reports/audit-opus-w23-05-bis.md`) et ses correctifs (`docs/agent-reports/sonnet-w23-05.md`, « Correctifs du second audit »). **Non déployé.** Cadre : `docs/DEPLOIEMENT-SERVEUR-1.2.0-PORTEFEUILLE.md` (sauvegarde automatique de la base par `deploy-server.sh`, retour arrière automatique si la santé échoue, `secrets/` et `docker-compose.override.yml` recopiés depuis la release en service). Le serveur en service est le `1.2.0` : Flyway au niveau **V62**, licences et portefeuille allumés depuis le 2026-10-04 13:57.
> **Aucun secret dans ce fichier.** Les versions (`version.properties`) ne sont pas touchées par ce lot : le coordinateur les monte (voir § 3). Si l'étiquette `server-1.2.1` est remplacée par une autre (par exemple `server-1.2.2`), remplacer le nom dans les commandes du § 5 et rien d'autre.

Règles du propriétaire (2026-10-04) respectées : les clés d'activation restent **hors ligne** ; une licence **n'est pas transférable** ; préproduction = production (aucun raccourci) ; le téléphone du propriétaire ne lit que les données de génération de clés ; `castbridge.wallet.require-bind-proof` reste **vrai**.

## 1. Ce que contient la livraison

| Élément | Effet en production |
|---|---|
| `V63__activation_tracking.sql` (23 tables `act_*`) et module `castbridge.server.activations` | Tables nouvelles, rien d'existant n'est modifié. Module **ÉTEINT par défaut** (`CASTBRIDGE_ACTIVATIONS_ENABLED=false`) : routes en 404, aucune tâche, aucune clé. |
| `V64__activation_registration.sql` (table `lic_registration`) et le **registrar** `ReportedActivationRegistrar` | Une activation de production présentée par la TV (route `wallet/sync` ou `activations/report`) crée ou rattache **la licence et le poste** côté serveur, avec preuve de possession de la TV. **ALLUMÉ au démarrage** : le registrar tourne dès que le module des licences est allumé, ce qu'il est en production depuis 1.2.0. |
| `V65__registration_hardening.sql` | Colonnes `lic_registration.ik_signed`, `lic_registration.signer_creates`, tables `lic_key_gate` (plafond par clé exact) et `wallet_period_claim` (une période payée une seule fois par TV et par monnaie). |
| `V66__install_key_binding.sql` (second audit) | Colonne `wallet_identity.expected_install_fp` : empreinte de la clé d'installation attendue après une réaffectation de liaison. |
| **Interrupteur du registrar** `castbridge.licenses.registrar.enabled` | Variable d'environnement `CASTBRIDGE_LICENSES_REGISTRAR_ENABLED`, **vrai par défaut**. À `false` : aucune activation présentée n'ouvre de licence ni de poste (réponse `REGISTRAR_OFF`, rien n'est écrit, la TV voit « licence en attente d'enregistrement »). Le reste du module (émission manuelle, portefeuille) ne change pas. Se change dans `docker-compose.override.yml` puis `docker compose up -d`, **sans redéployer** (§ 7, étape 1). |
| `common/Times` (dates MySQL tolérantes) | Corrige un défaut déjà présent en production dans les routes des commandes différées (non utilisées aujourd'hui). |

### 1.1 Règles que le propriétaire doit connaître

- **L'activation signée avec la clé d'installation de la TV (droit `ik`)** est la seule qui ouvre une licence et verse les jetons automatiquement. Sans `ik` (toutes les activations déjà émises, console 1.2.43 et bureau actuels), la présentation donne `NO_INSTALL_KEY` : la ligne attend la décision du propriétaire, aucun versement.
- **Une activation `ik` prouvée l'emporte sur une liaison prise par un voleur** (jeton sans `ik`) : l'identité de portefeuille est reliée à la TV qui prouve la clé signée (audit `WALLET_REBIND_BY_IK`, alerte douce `BINDING_TAKEN_OVER`, aucun versement perdu). Une licence enregistrée avec `ik` ne paie **que** l'identité dont la clé d'installation est cette clé.
- **Réaffectation (`POST /api/v1/admin/wallet/rebind`)** : le corps doit contenir `installKeyFingerprint`, l'empreinte lue sur l'**écran d'activation de la TV** (8 groupes de 4, majuscules ou minuscules, avec ou sans tirets). L'identité libérée n'accepte plus que la clé qui a cette empreinte : un voleur reçoit 409 (`BIND_EXPECTED`) tant que la vraie TV n'est pas passée. Sans empreinte : 400. **Ne jamais réaffecter BRX4** (inutile : elle est déjà liée).
- **Une demande d'appareil n'est pas signée** : un intermédiaire peut remplacer sa ligne `install_sig=`. Défenses : (1) la TV refuse une activation dont `ik` n'est pas sa propre clé (« Cette activation a été préparée pour une autre clé d'installation », avec les deux empreintes) ; (2) chaque émetteur (bureau, console du téléphone, ligne de commande, serveur) affiche l'empreinte de la clé qu'il signe : **la comparer avec l'écran de la TV avant de remettre l'activation** ; (3) un refus `INSTALL_KEY_MISMATCH` n'est jamais silencieux : alerte douce dans `GET /api/v1/admin/licenses/registrations` (champ `alerts`, empreintes attendue et présentée) et avis sur la TV (« Activation non reconnue : empreinte attendue … »).
- **Prolongation par le propriétaire (`LICENSE_EXTEND`)** d'une licence ouverte par notification : elle est payée (les fenêtres de service suivent les dates propres de la licence) ; l'édition affichée ne tombe pas à `NONE` tant que la licence est active.
- **Périodes payées (règle exacte)** : une période d'une TV est payée **une fois** par monnaie, quelle que soit la licence. Deux périodes dont les débuts sont à moins d'une **demi-période (15 jours)** l'une de l'autre sont la même période. Conséquence : un renouvellement anticipé (nouvelle licence qui commence avant la fin de la précédente) paie les périodes de la durée **couverte** (union des deux droits), comme le ferait la même licence prolongée ; le recouvrement n'est pas payé deux fois (2 × 90 jours avec 40 jours de recouvrement : 5 tranches, pas 6). Pour payer les deux durées en entier, la nouvelle licence doit commencer à la fin de la précédente.
- **Une TV vue dans sa fenêtre de 48 h** pendant une suspension, un quota plein ou un plafond garde sa fenêtre : à la reprise elle obtient son poste même 5 jours plus tard. Une TV jamais vue dans sa fenêtre attend toujours le propriétaire (`INSTALL_TIME_UNKNOWN`).
- **L'avis « Activation sans clé d'installation : décision du propriétaire »** ne s'affiche plus en permanence sur une TV déjà payée et liée à cet appareil (la ligne reste dans la liste du propriétaire).
- **TOTP** : un code est à usage unique **même sous concurrence** (mise à jour conditionnelle du dernier pas utilisé), pour la connexion web, les dons du portefeuille et la décision sur une activation.

## 2. Ce qui est prouvé

Sur H2 (suite du serveur) et sur **MySQL 8.4 réel** (Testcontainers, paramètres JDBC de production) :
- `TotpReplayMySqlTest` : 16 fils, un même code TOTP accepté exactement une fois (15 tours).
- `AuditW2305RateRaceMySqlTest`, `RegistrationMySqlTest` : plafond exact de 10 licences par jour et par clé sous 16 fils, 16 présentations simultanées d'une licence, deux licences d'une TV synchronisées par 12 fils (chaque période payée une fois).
- `MigrationRollbackMySqlTest` : V1 à V66, puis `U66`, `U65`, `U64` **exécutés deux fois** (rejouables), lignes `flyway_schema_history` effacées par les scripts eux-mêmes, V64 à V66 réappliquées sans réparation manuelle ; V64 interrompu à mi-chemin réparé par `U64` seul.
- Chaîne V62 vers V66 en un démarrage : migrations additives, environ 230 ms mesurées par l'audit (V63 à V65), indépendantes du nombre d'appareils.
- Retour au code 1.2.0 sur une base en V65 ou V66 : Flyway de 1.2.0 ignore les migrations futures (prouvé par l'audit ; `deploy-server.sh --rollback --apply` fonctionne).

## 3. Versions et ordre de mise à jour

1. `python3 tools/release/check_versions.py` doit donner **0 erreur**. Le coordinateur monte les versions avant tout build : serveur (nouvelle étiquette si besoin), **TV ≥ 0.14.35 (code 95)** et **téléphone ≥ 1.2.44 (code 74)**. Un build de cette branche avec les versions actuelles (TV 0.14.34-beta, téléphone 1.2.43-beta) donnerait deux APK différents sous le même `versionCode` : la mise à jour automatique ne passerait pas.
2. **Les APK signés existants** (`CastBridge-TV-0.14.34-beta-verrouillee-…apk`, `CastBridge-phone-1.2.43-beta.superadmin.apk`) **n'envoient ni ne signent `install_sig`/`ik`**. Tant que seuls ceux-là existent, toute activation émise est sans `ik` : décision du propriétaire pour chaque TV (`NO_INSTALL_KEY`).
3. **Ordre imposé** :
   1. **le serveur d'abord** (un serveur 1.2.0 refuse une ligne `install_sig=` à l'émission par le serveur) ;
   2. **puis la TV** (pour qu'elle envoie `install_sig`, affiche son empreinte et vérifie `ik`) ;
   3. **puis la console du téléphone et le bureau** (qui affichent l'empreinte) ;
   4. **puis seulement** les nouvelles activations.
   D'ici là, chaque nouvelle activation donne une décision `NO_INSTALL_KEY`, sans dommage.

## 4. Avant le jour J (sur le Mac)

1. Branche poussée sur GitHub (`git push origin integration/agents`, puis `git fetch origin`) : le script refuse une révision qu'aucune branche `origin/*` n'atteint. Arbre suivi propre : `git status --short | grep -v '^??'` vide.
2. Suite complète verte : `cd backend && mvn -o -q test` (Docker allumé pour les tests MySQL) ; `cd android && gradle … :core:test`.
3. **Préproduction d'abord, configuration identique** (préproduction = production) : V62, démarrage, trois migrations (V63 à V66 : quatre) ; une TV de test (TV ≥ 0.14.35 et console ≥ 1.2.44) avec une activation `ik`, rejouée 3 fois : un seul versement ; une activation sans `ik` : `NO_INSTALL_KEY` ; décision par compte nommé et TOTP ; vérifier que les empreintes de l'émetteur et de la TV sont identiques.
4. Comptes : au moins **un compte OWNER avec TOTP activé** (sinon la décision et les dons répondent 403).
5. `CASTBRIDGE_LICENSES_TRUSTED_KEYS` : clé de la console avec `ISSUE_PRODUCTION` ; une clé `REACTIVATE` seule ne crée rien. `castbridge.wallet.require-bind-proof=true` (défaut). Aucun mot de passe ni clé dans ce fichier ni dans le HANDOFF.

## 5. Déploiement (par le propriétaire, avec le préfixe `!` si le contrôle automatique refuse à l'assistant)

**Pré-contrôle Flyway (obligatoire) : la dernière ligne doit être V62 en succès, sans ligne V63 à V66, sans ligne en échec.**

```sh
ssh ubuntu@bridge.sti-cm.com
sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT installed_rank, version, script, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 4;"'
# attendu : première ligne = 62 / V62__wallet.sql / 1 ; aucune ligne avec success = 0 ; aucune version 63 à 66
```

Instantané « avant » de **BRX4** (code d'appareil `BRX4-W1C5-4WKB-6DGQ`, licence `lic-3tb6w-hujb5`) à garder hors du dépôt. Chaque commande : `sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "<REQUÊTE>"'`.

```sql
SELECT * FROM wallet_identity WHERE holder = 'BRX4-W1C5-4WKB-6DGQ';
SELECT a.cur, a.pocket, b.balance FROM wallet_balance b JOIN wallet_account a ON a.id = b.account_id WHERE a.holder = 'BRX4-W1C5-4WKB-6DGQ';
SELECT idem_key FROM wallet_txn WHERE idem_key LIKE 'grant:%BRX4-W1C5-4WKB-6DGQ%' OR idem_key LIKE 'grant:lic:lic-3tb6w-hujb5:%';
SELECT license_id, created_by, state, start_at, end_at, seats_allowed FROM lic_license WHERE license_id = 'lic-3tb6w-hujb5';
SELECT seat_id, device_code, state FROM lic_seat WHERE device_code = 'BRX4-W1C5-4WKB-6DGQ';
```

Le soldes attendus sont 5 000 NDEM et 50 MBOKO ; la clé `grant:lic:lic-3tb6w-hujb5:open-unlimited` dit si l'ouverture vient d'un versement de licence (sinon d'un don manuel).

```sh
git tag -a server-1.2.1 -m "serveur 1.2.1 : suivi des activations (V63 éteint), enregistrement automatique des licences (V64 à V66), interrupteur du registrar" HEAD
bash tools/release/deploy-server.sh server-1.2.1                # plan à blanc : relire (référence, version, aucune ligne BLOQUANT)
bash tools/release/deploy-server.sh --status --apply            # état avant : 1.2.0
bash tools/release/deploy-server.sh server-1.2.1 --apply        # sauvegarde de la base (noter le fichier), construction, démarrage, santé
bash tools/release/deploy-server.sh --status --apply            # état après : santé healthy, previous 1.2.0
```

Coupure d'API de 30 à 90 secondes. La sauvegarde automatique est `/var/backups/castbridge/db/castbridge-AAAAMMJJ-HHMMSS.sql.gz` : **noter le nom**.

## 6. Vérifications après déploiement

```sh
sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -e "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE \"act\\_%\"; SELECT COUNT(*) FROM device;"'
# attendu : 66 1 / 65 1 / 64 1 / 63 1 / 62 1 ; 23 ; le même nombre d'appareils qu'avant
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:7090/api/v1/admin/activations/summary   # 404 (module des activations éteint)
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:7090/api/v1/wallet/policy               # 401 (portefeuille allumé)
sudo docker logs --since 5m castbridge-api 2>&1 | grep -i -E "ERROR|registrar|wallet" | tail -8
cd /home/ubuntu/castbridge/current && ./smoke-test.sh                                              # aucun ÉCHEC
```

Puis, dans cet ordre :
1. **Deux synchronisations de BRX4** : soldes identiques à l'instantané (5 000 / 50), identité `UNLIMITED`.
2. `SELECT status, reason, COUNT(*) FROM lic_registration GROUP BY 1, 2;`
3. `GET /api/v1/admin/licenses/registrations` (jeton d'administration) : lignes en attente et `alerts`.
4. `SELECT * FROM wallet_period_claim WHERE holder = 'BRX4-W1C5-4WKB-6DGQ';` (au plus la case d'ouverture `OPEN|0`).
5. **Aucune réaffectation (`rebind`) de BRX4 ni d'une autre TV** sans l'empreinte lue sur son écran.

### 6.1 Ce que voit BRX4 (cas réel, code TV 0.14.33 ou 0.14.34, jeton sans `ik`)

Hypothèses à vérifier avant (l'audit n'a pas lu la production) : identifiant de licence et de poste du jeton de BRX4, date d'émission, clé de la console présente dans `CASTBRIDGE_LICENSES_TRUSTED_KEYS`, origine des 5 000 / 50. Aucune ligne existante n'est modifiée par les migrations.

| Cas | Première synchronisation | Ce que la TV voit | Action du propriétaire |
|---|---|---|---|
| 1. Clé de la console absente de `TRUSTED_KEYS` | `REFUSED/UNKNOWN_KEY`, rien d'écrit | aucun avis, rien ne change | ajouter la clé (variable d'environnement), puis cas 2, 3 ou 4 |
| 2. Jeton qui désigne `lic-3tb6w-hujb5` et le poste par défaut `d6afd1d7f1514f8e` | `ATTACHED` : une ligne `lic_issuance` (`source='REPORT'`), une ligne `lic_registration` (`ik_signed=0`), alerte douce `INSTALL_TIME_UNPROVEN` une fois | rien (soldes 5 000 / 50, `UNLIMITED`) ; la licence n'ayant aucune clé `ik` enregistrée, elle paie comme avant | aucune |
| 3. Même licence, autre identifiant de poste | `PENDING_DECISION/NO_INSTALL_KEY` | **plus d'avis permanent** (TV déjà payée et liée) ; `registration[]` porte le motif | **Accepter** : sans risque (un alias de poste, aucun versement) ; refuser fait aussi taire la ligne |
| 4. Autre identifiant de licence | `PENDING_DECISION/INSTALL_TIME_UNKNOWN` (jeton de plus de 48 h) ou `NO_INSTALL_KEY` ; aucune licence créée | rien (la TV a déjà une licence) | **REFUSER** (accepter créerait une seconde licence au nom du client anonyme) |

Dans tous les cas : le portefeuille convertit les clés `grant:lic:lic-3tb6w-hujb5:*` en cases (au plus `OPEN|0`, une écriture), **soldes inchangés**, prochaine tranche de 1 000 + 10 à `start_at` + 30 jours, une seule fois. L'identité et la clé d'installation de BRX4 ne changent pas. **Après la mise à jour de la TV (≥ 0.14.35) et une nouvelle activation avec `ik`** : BRX4 présente sa clé, la liaison existante est déjà la sienne (rien à reprendre), la licence reçoit une ligne `ik_signed` et continue de payer BRX4 ; une autre TV ne peut pas la payer.

## 7. Retour arrière (arbre de décision)

À lire les scripts au tag : `git --git-dir=/home/ubuntu/castbridge/castbridge.git show server-1.2.1:backend/src/main/resources/db/rollback/U66__install_key_binding_rollback.sql` (idem U65, U64 ; le déploiement n'extrait que `backend/`).

1. **Le registrar fait un dégât (mauvaises licences créées, versements inattendus) : l'éteindre, sans redéployer.** Dans `docker-compose.override.yml` de la release en service, ajouter `CASTBRIDGE_LICENSES_REGISTRAR_ENABLED: "false"` à l'environnement de l'API, puis `docker compose up -d api` (redémarrage de 30 à 90 s). Aucune donnée n'est perdue ; les licences déjà créées restent. Pour le rallumer : supprimer la ligne et recommencer.
2. **Le code 1.2.1 ne va pas, la base est saine** : `bash tools/release/deploy-server.sh --rollback --apply` : revient à l'image 1.2.0 (Flyway de l'ancien code accepte une base en V66, les tables restent inertes). **Avant**, lister :
   - `SELECT license_id FROM lic_license WHERE created_by LIKE 'report:%';` : l'ancien code paierait ces licences **sans limite de rattrapage ni fenêtre** et ignorerait les cases (double versement possible pour une TV à deux licences) ;
   - les TV à plusieurs licences : `SELECT device_code, COUNT(DISTINCT license_pk) FROM lic_seat WHERE state = 'ACTIVE' GROUP BY device_code HAVING COUNT(DISTINCT license_pk) > 1;`
3. **Le schéma doit aussi revenir** (seulement si nécessaire ; serveur arrêté, sauvegarde faite) : **dans cet ordre**, `U66`, `U65`, `U64`, puis `tools/activations/rollback-V63.sql`. **Chaque script est rejouable** (si l'un échoue à mi-chemin, le relancer) et **efface lui-même** sa ligne `flyway_schema_history` (V66, V65, V64 ; `rollback-V63.sql` efface la sienne). Ne rien effacer à la main. Sans les lignes effacées, le redémarrage échouerait « Validate failed ».
4. **V64 (ou V65, V66) s'est arrêtée à mi-chemin** : l'historique montre `success=0` pour cette version, une table ou une colonne partielle existe, le nouveau code ne démarre pas (« Validate failed »), l'ancien code 1.2.0 redémarre (donc le retour arrière automatique de `deploy-server.sh` fonctionne). Réparation prouvée : lancer le script `U` de cette version (et de celles au-dessus, dans l'ordre inverse) : il retire l'objet partiel **et la ligne en échec**, puis redémarrer le nouveau code : les migrations s'appliquent à nouveau (prouvé : `MigrationRollbackMySqlTest`).
5. **Dernier recours** : restaurer la sauvegarde notée au § 5 (`gunzip -c … | mysql`), avec le code 1.2.0. **Toute écriture faite depuis le déploiement est perdue** : à n'utiliser que si les étapes 1 à 4 échouent.

## 8. Perte de données

Aucune par la migration : additive, sauvegarde automatique avant migration, retour arrière prouvé sur MySQL réel. Les jetons NDEM / MBOKO versés par le registrar restent dans le grand livre ; il n'y a pas de retour arrière automatique des versements.

## 9. Ce qui n'est pas vérifié

- Pas de serveur réel, pas de vraie TV, pas de lecture de la base de production : le cas BRX4 repose sur les hypothèses du § 6.1.
- La vérification de `ik` par la TV et l'affichage de l'empreinte sur l'écran de la TV demandent **trois lignes** dans `ActivationCenter` (voir le rapport de livraison, « Crochet à ajouter par le coordinateur ») ; l'écran d'activation de la TV doit afficher l'empreinte (`InstallSigner.fingerprintText()`).
- La TV 0.14.35 et la console 1.2.44 ne sont pas construites ni signées ici.

## APPROBATIONS À OBTENIR DU PROPRIÉTAIRE (oui / non)

1. Créneau : une coupure d'API de 30 à 90 secondes pour le serveur ; une seconde si le registrar doit être éteint (§ 7, étape 1).
2. Lancer les commandes du § 5 avec `!` (ou ajouter une règle de permission pour `deploy-server.sh`).
3. Le registrar est **ALLUMÉ dès le démarrage** (décision à confirmer : sinon définir `CASTBRIDGE_LICENSES_REGISTRAR_ENABLED=false` avant le premier démarrage).
4. Montée des versions (TV 0.14.35 / 95, téléphone 1.2.44 / 74, serveur selon le coordinateur) avant tout build, et ordre : serveur, TV, console et bureau, nouvelles activations.
5. Cas BRX4 : accepter (cas 3) ou refuser (cas 4) la ligne en attente selon ce que montre `GET /api/v1/admin/licenses/registrations`.
6. Rédaction juridique reportée au 31/12/2026 (décision du propriétaire) : aucune action ici.
