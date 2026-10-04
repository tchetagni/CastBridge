# Re-audit Opus w23-05 bis : correctifs `f239815c` (activation liée à la clé d'installation, cases de période, REACTIVATE, refus définitif, plafond, décision nommée)

Base : `integration/agents` à `c387c831` (fusion de `f239815c`). Lecture seule, sauf ce fichier. Aucun commit, aucun accès serveur, aucun déploiement, aucun réseau hors localhost.

Preuves : copie neuve de `backend/` et `tools/` hors dépôt (`<scratchpad>/reaudit-w2305bis/`), créée pour cet audit. J'y ai écrit deux classes de test :
* `ReauditW2305BisProofTest` : 7 tests sur H2, dans le contexte complet des trois modules, avec `require-bind-proof=true`.
* `ReauditMigrationChainMySqlTest` : 2 tests sur MySQL 8.4 réel (Testcontainers).

Chaque assertion décrit le comportement ATTENDU : un échec est donc un défaut démontré. **6 tests sur 9 échouent sur le code audité** (R1, R2, R3, R4, R6, R7). R5 et les deux tests de migration passent.

J'ai aussi relancé, dans la copie, `AuditW2305RateRaceMySqlTest` et `RegistrationMySqlTest` (MySQL 8.4 : 7/7 verts), les vecteurs Java (`WireFormatVectorsTest`, `EnvelopeVectorsTest`, `ServerIssuedInstallKeyTest`, `DeviceRequestInstallLineTest` : verts) et `tools/activation/verify_vectors.py` (379 contrôles, 0 échec). Les suites complètes n'ont pas été lancées : c'est le travail du coordinateur.

---

## Constats classés

### HIGH-A : la clé `ik` protège la CRÉATION de la licence, pas le BÉNÉFICIAIRE. L'identité de portefeuille reste « premier qui prouve ». Un détenteur d'un jeton sans `ik` capte les 5 000 + 50 que déclenche l'activation `ik` de la vraie TV, et la réaffectation (rebind) ne tient pas.

**Où :**
* `WalletSyncController.java:110-117` : liaison au premier appareil qui prouve une clé quelconque, sans rapport avec `ik`.
* `GrantService.java:128-209` : versement à l'identité (code d'appareil), quel que soit l'appareil API lié.
* `ReportedActivationRegistrar.java:189` : le contrôle `ik` ne regarde que la présentation en cours.
* `WalletRepository.clearBinding:81` et `WalletAdminController.rebind:182-194` : après un rebind, le premier qui prouve reprend l'identité.

**Preuve R1** (`r1_anIdentityCapturedWithALegacyTokenIsNotPaidByTheRealTvsIkActivation`, échoue) :
1. Le voleur, avec sa propre clé et un jeton SANS `ik` du même matériel, synchronise le premier. Il ouvre l'identité et la lie à son appareil (l'enregistrement donne `NO_INSTALL_KEY`, rien n'est payé).
2. La vraie TV présente ensuite une activation AVEC son `ik`. Le registrar l'enregistre, et les 5 000 NDEM sont versés à l'identité liée au voleur :

```
R1 real sync : registration=[{"status":"REGISTERED",…}] edition={"ed":"UNLIMITED",…,"boundOther":true} NDEM=5000
R1 thief sync : edition={"ed":"UNLIMITED","license":"ACTIVE","grace":false,"boundOther":false}
expected: <false> but was: <true>  (NDEM=5000, lié à=1 = voleur)
```

Côté voleur, l'identité est « vivante » (`accepted && !boundOther`, `WalletSyncController:147`) : il peut miser ces jetons.

**Preuve R2** (`r2_afterARebindTheThiefWithALegacyTokenRecapturesTheIdentity`, échoue) : après `POST /api/v1/admin/wallet/rebind` (compte nommé + TOTP), le voleur synchronise avant la vraie TV et reprend la liaison. La vraie TV présente pourtant une activation `ik` qu'elle prouve. Résultat : `expected: <4> but was: <3>`, c'est-à-dire liée au voleur et `boundOther=true` pour la vraie TV.

**Conditions :**
* un jeton de production sans `ik` (ou tout jeton que `EditionReader` accepte) pour le MÊME matériel ;
* ET une identité encore libre : TV jamais synchronisée, ou identité libérée par un rebind.

Les TV du parc déjà synchronisées (BRX4 comprise) ne sont pas exposées tant qu'on ne les réaffecte pas. Sont exposées toutes les TV activées APRÈS le déploiement avec les émetteurs actuels (téléphone 1.2.43, voir MEDIUM-D), et toute TV réaffectée.

**Ce que cela bloque pour la vraie TV :**
* `BOUND_OTHER_TV` permanent : pas de mise, et les versements vont à l'autre appareil.
* La seule « réparation » documentée (rebind) se perd au profit du premier qui repasse.
* Le rapport `sonnet-w23-05` présente ce cas comme « identité vide liée » : c'est inexact. Elle se remplit dès que la vraie TV obtient une activation `ik`, ou dès que le propriétaire accepte la ligne `NO_INSTALL_KEY`.

**Correctif proposé :**
1. Dans `sync`, une activation `ik` prouvée l'emporte sur la liaison existante quand `wallet_identity.install_pub` est différente (relier l'identité à l'appareil qui prouve `ik`, audit `WALLET_REBIND_BY_IK`).
2. Ne verser une tranche d'une licence dont la ligne `lic_registration` porte `ik_signed=TRUE` que si `wallet_identity.install_pub = ik`.
3. Le rebind doit prendre en paramètre l'empreinte de la clé d'installation attendue (lue sur l'écran de la TV) et ne lier QUE cette clé.

### MEDIUM-A : la prolongation par le propriétaire (`LICENSE_EXTEND`) d'une licence `report:` n'est jamais payée, et l'édition affichée tombe à NONE

**Où :** `JdbcLicenseFacts.windows:83-118` ne lit que les droits `usage` des jetons enregistrés. `GrantService.clip:264` coupe les intervalles de la licence à ces fenêtres, sans tenir compte de `LICENSE_EXTEND`. Or `alignEnd:445` dit justement que la décision du propriétaire prime.

**Preuve R4** (échoue) : clé de 30 jours, puis le propriétaire prolonge la fin à J+90 (`licenses.extend`) ; synchronisation à J+65 :
```
expected: <3000> but was: <1000>   edition={"ed":"NONE","license":"ACTIVE",…}
```
La licence est `ACTIVE` jusqu'à J+90, mais la TV voit `NONE` et ne reçoit ni p1 ni p2.

**Correctif proposé :** `windows()` doit renvoyer `null` (aucune restriction), ou étendre la dernière fenêtre jusqu'à `end_at`, dès qu'un `LICENSE_EXTEND` ou un `LICENSE_UPDATE` du propriétaire existe pour la licence.

### MEDIUM-B (régression introduite par `f239815c`) : un code TOTP n'est plus à usage unique sous concurrence. Cela touche les dons du portefeuille ET la décision sur une activation.

**Où :** `LicenseAccounts.verifyNamedAdmin:49-58` appelle `this.checkLoginCode(...)`. C'est une auto-invocation : le `@Transactional` de `checkLoginCode:147` est contourné par le proxy Spring. Le `SELECT … FOR UPDATE` tourne donc en autocommit, le verrou tombe aussitôt, et plusieurs requêtes lisent le même `totp_last_step`. Avant le correctif, `AdminAccess.verify` appelait `accounts.checkLoginCode` À TRAVERS le proxy, donc dans une transaction.

**Preuve R7** (échoue) : 8 appels simultanés avec le même code, 15 tours :
```
le même code TOTP accepté plusieurs fois en parallèle ==> expected: <1> but was: <7>
```
**Vérification du correctif** : avec `@Transactional` sur `verifyNamedAdmin`, R7 passe.

Il faut en plus le jeton porteur d'administration. Mais c'est un contrôle de sécurité affaibli sur deux routes qui créent de la valeur (`/api/v1/admin/wallet/grant…`, `/registrations/{fp}/decision`).

**Correctif :** `@Transactional` sur `verifyNamedAdmin`, et un test de concurrence (R7) dans la suite.

### MEDIUM-C : la demande d'appareil n'est pas signée. Un intermédiaire qui remplace `install_sig=` par sa propre clé obtient la licence et le portefeuille ; la vraie TV est refusée sans avis.

**Où :**
* `DeviceIdentity.parseRequest:166-172` (serveur) et `OwnerFrames.parseDeviceInfo` / `DeviceRequest.parse` (bureau, CLI, console) : la clé est lue telle quelle dans un texte collé ou relayé.
* `OwnerCli inspect` affiche seulement « présente/absente », jamais l'empreinte.
* La TV ignore le droit `ik` à l'activation : elle ne vérifie pas que c'est SA clé.
* `ReportedActivationRegistrar:189-191` répond `INSTALL_KEY_MISMATCH` en `REFUSED`, donc sans ligne, sans avis (`registrationNotices:198`) et sans recours du propriétaire (absente de `pending`).
* `WalletSyncController.withoutForeignInstallKeys:218-234` retire même le jeton de la lecture de la vraie TV, qui reçoit `ACTIVATE`.

**Scénario** (raisonné, la mécanique est celle prouvée par `f1b` dans l'autre sens) :
1. Le revendeur qui relaie la demande par messagerie remplace `install_sig` par sa clé `K_M`.
2. L'activation porte `ik=K_M` et les facteurs de la vraie TV.
3. Le revendeur s'inscrit `app=tv`, annonce le code de la TV (le contrôle `CLONE` ne compare que le code annoncé au code du jeton), et prouve `K_M`. Il obtient licence, poste, identité, 5 000 + 50.
4. La vraie TV, activée localement, ne touche rien et ne voit aucun avis.

Le chemin de la console du téléphone (route `GET /api/tv/device-request` sur le réseau local, PIN) est nettement plus sûr que le copier-coller.

**Correctif proposé :**
* la TV vérifie à l'activation que `ik` est sa propre clé, sinon elle refuse en le disant ;
* les émetteurs affichent l'empreinte lisible de `install_sig` (8 groupes de 4) à comparer à l'écran de la TV avant de signer ;
* `INSTALL_KEY_MISMATCH` devient au minimum un avis visible sur la TV.

### MEDIUM-D : déploiement et versions. Le serveur actuel allume le registrar dès son démarrage, les applications signées n'envoient pas `install_sig`, et la version sur HEAD entre en collision avec l'APK déjà signé.

1. **Builds signés existants SANS `install_sig` ni `ik`** (`unzip -p … classes*.dex | grep install_sig` = 0) : `~/CastBridge-release/SIGNED/CastBridge-TV-0.14.34-beta-verrouillee-armeabi-v7a.apk` (17:15) et `CastBridge-phone-1.2.43-beta.superadmin.apk` (17:16). Les deux ont été produits avant `f239815c` (18:09).
   * Conséquence après le déploiement du serveur : TOUTE activation de production émise par la console 1.2.43 (ou par le bureau actuel) est sans `ik`. Elle donne donc `NO_INSTALL_KEY` : aucune ouverture automatique, décision du propriétaire pour chaque TV.
   * De plus, une TV 0.14.34 n'envoie pas `install_sig` : même une console à jour émettra sans `ik` pour elle.
2. **Collision de versions** : `version.properties` n'a pas bougé depuis `4b884064`. Un build de HEAD serait donc encore TV 0.14.34-beta (94, ou 95 en verrouillée) et téléphone 1.2.43-beta (73), mais AVEC `install_sig`. Il y aurait ainsi deux APK différents sous le même `versionCode`, et la mise à jour automatique ne passerait pas. Il faut monter les versions (TV 0.14.35/95, téléphone 1.2.44/74) avant tout build.
3. **`docs/DEPLOIEMENT-SERVEUR-1.2.1.md` est périmé** : il décrit « V63, module éteint, rien ne change pour les TV ». Or HEAD (toujours `server.versionName=1.2.1`, code 5) contient V64 et V65, et le registrar s'allume avec le module des licences, déjà allumé en production depuis 1.2.0. Aucun interrupteur propre au registrar n'existe : seul `castbridge.licenses.enabled` coupe tout. Il faut une nouvelle version serveur (1.2.2 ou 1.2.1 redéfinie, avec doc à jour) et, idéalement, un interrupteur `castbridge.licenses.auto-registration`.
4. **Ordre imposé** :
   * le serveur AVANT la TV et le téléphone : un serveur 1.2.0 refuse `install_sig=` dans l'API d'émission (« ligne inattendue »). Cela ne concerne que l'émission par le serveur ; l'émission hors ligne n'en dépend pas ;
   * puis la TV (pour qu'elle envoie `install_sig`) AVANT d'émettre de nouvelles activations ;
   * puis la console et le bureau.

### LOW

| # | Constat | Où / preuve |
|---|---|---|
| LOW-A | Une TV vue pendant une suspension n'a son poste automatiquement qu'avec une reprise dans les 48 h de l'émission. Au-delà, elle passe en `INSTALL_TIME_UNKNOWN` (décision du propriétaire). Ce n'est pas une régression (avant : `ATTACHED` sans poste, pour toujours), mais le rapport dit à tort « a son poste après la reprise ». | `evaluate:253-255` avant `:298`. Preuve R3 : `expected: <REGISTERED> but was: <PENDING_DECISION>` (`INSTALL_TIME_UNKNOWN`) |
| LOW-B | Arrondi des cases. Un renouvellement anticipé de plus d'une demi-période (nouvelle licence qui commence avant la fin de la précédente) perd une tranche : 2 × 90 j vendus, 5 tranches au lieu de 6. | `GrantService.slotOf:229`. Preuve R6 : `expected: <6000> but was: <5000>` |
| LOW-C | `U65` n'est pas rejouable : `ALTER … DROP COLUMN` sans `IF EXISTS`, qui n'existe pas en MySQL. `U64` et `U65` n'effacent pas les lignes Flyway (commentaire seulement). Aucun test n'exécute `U64`/`U65` (`low7_…` lit seulement le texte). | `ReauditMigrationChainMySqlTest.chain` : un 2e `U65` échoue à l'instruction 3 ; lignes 64/65 encore là après les scripts |
| LOW-D | L'avis `NO_INSTALL_KEY` reste affiché à chaque synchronisation pour une TV déjà licenciée et payée, jusqu'à la décision. C'est le cas de BRX4 si son jeton désigne la licence manuelle sous un autre identifiant de poste. Non couvert : la mutation N9 survit. | `registrationNotices:200-202` |
| LOW-E | Parité : `ActivationService` (Java) signe `ik` aussi pour `subject=phone`. Kotlin (`ActivationIssuer:118`) et Python le refusent. | `ActivationService.java:187-191` |
| LOW-F | Incompatibilité latente : `DelegatedVerifier.refusal:50` refuse TOUT droit inconnu. Une activation de point focal (délégation) qui porterait `ik` serait rejetée par la TV quand ce chemin sera branché (aujourd'hui seulement dans `core` et les vecteurs). | `DelegatedVerifier.kt:50` |
| LOW-G | `WINDOW_TOO_LONG`, `CLOCK`, `INSTALL_KEY_MISMATCH` et `UNKNOWN_KEY` sont des refus sans ligne : rien dans la liste du propriétaire, aucun avis sur la TV (seulement `registration[]`). Aucun recours possible. | `check:180-191`, `registrationNotices:198` |
| LOW-H | Une synchronisation SANS preuve lit encore les jetons `ik` d'une autre TV (`proven ? filtre : activations`) et renvoie l'instantané signé (soldes) d'une identité liée à un autre appareil. Défaut antérieur, mais le commentaire « n'est lue QUE pour la TV qui la détient » est faux. | `WalletSyncController:101`, `:144-153` |
| LOW-I | Les cases ne couvrent que licence contre licence. Une licence acceptée tard dont le début précède des périodes d'essai déjà payées repaie ces périodes (essai + licence). Défaut antérieur, borné par le rattrapage. | `GrantService:148-153` contre `:159` |
| LOW-J | TV qui régénère sa clé d'installation (données effacées, réinstallation) : son jeton `ik` est retiré de sa propre lecture, donc plus d'édition et plus de versement (`ACTIVATE`), et un nouvel appareil API donne `BOUND_OTHER_TV`. Réparation : rebind (compte nommé + TOTP) PUIS une nouvelle activation avec la nouvelle `ik` (le poste existant est rattaché, le versement reprend sur la même licence). Le rebind reste exposé à HIGH-A. La clé est un fichier en clair (`PlainWrapper`), copiable par sauvegarde ADB. | `WalletHub.installSigner:211-213`, `WalletSyncController:229` |
| LOW-K | Coût par synchronisation : une ligne `REGISTERED`/`ATTACHED` fait encore vérification de signature, `FOR UPDATE`, `UPDATE last_server_at` et transaction toutes les 15 min par TV, soit environ 110 UPDATE/s pour 100 000 TV. Chaque premier enregistrement écrit dans le journal d'audit chaîné (tête unique verrouillée). | `apply:229-231` |
| LOW-L | Documents : `TV-DEMANDE-APPAREIL.md` dit encore « ces cinq clés » (six maintenant). `DEPLOIEMENT-SERVEUR-1.2.1.md` est périmé (MEDIUM-D). | |

---

## Réponses point par point

**(1) HIGH-1 (`ik`)**
* **Jeton `ik` copié** : refusé. La preuve `bind` est liée au code, à l'appareil API et à l'heure ± 5 min (`BindProof:38-49`), et la clé prouvée doit être EXACTEMENT `ik` (`check:189`). Le portefeuille retire ce jeton de la lecture de tout autre appareil (`:218`). Une clé fournie par l'attaquant dans la même requête ne sert à rien : `ik` vient du jeton signé, pas de la requête.
* **Rejeu d'une preuve capturée** depuis un autre appareil : invalide (`devicePublicId` dans le message). Depuis le même appareil, il faut son jeton porteur, ce qui revient à être cet appareil.
* **Jeton sans `ik`** : voir HIGH-A (liaison, versements et rebind non durable).
* **Confusion entre `install=x25519` et `install_sig`** : non. Les préfixes sont distincts (`startsWith("install=")` ne capte pas `install_sig=`, et Kotlin teste `key ==`). Une clé X25519 mise dans `install_sig` ne peut produire aucune preuve Ed25519 : refus, au détriment du seul demandeur.
* **Émetteurs** (bureau, CLI, console, serveur) : la clé vient toujours de la demande d'appareil, qui n'est PAS signée (MEDIUM-C). `LicensedIssuer` et `OwnerCli` la mettent seulement en production, et `ActivationIssuer` refuse un doublon et une autre cible que la TV. Le serveur ne vérifie pas la cible (LOW-E).
* **`install_sig` d'une AUTRE TV** dans la demande : seul le détenteur de la clé privée correspondante peut s'en servir. Si c'est la clé de l'intermédiaire, il gagne (MEDIUM-C). Si c'est celle d'une TV tierce honnête, personne ne gagne rien, et l'acheteur perd son activation.
* **Fenêtre de 48 h et `CLOCK`** : `notAfter − issuedAt ≤ 48 h` et `issuedAt ≤ now + 1 h` sont vérifiés (`check:180-181`), cohérents avec `MAX_WINDOW_HOURS=48` (Kotlin `CODE_VALIDITY_HOURS=48`, Java `48`).

**(2) HIGH-3 (cases)**
* **Conversion** : elle se fait PARESSEUSEMENT, à la première synchronisation de chaque identité (`backfillClaims`), et non à la migration. Elle est sans écriture au grand livre, idempotente (`claimed.add` puis INSERT, doublon ignoré), faite avant toute pose dans la même synchronisation, donc sans course.
* **Preuve R5** (passe) : base vidée de ses cases après ouverture + 2 périodes, puis seconde licence acceptée et 3 synchronisations. Résultat : 7 000 / 70, et 3 cases.
* Cette conversion n'était couverte par AUCUN test de la suite : la mutation N11 (supprimer `backfillClaims`) survit.
* La case d'ouverture `OPEN|0` est unique par identité, en plus de `opened_unlimited`. Les intervalles de suspension sont inchangés (`LicenseSpanBook`), et la libération de poste aussi (`wallet_license_claim`).
* Limites : LOW-B (arrondi), LOW-I (essai). Une licence qui n'apparaît plus dans `forDevice` (poste libéré sans révocation) ne voit pas ses anciens versements convertis. C'est sans effet sauf licence ultérieure antidatée.

**(3) HIGH-2 et les MEDIUM**
* REACTIVATE ne crée, n'aligne et ne compte rien (`evaluate:260, 280, 285, 301` ; `unlimitedByCreatorKey`). Note : `windows` lit `signer_creates` figé à l'insertion, alors que `unlimitedByCreatorKey` lit la portée COURANTE de la clé (incohérence seulement si la configuration change).
* Refus définitif : correct (`apply:222`, avant toute écriture).
* Interruption entre deux clés : correcte, mais MEDIUM-A (prolongation par le propriétaire). La fusion des fenêtres par la grâce n'est pas testée (mutation N5).
* `lic_key_gate` : 16 fils sur MySQL donnent exactement 10 (relancé, vert). L'ordre des verrous reste cohérent : la création verrouille la ligne de clé avant d'insérer la licence, et le rattachement verrouille `lic_license` puis la clé. Aucun cycle trouvé.
* Suspension : LOW-A.
* Décision : compte nommé + TOTP, rôle OWNER obligatoire, audit au nom du compte (`REGISTRATION_DECIDED`), 404 sur empreinte inconnue, 409 si la ligne n'est pas en attente. Mais le TOTP est rejouable en parallèle : MEDIUM-B.

**(4) Chaîne de migrations en production (V62 vers V63+V64+V65 en un démarrage)**
* Ordre : 63, 64, 65. Toutes sont additives (tables neuves, et `ALTER` sur `lic_registration`, vide car créée par V64). La durée ne dépend pas du nombre d'appareils : **230 ms** mesurés sur MySQL 8.4.
* Échec à mi-chemin de V64 (prouvé en cassant un index) :
  * l'historique montre `63 success=1`, `64 success=0`, et la table partielle existe ;
  * le redémarrage du nouveau code échoue (« Validate failed ») ;
  * **l'ancien code 1.2.0 redémarre** (Flyway ignore les migrations futures, même en échec), donc le retour arrière automatique de `deploy-server.sh` fonctionne ;
  * réparation : `U64` (table partielle), puis `DELETE FROM flyway_schema_history WHERE version='64' AND success=0`, puis redémarrage. V64 et V65 s'appliquent alors (2 exécutées).
* Ancien code sur une base en V65 : Flyway limité à V62 valide et n'exécute rien (prouvé).
* Risques métier d'un retour arrière : `report:` payées sans limite de rattrapage ni fenêtre, cases ignorées (double versement possible avec deux licences par TV). Les commentaires de `U64`/`U65` le disent.
* `U65` puis `U64` puis effacement des lignes 64/65 puis redémarrage : V64 et V65 se réappliquent (prouvé). `U65` n'est pas rejouable (LOW-C).

**(5) Cas réel BRX4-W1C5-4WKB-6DGQ** (licence `lic-3tb6w-hujb5`, `created_by='admin-token'`, poste `d6afd1d7f1514f8e`, identité `UNLIMITED`, `opened_unlimited=1`, 5 000 / 50, jeton sans `ik`)

*Migration :* aucune ligne existante modifiée.

*Première synchronisation après le déploiement* (TV 0.14.33/0.14.34, preuve `bind` avec sa clé ; le jeton sans `ik` reste dans la lecture) :

1. **Clé de la console absente de `CASTBRIDGE_LICENSES_TRUSTED_KEYS`** : `REFUSED/UNKNOWN_KEY`, rien d'écrit, plus aucun avis (LOW-4 corrigé).
2. **Jeton qui désigne `lic-3tb6w-hujb5` ET poste par défaut = `d6afd1d7f1514f8e`** (l'identifiant de poste est déterministe : `defaultSeat(licence, facteurs)`) :
   * `ATTACHED`, avec une ligne `lic_issuance` `source='REPORT'`, une ligne `lic_registration` (`ATTACHED`, `ik_signed=0`, `signer_creates=1`) et une alerte douce `INSTALL_TIME_UNPROVEN` dans `lic_audit`, une fois ;
   * `alignEnd` ne touche rien, puisque `created_by` ne commence ni par `import:` ni par `report:` ;
   * ensuite, à chaque synchronisation, seulement `UPDATE last_server_at`.
3. **Même licence, autre identifiant de poste** : `PENDING_DECISION/NO_INSTALL_KEY`, avec un avis « Activation sans clé d'installation : décision du propriétaire » visible à CHAQUE synchronisation (LOW-D). **Accepter** est sans risque : un alias de poste, `ATTACHED`, aucun versement. Refuser fait aussi taire l'avis.
4. **Autre identifiant de licence** :
   * `PENDING_DECISION/INSTALL_TIME_UNKNOWN` si le jeton a plus de 48 h (avis masqué, la TV a une licence), sinon `NO_INSTALL_KEY` (avis visible) ;
   * aucune licence créée ;
   * **à REFUSER**. Accepter créerait une seconde licence (client anonyme). Grâce aux cases, rien ne serait repayé, sauf l'arrondi LOW-B si les débuts diffèrent de plus de 15 jours.
5. **Portefeuille dans tous les cas** :
   * conversion des clés `grant:lic:lic-3tb6w-hujb5:*` en cases (au plus `OPEN|0`, une écriture dans `wallet_period_claim`). Si les 5 000 / 50 viennent d'un don manuel et non d'un versement de licence, aucune case n'est créée, mais `opened_unlimited=1` bloque l'ouverture ;
   * **soldes inchangés** (5 000 / 50), édition `UNLIMITED` ;
   * prochaine tranche de 1 000 + 10 à `start_at + 30 j`, une seule fois.
   * Identité et clé d'installation inchangées. Ne JAMAIS faire de rebind de BRX4 (HIGH-A).

**(6) Mutations**
* Les 3 survivantes d'origine que j'ai rejouées sont TUÉES :
  * MB (`WRONG_SUBJECT` retiré) : tuée par `mb_…` ;
  * MF (révocation du poste sans comparer les dates) : tuée par `mf_…` ;
  * MG (`registerAll` qui relance l'erreur) : tuée par `mg_…`.
* N12 (case par plancher au lieu de l'arrondi) est tuée par `f4`.
* **5 nouvelles qui SURVIVENT** à `AuditW2305FixTest`, `Registrar*`, `CatchUpTest`, `EndAtTest`, `Wallet*`, `Grant*` et `ProductionUpgradeRehearsalTest` :

| # | Mutation | Test qui la tuerait |
|---|---|---|
| N5 | Fusion des fenêtres sans la grâce (`JdbcLicenseFacts:103`) | renouvellement 3 jours après la fin (≤ grâce) : la période suivante doit être payée |
| N9 | `NO_INSTALL_KEY` masqué quand la TV a une licence (`WalletSyncController:202`) | TV licenciée + jeton sans `ik` sous une autre licence : avis attendu |
| N10 | Garde « jeton sans `ik` qui changerait la durée » retirée (`evaluate:278`) | licence `report:` de 30 jours + jeton sans `ik` de 90 jours du même poste : `end_at` inchangé, `NO_INSTALL_KEY` |
| N11 | `backfillClaims` supprimé (`GrantService:158`) | R5 de ce rapport |
| N13 | `verifyNamedAdmin` sans transaction (l'état actuel, MEDIUM-B) | R7 de ce rapport |

Tests manquants à ajouter aussi : R1, R2 (HIGH-A), R3, R4, R6, et la chaîne `U65`/`U64` exécutée sur MySQL.

**(7) Kotlin**
* `ActivationIssuer.Request.installKey` est signée en `ik|<hex>` en production pour une TV seulement, doublon refusé, et ajoutée après les droits.
* `InstallSigner.publicKey` renvoie une copie.
* `OwnerFrames.deviceInfo` ajoute la ligne `install_sig=ed25519|…`. `parseDeviceInfo` distingue `install` et `install_sig`, et refuse un doublon ou un hex invalide.
* `ActivationCenter.requestText` prend la clé de `WalletHub.installSigner()` (créée si absente : une TV neuve l'envoie dès la première demande, et c'est bien la clé qui signe `bind`).
* Route `GET /api/tv/device-request` : relecture puis réémission de SIX champs connus seulement ; la liste blanche reste stricte. `installSig` est une clé PUBLIQUE nécessaire à la génération de la clé, ce qui reste conforme à la règle « le téléphone ne lit que les données de génération ». `TvDeviceRequestParser` est strict (64 hex) et ignore les champs inconnus.
* Parité des vecteurs : Python 379/0, Java vert. Écart Java pour `subject=phone` (LOW-E). Délégation (LOW-F).
* **Non lancé** : les tests Gradle (`:core:test`, `InstallKeyClaimTest`, `ActivationVectorsTest`). Je n'ai pas voulu écrire de sorties de build dans le dépôt.

---

## Vérifié correct
* `ik` signé et contrôlé sans état. Un jeton `ik` volé ne crée, ne lie et ne paie rien pour l'attaquant, et n'écrit aucune ligne qui bloquerait la vraie TV.
* Preuve `bind` liée au code, à l'appareil API et à ± 5 min. Clé prouvée = `ik` exactement.
* Fenêtre ≤ 48 h et émission ≤ +1 h vérifiées par le serveur. `LATE_NOTICE` = 366 j = `CatchUpPolicy.MAXIMUM`.
* REACTIVATE sans effet sur les dates ni création. Refus du propriétaire définitif. Plafond exact sous 16 fils MySQL. 404 sur empreinte inconnue.
* Cases (identité, monnaie, case) réclamées dans la transaction de la pose. Conversion des versements d'avant V65 correcte et idempotente (R5). `f4` 7 000 reste 7 000, MySQL 12 fils.
* V63 + V64 + V65 : additives, 230 ms, échec à mi-chemin réparable, ancien code compatible, `U65` + `U64` corrects dans l'ordre documenté.
* Kotlin : liste blanche de la route conservée. Les anciennes TV gardent et ignorent `ik` (vecteurs). Aucun jeton ni secret dans les réponses ou les journaux de ce chemin.

## Liste à suivre avant le déploiement du serveur (V63 + V64 + V65)
1. **Bloquant** : corriger MEDIUM-B (`@Transactional` sur `verifyNamedAdmin`, + R7). Décider HIGH-A : au minimum un rebind qui ne lie que l'empreinte de clé lue sur la TV, et ne jamais verser une licence `ik_signed` à une identité dont `install_pub ≠ ik`. Corriger MEDIUM-A si une licence `report:` peut être prolongée par le propriétaire.
2. Monter les versions : serveur (nouvelle étiquette, doc `DEPLOIEMENT-SERVEUR-*.md` réécrite pour V64/V65 et registrar ALLUMÉ), TV ≥ 0.14.35/95 et téléphone ≥ 1.2.44/74 avant tout build contenant `install_sig`. `python3 tools/release/check_versions.py` doit donner 0 erreur.
3. Sauvegarde (`deploy-server.sh` la fait ; noter le fichier), puis : `SELECT version, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank;` → la dernière ligne doit être `62 / V62__wallet.sql / success=1`, sans V63 ancien ni ligne en échec.
4. Instantané « avant » de BRX4 :
   * `SELECT * FROM wallet_identity WHERE holder='BRX4-W1C5-4WKB-6DGQ';`
   * soldes (`wallet_balance` joint à `wallet_account`) ;
   * `SELECT idem_key FROM wallet_txn WHERE holder='BRX4-W1C5-4WKB-6DGQ';` (savoir si 5 000 / 50 vient de `grant:lic:lic-3tb6w-hujb5:open-unlimited` ou d'un don) ;
   * `SELECT license_id, created_by, start_at, end_at, seats_allowed FROM lic_license WHERE license_id='lic-3tb6w-hujb5';`
   * `SELECT seat_id, device_code, state FROM lic_seat WHERE device_code='BRX4-W1C5-4WKB-6DGQ';`
5. Lire sur la console (journal, sans exporter le jeton) l'identifiant de licence, le poste et la date d'émission du jeton de BRX4, puis classer : cas 2, 3 ou 4 du § (5).
6. `CASTBRIDGE_LICENSES_TRUSTED_KEYS` : clé de la console avec `ISSUE_PRODUCTION`. Clé `REACTIVATE` seule permise (HIGH-2 corrigé). `castbridge.wallet.require-bind-proof=true` (défaut). Pas de mot de passe ni de clé dans la doc ou le HANDOFF.
7. Comptes : au moins un compte OWNER avec TOTP activé (sinon la décision et les dons répondent 403).
8. Préproduction d'abord, configuration identique : V62 → démarrage → 3 migrations ; une TV de test avec activation `ik` (TV ≥ 0.14.35 + console ≥ 1.2.44), rejouée 3 fois, un seul versement ; une activation sans `ik` → `NO_INSTALL_KEY` ; décision par compte nommé + TOTP.
9. Après le déploiement en production :
   * deux synchronisations de BRX4, soldes identiques à l'instantané ;
   * `SELECT status, reason, COUNT(*) FROM lic_registration GROUP BY 1,2;`
   * `GET /api/v1/admin/licenses/registrations` ;
   * `SELECT * FROM wallet_period_claim WHERE holder='BRX4-W1C5-4WKB-6DGQ';`
   * pour BRX4 : cas 3 = accepter, cas 4 = refuser ;
   * **aucun rebind** de BRX4 ni d'une autre TV tant que HIGH-A n'est pas corrigé.
10. Ordre de mise à jour : serveur, puis TV (pour qu'elle envoie `install_sig`), puis console et bureau, puis seulement émettre de nouvelles activations. D'ici là, chaque activation donne une décision `NO_INSTALL_KEY`.
11. Retour arrière :
   * code : `deploy-server.sh --rollback --apply` (base en V65 acceptée par 1.2.0, prouvé). Lister d'abord `SELECT license_id FROM lic_license WHERE created_by LIKE 'report:%';` (l'ancien code les paierait sans limite) et les TV à plusieurs licences ;
   * schéma (seulement si nécessaire) : `U65` puis `U64`, puis `DELETE FROM flyway_schema_history WHERE version IN ('65','64');`, puis `rollback-V63.sql` (qui efface sa propre ligne). `U65` ne se rejoue pas : s'il échoue à mi-chemin, finir à la main ;
   * échec de V64 à mi-chemin : `U64`, puis `DELETE … version='64' AND success=0`, puis redémarrage.

## Ce que je n'ai pas pu faire
* Pas de suite complète (coordinateur), pas de serveur réel, pas de vraie TV, pas de lecture de la base de production. Le cas BRX4 repose sur des hypothèses : identifiant de licence et de poste du jeton, date d'émission, clé de confiance configurée, origine des 5 000 / 50.
* Tests Kotlin / Gradle non lancés (lecture du code, et APK inspectés par recherche de chaînes dans le dex seulement).
* MEDIUM-C est raisonné, pas exécuté de bout en bout. R7 tourne sur H2 (non lancé sur MySQL). Le correctif `@Transactional` a été vérifié sur H2.
* Les durées de migration sont mesurées sur une base vide de lignes. Elles ne dépendent pas des données, puisque seules des tables neuves sont créées ou modifiées.
* La preuve « l'ancien code démarre » utilise le Flyway de HEAD. L'étiquette `server-1.2.0` a le même parent Spring Boot (3.5.16), donc la même version de Flyway.
